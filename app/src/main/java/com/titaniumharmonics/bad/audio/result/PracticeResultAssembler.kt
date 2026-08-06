package com.titaniumharmonics.bad.audio.result

import com.titaniumharmonics.bad.audio.detection.DetectedHit
import com.titaniumharmonics.bad.audio.detection.HitDetectionResult
import com.titaniumharmonics.bad.audio.detection.SessionDetectionSnapshot
import com.titaniumharmonics.bad.audio.matching.HitJudgement
import com.titaniumharmonics.bad.audio.matching.HitMatchingResult
import com.titaniumharmonics.bad.audio.matching.SessionJudgementSnapshot
import com.titaniumharmonics.bad.audio.matching.RuntimeExerciseSampleTimeline
import com.titaniumharmonics.bad.audio.metronome.SessionMetronomeSnapshot
import com.titaniumharmonics.bad.exercise.RuntimeExercise
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.sqrt

object PracticeResultAssembler {
    fun assemble(
        runtimeExercise: RuntimeExercise,
        matchingResult: HitMatchingResult,
        hitDetectionResult: HitDetectionResult,
        judgementSnapshot: SessionJudgementSnapshot,
        detectionSnapshot: SessionDetectionSnapshot,
        metronomeSnapshot: SessionMetronomeSnapshot,
    ): PracticeResult {
        require(matchingResult.configuration == judgementSnapshot.configuration) {
            "Matching must use the frozen session judgement configuration."
        }
        require(hitDetectionResult.configuration == detectionSnapshot.configuration) {
            "Detection must use the frozen session detection configuration."
        }
        require(
            RuntimeExerciseSampleTimeline.expectedNotes(
                runtimeExercise,
                matchingResult.sampleRateHz,
            ) == matchingResult.expectedNoteJudgements.map { it.expected },
        ) { "Matching output does not belong to the supplied runtime exercise." }
        val intensityByHit = normalizeIntensity(matchingResult.acceptedHits)
        val detectionIndexByHit = IdentityHashMap<DetectedHit, Int>().apply {
            hitDetectionResult.hits.forEachIndexed { index, hit -> put(hit, index) }
        }

        val judgedNotes = matchingResult.expectedNoteJudgements.map { matched ->
            val hit = matched.detectedHit
            JudgedNote(
                expectedNoteIndex = matched.expected.index,
                expectedExerciseSample = matched.expected.exerciseSample,
                expectedExerciseTimeMillis = matched.expected.exerciseTimeMillis,
                measureIndex = matched.expected.note.measureIndex,
                positionInMeasureTicks = matched.expected.note.positionInMeasureTicks,
                beatPosition = matched.expected.note.positionInMeasureTicks.toDouble() /
                    runtimeExercise.ticksPerQuarterNote,
                matchedHit = hit,
                rawDetectedSample = hit?.rawExerciseSample,
                rawDetectedTimeMillis = hit?.rawExerciseTimeMillis,
                calibratedDetectedSample = hit?.calibratedExerciseSample,
                calibratedDetectedTimeMillis = hit?.calibratedExerciseTimeMillis,
                timingErrorSamples = matched.timingErrorSamples,
                timingErrorMillis = matched.timingErrorMillis,
                judgement = matched.judgement,
                detectionConfidence = hit?.confidence,
                relativeIntensity = hit?.let { intensityByHit[it] },
                accent = matched.expected.note.accent,
                targetIntensity = matched.expected.note.targetIntensity,
            )
        }
        val extraHits = matchingResult.extraDetectedHits.map { hit ->
            ExtraHit(
                detectedHitIndex = detectionIndexByHit[hit]
                    ?: hitDetectionResult.hits.indexOf(hit).takeIf { it >= 0 }
                    ?: error("An extra hit is absent from the detection result."),
                detectedHit = hit,
                rawSample = hit.rawExerciseSample,
                rawTimeMillis = hit.rawExerciseTimeMillis,
                calibratedSample = hit.calibratedExerciseSample,
                calibratedTimeMillis = hit.calibratedExerciseTimeMillis,
                peakAmplitude = hit.peakAmplitude,
                relativeIntensity = checkNotNull(intensityByHit[hit]),
                confidence = hit.confidence,
            )
        }
        val timingErrors = judgedNotes.mapNotNull(JudgedNote::timingErrorMillis)
        val intensities = intensityByHit.values.toList()
        val totalExpected = judgedNotes.size
        val matchedCount = judgedNotes.count { it.matchedHit != null }
        val onTimeCount = judgedNotes.count { it.judgement == HitJudgement.ON_TIME }
        val missedCount = judgedNotes.count { it.judgement == HitJudgement.MISSED }

        return PracticeResult(
            schemaVersion = PracticeResult.CURRENT_SCHEMA_VERSION,
            exerciseId = runtimeExercise.id,
            exerciseName = runtimeExercise.name,
            bpm = runtimeExercise.tempoBpm,
            sampleRateHz = matchingResult.sampleRateHz,
            runtimeExercise = runtimeExercise,
            judgedNotes = judgedNotes,
            extraHits = extraHits,
            totalExpectedNotes = totalExpected,
            earlyCount = judgedNotes.count { it.judgement == HitJudgement.EARLY },
            onTimeCount = onTimeCount,
            lateCount = judgedNotes.count { it.judgement == HitJudgement.LATE },
            missedCount = missedCount,
            extraCount = extraHits.size,
            accuracy = rate(onTimeCount, totalExpected),
            hitRate = rate(matchedCount, totalExpected),
            meanAbsoluteTimingErrorMillis = timingErrors.takeUnless(List<Double>::isEmpty)
                ?.map(::abs)?.average(),
            signedMeanTimingErrorMillis = timingErrors.takeUnless(List<Double>::isEmpty)
                ?.average(),
            medianAbsoluteTimingErrorMillis = median(timingErrors.map(::abs)),
            timingErrorStandardDeviationMillis = standardDeviation(timingErrors),
            missedRate = rate(missedCount, totalExpected),
            extraHitRate = rate(extraHits.size, matchingResult.acceptedHits.size),
            meanRelativeIntensity = intensities.takeUnless(List<Double>::isEmpty)?.average(),
            minimumRelativeIntensity = intensities.minOrNull(),
            maximumRelativeIntensity = intensities.maxOrNull(),
            judgementSnapshot = judgementSnapshot,
            detectionSnapshot = detectionSnapshot,
            metronomeSnapshot = metronomeSnapshot,
            timingCalibration = detectionSnapshot.timingCalibration,
            calibrationOffsetSamples = hitDetectionResult.calibrationOffsetSamples,
            calibrationApplied = hitDetectionResult.calibrationApplied,
        )
    }

    private fun normalizeIntensity(hits: List<DetectedHit>): IdentityHashMap<DetectedHit, Double> {
        val result = IdentityHashMap<DetectedHit, Double>()
        if (hits.isEmpty()) return result
        hits.forEach { require(it.peakAmplitude.isFinite()) }
        val minimum = hits.minOf { it.peakAmplitude.toDouble() }
        val maximum = hits.maxOf { it.peakAmplitude.toDouble() }
        hits.forEach { hit ->
            result[hit] = if (maximum == minimum) {
                1.0
            } else {
                ((hit.peakAmplitude - minimum) / (maximum - minimum)).coerceIn(0.0, 1.0)
            }
        }
        return result
    }

    private fun rate(numerator: Int, denominator: Int): Double =
        if (denominator == 0) 0.0 else numerator.toDouble() / denominator

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else
            (sorted[middle - 1] + sorted[middle]) / 2.0
    }

    /** Population standard deviation over matched-note timing errors. */
    private fun standardDeviation(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val mean = values.average()
        return sqrt(values.sumOf { value -> (value - mean) * (value - mean) } / values.size)
    }
}
