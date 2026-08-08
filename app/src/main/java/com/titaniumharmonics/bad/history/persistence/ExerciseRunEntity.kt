package com.titaniumharmonics.bad.history.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exercise_runs",
    indices = [
        Index(value = ["completed_at_epoch_millis"]),
        Index(value = ["exercise_id", "completed_at_epoch_millis"]),
    ],
)
data class ExerciseRunEntity(
    @PrimaryKey
    @ColumnInfo(name = "run_id")
    val runId: String,
    @ColumnInfo(name = "exercise_id")
    val exerciseId: String,
    @ColumnInfo(name = "exercise_name_snapshot")
    val exerciseNameSnapshot: String,
    @ColumnInfo(name = "started_at_epoch_millis")
    val startedAtEpochMillis: Long,
    @ColumnInfo(name = "completed_at_epoch_millis")
    val completedAtEpochMillis: Long,
    @ColumnInfo(name = "bpm")
    val bpm: Double,
    @ColumnInfo(name = "exercise_duration_millis")
    val exerciseDurationMillis: Double,
    @ColumnInfo(name = "expanded_measure_count")
    val expandedMeasureCount: Int,
    @ColumnInfo(name = "total_expected_notes")
    val totalExpectedNotes: Int,
    @ColumnInfo(name = "accuracy")
    val accuracy: Double,
    @ColumnInfo(name = "hit_rate")
    val hitRate: Double,
    @ColumnInfo(name = "mean_absolute_timing_error_millis")
    val meanAbsoluteTimingErrorMillis: Double?,
    @ColumnInfo(name = "signed_mean_timing_error_millis")
    val signedMeanTimingErrorMillis: Double?,
    @ColumnInfo(name = "missed_count")
    val missedCount: Int,
    @ColumnInfo(name = "extra_count")
    val extraCount: Int,
    @ColumnInfo(name = "run_schema_version")
    val runSchemaVersion: Int,
    @ColumnInfo(name = "app_version")
    val appVersion: String,
    @ColumnInfo(name = "payload_json")
    val payloadJson: String,
)

data class ExerciseRunSummaryRow(
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "exercise_name_snapshot") val exerciseNameSnapshot: String,
    @ColumnInfo(name = "started_at_epoch_millis") val startedAtEpochMillis: Long,
    @ColumnInfo(name = "completed_at_epoch_millis") val completedAtEpochMillis: Long,
    @ColumnInfo(name = "bpm") val bpm: Double,
    @ColumnInfo(name = "exercise_duration_millis") val exerciseDurationMillis: Double,
    @ColumnInfo(name = "expanded_measure_count") val expandedMeasureCount: Int,
    @ColumnInfo(name = "total_expected_notes") val totalExpectedNotes: Int,
    @ColumnInfo(name = "accuracy") val accuracy: Double,
    @ColumnInfo(name = "hit_rate") val hitRate: Double,
    @ColumnInfo(name = "mean_absolute_timing_error_millis")
    val meanAbsoluteTimingErrorMillis: Double?,
    @ColumnInfo(name = "signed_mean_timing_error_millis")
    val signedMeanTimingErrorMillis: Double?,
    @ColumnInfo(name = "missed_count") val missedCount: Int,
    @ColumnInfo(name = "extra_count") val extraCount: Int,
    @ColumnInfo(name = "run_schema_version") val runSchemaVersion: Int,
    @ColumnInfo(name = "app_version") val appVersion: String,
)
