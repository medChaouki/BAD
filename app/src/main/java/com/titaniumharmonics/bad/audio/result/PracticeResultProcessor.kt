package com.titaniumharmonics.bad.audio.result

import com.titaniumharmonics.bad.audio.RecordedSession
import com.titaniumharmonics.bad.audio.analysis.AudioAnalysis
import com.titaniumharmonics.bad.audio.detection.HitDetectionResult
import com.titaniumharmonics.bad.audio.matching.HitMatcher
import com.titaniumharmonics.bad.audio.matching.RuntimeExerciseSampleTimeline
import kotlinx.coroutines.CancellationException

sealed interface PracticeResultProcessingResult {
    data class Success(
        val result: PracticeResult,
        val graphModel: ProductionGraphModel,
    ) : PracticeResultProcessingResult
    data class Failure(val reason: PracticeResultFailure) : PracticeResultProcessingResult
}

sealed class PracticeResultFailure(val userMessage: String) {
    data object SessionMismatch : PracticeResultFailure(
        "The completed recording and analysis data do not belong to the same practice session.",
    )

    data object InvalidTimeline : PracticeResultFailure(
        "The completed exercise timeline could not be matched safely.",
    )

    data object ProcessingFailed : PracticeResultFailure(
        "The practice result could not be calculated.",
    )

    data object GraphUnavailable : PracticeResultFailure(
        "The practice result graph could not be prepared safely.",
    )
}

sealed interface PracticeResultState {
    data object NotStarted : PracticeResultState
    data object Matching : PracticeResultState
    data class Ready(
        val result: PracticeResult,
        val graphModel: ProductionGraphModel,
    ) : PracticeResultState
    data class Failed(val message: String) : PracticeResultState
}

/** Pure boundary between completed detection and the immutable current-run result. */
class PracticeResultProcessor {
    fun process(
        session: RecordedSession,
        analysis: AudioAnalysis,
        detection: HitDetectionResult,
        cancellationCheck: () -> Unit = {},
    ): PracticeResultProcessingResult {
        cancellationCheck()
        val validationFailure = validate(session, analysis, detection)
        if (validationFailure != null) return PracticeResultProcessingResult.Failure(validationFailure)

        return try {
            val matching = HitMatcher.match(
                runtimeExercise = session.runtimeExercise,
                detectedHits = detection.hits,
                sampleRateHz = analysis.sampleRateHz,
                configuration = session.judgementSnapshot.configuration,
            )
            cancellationCheck()
            val result = PracticeResultAssembler.assemble(
                runtimeExercise = session.runtimeExercise,
                matchingResult = matching,
                hitDetectionResult = detection,
                judgementSnapshot = session.judgementSnapshot,
                detectionSnapshot = session.detectionSnapshot,
                metronomeSnapshot = session.metronomeSnapshot,
            )
            cancellationCheck()
            when (val graph = ProductionGraphModelBuilder.build(result, analysis)) {
                is ProductionGraphBuildResult.Success ->
                    PracticeResultProcessingResult.Success(result, graph.model)
                is ProductionGraphBuildResult.Failure ->
                    PracticeResultProcessingResult.Failure(PracticeResultFailure.GraphUnavailable)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            PracticeResultProcessingResult.Failure(PracticeResultFailure.ProcessingFailed)
        }
    }

    private fun validate(
        session: RecordedSession,
        analysis: AudioAnalysis,
        detection: HitDetectionResult,
    ): PracticeResultFailure? {
        if (
            analysis.sampleRateHz != session.audioFormat.sampleRateHz ||
            analysis.gradedSampleFrameCount != session.gradedExerciseSampleFrames ||
            analysis.metronomeConfiguration != session.metronomeSnapshot.configuration ||
            detection.configuration != session.detectionSnapshot.configuration
        ) return PracticeResultFailure.SessionMismatch

        val expectedSamples = RuntimeExerciseSampleTimeline.expectedNotes(
            session.runtimeExercise,
            analysis.sampleRateHz,
        ).map { it.exerciseSample }
        if (detection.expectedExerciseSamples.toList() != expectedSamples) {
            return PracticeResultFailure.InvalidTimeline
        }
        return null
    }
}
