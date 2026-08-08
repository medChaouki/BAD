package com.titaniumharmonics.bad.history.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseRunDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ExerciseRunEntity)

    @Query("SELECT * FROM exercise_runs WHERE run_id = :runId")
    suspend fun getById(runId: String): ExerciseRunEntity?

    @Query("SELECT * FROM exercise_runs WHERE run_id = :runId")
    fun observeById(runId: String): Flow<ExerciseRunEntity?>

    @Query(
        "SELECT * FROM exercise_runs WHERE exercise_id = :exerciseId " +
            "ORDER BY completed_at_epoch_millis DESC, run_id DESC",
    )
    fun observeForExercise(exerciseId: String): Flow<List<ExerciseRunEntity>>

    @Query("SELECT * FROM exercise_runs ORDER BY completed_at_epoch_millis DESC, run_id DESC")
    fun observeAll(): Flow<List<ExerciseRunEntity>>

    @Query(
        "SELECT run_id, exercise_id, exercise_name_snapshot, started_at_epoch_millis, " +
            "completed_at_epoch_millis, bpm, exercise_duration_millis, " +
            "expanded_measure_count, total_expected_notes, accuracy, hit_rate, " +
            "mean_absolute_timing_error_millis, signed_mean_timing_error_millis, " +
            "missed_count, extra_count, run_schema_version, app_version " +
            "FROM exercise_runs WHERE exercise_id = :exerciseId " +
            "ORDER BY completed_at_epoch_millis DESC, run_id DESC",
    )
    fun observeSummariesForExercise(exerciseId: String): Flow<List<ExerciseRunSummaryRow>>

    @Query(
        "SELECT run_id, exercise_id, exercise_name_snapshot, started_at_epoch_millis, " +
            "completed_at_epoch_millis, bpm, exercise_duration_millis, " +
            "expanded_measure_count, total_expected_notes, accuracy, hit_rate, " +
            "mean_absolute_timing_error_millis, signed_mean_timing_error_millis, " +
            "missed_count, extra_count, run_schema_version, app_version " +
            "FROM exercise_runs ORDER BY completed_at_epoch_millis DESC, run_id DESC",
    )
    fun observeAllSummaries(): Flow<List<ExerciseRunSummaryRow>>

    @Query("DELETE FROM exercise_runs WHERE run_id = :runId")
    suspend fun deleteById(runId: String): Int

    @Query("DELETE FROM exercise_runs WHERE exercise_id = :exerciseId")
    suspend fun deleteForExercise(exerciseId: String): Int
}
