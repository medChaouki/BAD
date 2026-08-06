package com.titaniumharmonics.bad.audio.result

import com.titaniumharmonics.bad.audio.analysis.AudioAnalysis
import com.titaniumharmonics.bad.audio.matching.HitJudgement
import com.titaniumharmonics.bad.audio.matching.RuntimeExerciseSampleTimeline
import kotlin.math.abs

object ProductionGraphModelBuilder {
    fun build(
        result: PracticeResult,
        analysis: AudioAnalysis,
        maximumEnvelopePointCount: Int = ProductionGraphModel.MAXIMUM_ENVELOPE_POINT_COUNT,
    ): ProductionGraphBuildResult = try {
        require(analysis.sampleRateHz == result.sampleRateHz) {
            "The analysis and result sample rates differ."
        }
        val durationSamples = RuntimeExerciseSampleTimeline.ticksToSamples(
            result.runtimeExercise,
            result.runtimeExercise.totalTicks,
            result.sampleRateHz,
        )
        val runtimeExpected = RuntimeExerciseSampleTimeline.expectedNotes(
            result.runtimeExercise,
            result.sampleRateHz,
        )
        require(runtimeExpected.size == result.judgedNotes.size)
        runtimeExpected.zip(result.judgedNotes).forEach { (runtime, judged) ->
            require(runtime.index == judged.expectedNoteIndex)
            require(runtime.exerciseSample == judged.expectedExerciseSample)
            require(runtime.note.measureIndex == judged.measureIndex)
            require(runtime.note.positionInMeasureTicks == judged.positionInMeasureTicks)
        }
        val expected = ArrayList<ProductionExpectedNoteMarker>(result.judgedNotes.size)
        val matched = ArrayList<ProductionMatchedHitMarker>()
        val connectors = ArrayList<ProductionTimingConnector>()
        val missed = ArrayList<ProductionMissedNoteMarker>()

        result.judgedNotes.forEach { note ->
            validateNote(note, result)
            expected += ProductionExpectedNoteMarker(
                expectedNoteIndex = note.expectedNoteIndex,
                exerciseSample = note.expectedExerciseSample,
                exerciseTimeMillis = note.expectedExerciseTimeMillis,
                measureIndex = note.measureIndex,
                beatPosition = note.beatPosition,
                judgement = note.judgement,
            )
            if (note.judgement == HitJudgement.MISSED) {
                missed += ProductionMissedNoteMarker(
                    expectedNoteIndex = note.expectedNoteIndex,
                    exerciseSample = note.expectedExerciseSample,
                    exerciseTimeMillis = note.expectedExerciseTimeMillis,
                    measureIndex = note.measureIndex,
                    beatPosition = note.beatPosition,
                )
            } else {
                matched += ProductionMatchedHitMarker(
                    expectedNoteIndex = note.expectedNoteIndex,
                    calibratedExerciseSample = checkNotNull(note.calibratedDetectedSample),
                    calibratedExerciseTimeMillis = checkNotNull(note.calibratedDetectedTimeMillis),
                    judgement = note.judgement,
                    relativeIntensity = checkNotNull(note.relativeIntensity),
                    confidence = checkNotNull(note.detectionConfidence),
                )
                connectors += ProductionTimingConnector(
                    expectedNoteIndex = note.expectedNoteIndex,
                    expectedExerciseSample = note.expectedExerciseSample,
                    calibratedActualSample = note.calibratedDetectedSample,
                    timingErrorSamples = checkNotNull(note.timingErrorSamples),
                    timingErrorMillis = checkNotNull(note.timingErrorMillis),
                )
            }
        }

        val extras = result.extraHits.map { hit ->
            ProductionExtraHitMarker(
                detectedHitIndex = hit.detectedHitIndex,
                calibratedExerciseSample = hit.calibratedSample,
                calibratedExerciseTimeMillis = hit.calibratedTimeMillis,
                relativeIntensity = hit.relativeIntensity,
                confidence = hit.confidence,
            )
        }
        val envelope = ProductionEnvelopeDownsampler.downsample(
            analysis,
            durationSamples,
            maximumEnvelopePointCount,
        )
        val guides = result.runtimeExercise.measures.map { measure ->
            ProductionMeasureGuide(
                measureIndex = measure.index,
                exerciseSample = RuntimeExerciseSampleTimeline.ticksToSamples(
                    result.runtimeExercise,
                    measure.startTick,
                    result.sampleRateHz,
                ),
            )
        }

        ProductionGraphBuildResult.Success(
            ProductionGraphModel(
                exerciseId = result.exerciseId,
                sampleRateHz = result.sampleRateHz,
                exerciseDurationSamples = durationSamples,
                exerciseDurationMillis = RuntimeExerciseSampleTimeline.samplesToMillis(
                    durationSamples,
                    result.sampleRateHz,
                ),
                maximumEnvelopeAmplitude = envelope.maxOfOrNull { it.amplitude }
                    ?.coerceAtLeast(MINIMUM_ENVELOPE_SCALE) ?: MINIMUM_ENVELOPE_SCALE,
                envelopePoints = envelope,
                expectedNotes = expected,
                matchedHits = matched,
                timingConnectors = connectors,
                missedNotes = missed,
                extraHits = extras,
                measureGuides = guides,
            ),
        )
    } catch (_: IllegalArgumentException) {
        ProductionGraphBuildResult.Failure(
            "The production result graph could not be built from inconsistent session data.",
        )
    } catch (_: IllegalStateException) {
        ProductionGraphBuildResult.Failure(
            "The production result graph could not be built from incomplete session data.",
        )
    }

    private fun validateNote(note: JudgedNote, result: PracticeResult) {
        require(note.expectedExerciseSample >= 0L)
        require(note.expectedExerciseTimeMillis.isFinite())
        if (note.judgement == HitJudgement.MISSED) {
            require(note.matchedHit == null)
            require(note.calibratedDetectedSample == null)
            require(note.calibratedDetectedTimeMillis == null)
            require(note.timingErrorSamples == null && note.timingErrorMillis == null)
            return
        }

        val actualSample = checkNotNull(note.calibratedDetectedSample)
        val actualMillis = checkNotNull(note.calibratedDetectedTimeMillis)
        val errorSamples = checkNotNull(note.timingErrorSamples)
        val errorMillis = checkNotNull(note.timingErrorMillis)
        require(errorSamples == actualSample - note.expectedExerciseSample)
        require(abs((actualMillis - note.expectedExerciseTimeMillis) - errorMillis) < MILLIS_TOLERANCE)
        require(note.relativeIntensity?.let { it.isFinite() && it in 0.0..1.0 } == true)
        require(note.detectionConfidence?.let { it.isFinite() && it in 0.0..1.0 } == true)

        when (note.judgement) {
            HitJudgement.EARLY -> require(errorSamples < 0L)
            HitJudgement.LATE -> require(errorSamples > 0L)
            HitJudgement.ON_TIME -> require(
                errorMillis >= -result.judgementSnapshot.configuration.onTimeBeforeMillis -
                    MILLIS_TOLERANCE &&
                    errorMillis <= result.judgementSnapshot.configuration.onTimeAfterMillis +
                    MILLIS_TOLERANCE,
            )
            HitJudgement.MISSED -> error("Handled above.")
        }
    }

    private const val MILLIS_TOLERANCE = 0.001
    private const val MINIMUM_ENVELOPE_SCALE = 0.001f
}
