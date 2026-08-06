package com.titaniumharmonics.bad.audio.result

import com.titaniumharmonics.bad.audio.calibration.TimingCalibration
import com.titaniumharmonics.bad.audio.detection.DetectedHit
import com.titaniumharmonics.bad.audio.detection.SessionDetectionSnapshot
import com.titaniumharmonics.bad.audio.matching.HitJudgement
import com.titaniumharmonics.bad.audio.matching.SessionJudgementSnapshot
import com.titaniumharmonics.bad.audio.metronome.SessionMetronomeSnapshot
import com.titaniumharmonics.bad.exercise.RuntimeExercise
import java.util.Collections

data class JudgedNote(
    val expectedNoteIndex: Int,
    val expectedExerciseSample: Long,
    val expectedExerciseTimeMillis: Double,
    val measureIndex: Int,
    val positionInMeasureTicks: Long,
    val beatPosition: Double,
    val matchedHit: DetectedHit?,
    val rawDetectedSample: Long?,
    val rawDetectedTimeMillis: Double?,
    val calibratedDetectedSample: Long?,
    val calibratedDetectedTimeMillis: Double?,
    val timingErrorSamples: Long?,
    val timingErrorMillis: Double?,
    val judgement: HitJudgement,
    val detectionConfidence: Double?,
    val relativeIntensity: Double?,
    val accent: Boolean,
    val targetIntensity: Double?,
)

data class ExtraHit(
    val detectedHitIndex: Int,
    val detectedHit: DetectedHit,
    val rawSample: Long,
    val rawTimeMillis: Double,
    val calibratedSample: Long,
    val calibratedTimeMillis: Double,
    val peakAmplitude: Float,
    val relativeIntensity: Double,
    val confidence: Double,
)

class PracticeResult(
    val schemaVersion: Int,
    val exerciseId: String,
    val exerciseName: String,
    val bpm: Double,
    val sampleRateHz: Int,
    val runtimeExercise: RuntimeExercise,
    judgedNotes: List<JudgedNote>,
    extraHits: List<ExtraHit>,
    val totalExpectedNotes: Int,
    val earlyCount: Int,
    val onTimeCount: Int,
    val lateCount: Int,
    val missedCount: Int,
    val extraCount: Int,
    val accuracy: Double,
    val hitRate: Double,
    val meanAbsoluteTimingErrorMillis: Double?,
    val signedMeanTimingErrorMillis: Double?,
    val medianAbsoluteTimingErrorMillis: Double?,
    val timingErrorStandardDeviationMillis: Double?,
    val missedRate: Double,
    val extraHitRate: Double,
    val meanRelativeIntensity: Double?,
    val minimumRelativeIntensity: Double?,
    val maximumRelativeIntensity: Double?,
    val judgementSnapshot: SessionJudgementSnapshot,
    val detectionSnapshot: SessionDetectionSnapshot,
    val metronomeSnapshot: SessionMetronomeSnapshot,
    val timingCalibration: TimingCalibration?,
    val calibrationOffsetSamples: Long,
    val calibrationApplied: Boolean,
) {
    val judgedNotes: List<JudgedNote> = judgedNotes.immutableCopy()
    val extraHits: List<ExtraHit> = extraHits.immutableCopy()

    init {
        require(schemaVersion > 0)
        require(exerciseId.isNotBlank())
        require(exerciseName.isNotBlank())
        require(bpm.isFinite() && bpm > 0.0)
        require(sampleRateHz > 0)
        require(totalExpectedNotes == this.judgedNotes.size)
        require(earlyCount + onTimeCount + lateCount + missedCount == totalExpectedNotes)
        require(extraCount == this.extraHits.size)
        listOf(accuracy, hitRate, missedRate, extraHitRate).forEach {
            require(it.isFinite() && it in 0.0..1.0)
        }
        listOfNotNull(
            meanAbsoluteTimingErrorMillis,
            signedMeanTimingErrorMillis,
            medianAbsoluteTimingErrorMillis,
            timingErrorStandardDeviationMillis,
            meanRelativeIntensity,
            minimumRelativeIntensity,
            maximumRelativeIntensity,
        ).forEach { require(it.isFinite()) }
        listOfNotNull(
            meanRelativeIntensity,
            minimumRelativeIntensity,
            maximumRelativeIntensity,
        ).forEach { require(it in 0.0..1.0) }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

private fun <T> List<T>.immutableCopy(): List<T> =
    Collections.unmodifiableList(ArrayList(this))
