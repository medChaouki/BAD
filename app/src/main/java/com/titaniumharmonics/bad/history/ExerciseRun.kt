package com.titaniumharmonics.bad.history

import com.titaniumharmonics.bad.audio.calibration.TimingCalibration
import com.titaniumharmonics.bad.audio.detection.SessionDetectionSnapshot
import com.titaniumharmonics.bad.audio.matching.SessionJudgementSnapshot
import com.titaniumharmonics.bad.audio.metronome.SessionMetronomeSnapshot
import com.titaniumharmonics.bad.audio.result.PracticeResult
import com.titaniumharmonics.bad.audio.result.ProductionGraphModel
import com.titaniumharmonics.bad.exercise.RuntimeExercise
import java.util.UUID

/**
 * Immutable historical truth for one completed practice attempt.
 *
 * The completed result owns the canonical exercise and configuration snapshots. Derived
 * properties deliberately delegate to that snapshot so mutable exercises and current settings
 * can never reinterpret the run.
 */
class ExerciseRun(
    val runId: String,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long,
    val practiceResult: PracticeResult,
    val productionGraph: ProductionGraphModel,
    val appVersion: String,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    val exerciseId: String get() = practiceResult.exerciseId
    val exerciseNameSnapshot: String get() = practiceResult.exerciseName
    val runtimeExerciseSnapshot: RuntimeExercise get() = practiceResult.runtimeExercise
    val bpm: Double get() = practiceResult.bpm
    val exerciseDurationSamples: Long get() = productionGraph.exerciseDurationSamples
    val exerciseDurationMillis: Double get() = productionGraph.exerciseDurationMillis
    val expandedMeasureCount: Int get() = practiceResult.runtimeExercise.measureCount
    val totalExpectedNotes: Int get() = practiceResult.totalExpectedNotes
    val detectionSnapshot: SessionDetectionSnapshot get() = practiceResult.detectionSnapshot
    val metronomeSnapshot: SessionMetronomeSnapshot get() = practiceResult.metronomeSnapshot
    val judgementSnapshot: SessionJudgementSnapshot get() = practiceResult.judgementSnapshot
    val calibrationMetadata: TimingCalibration? get() = practiceResult.timingCalibration

    init {
        require(runId.isNotBlank()) { "runId must not be blank." }
        require(startedAtEpochMillis >= 0L) { "Run start time must not be negative." }
        require(completedAtEpochMillis >= startedAtEpochMillis) {
            "Run completion must not precede its start."
        }
        require(appVersion.isNotBlank()) { "appVersion must not be blank." }
        require(schemaVersion > 0) { "schemaVersion must be positive." }
        require(productionGraph.exerciseId == practiceResult.exerciseId) {
            "The production graph belongs to another exercise."
        }
        require(productionGraph.sampleRateHz == practiceResult.sampleRateHz) {
            "The production graph and result sample rates differ."
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        fun create(
            startedAtEpochMillis: Long,
            completedAtEpochMillis: Long,
            practiceResult: PracticeResult,
            productionGraph: ProductionGraphModel,
            appVersion: String,
            runId: String = UUID.randomUUID().toString(),
        ): ExerciseRun = ExerciseRun(
            runId = runId,
            startedAtEpochMillis = startedAtEpochMillis,
            completedAtEpochMillis = completedAtEpochMillis,
            practiceResult = practiceResult,
            productionGraph = productionGraph,
            appVersion = appVersion,
        )
    }
}

data class ExerciseRunSummary(
    val runId: String,
    val exerciseId: String,
    val exerciseNameSnapshot: String,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long,
    val bpm: Double,
    val exerciseDurationMillis: Double,
    val expandedMeasureCount: Int,
    val totalExpectedNotes: Int,
    val accuracy: Double,
    val hitRate: Double,
    val meanAbsoluteTimingErrorMillis: Double?,
    val signedMeanTimingErrorMillis: Double?,
    val missedCount: Int,
    val extraCount: Int,
    val schemaVersion: Int,
    val appVersion: String,
)
