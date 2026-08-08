package com.titaniumharmonics.bad.history

import kotlinx.coroutines.flow.Flow

sealed interface ExerciseRunPersistenceError {
    val runId: String?

    data class SaveFailure(override val runId: String) : ExerciseRunPersistenceError
    data class LoadFailure(override val runId: String?) : ExerciseRunPersistenceError
    data class MissingRun(override val runId: String) : ExerciseRunPersistenceError
    data class CorruptedPayload(override val runId: String) : ExerciseRunPersistenceError
    data class UnsupportedSchema(
        override val runId: String,
        val schemaVersion: Int,
    ) : ExerciseRunPersistenceError
    data class InvalidGraphPayload(override val runId: String) : ExerciseRunPersistenceError
    data class PayloadTooLarge(
        override val runId: String,
        val sizeBytes: Int,
        val maximumBytes: Int,
    ) : ExerciseRunPersistenceError
    data class DatabaseMigrationFailure(override val runId: String? = null) :
        ExerciseRunPersistenceError
    data class DeleteFailure(override val runId: String?) : ExerciseRunPersistenceError
}

sealed interface ExerciseRunSaveResult {
    data object Saved : ExerciseRunSaveResult
    data class Failed(val error: ExerciseRunPersistenceError) : ExerciseRunSaveResult
}

sealed interface ExerciseRunLoadResult {
    data class Found(val run: ExerciseRun) : ExerciseRunLoadResult
    data class Failed(val error: ExerciseRunPersistenceError) : ExerciseRunLoadResult
}

class ExerciseRunCollection(
    runs: List<ExerciseRun>,
    failures: List<ExerciseRunPersistenceError> = emptyList(),
) {
    val runs: List<ExerciseRun> = runs.toList()
    val failures: List<ExerciseRunPersistenceError> = failures.toList()
}

sealed interface ExerciseRunDeleteResult {
    data object Deleted : ExerciseRunDeleteResult
    data class Failed(val error: ExerciseRunPersistenceError) : ExerciseRunDeleteResult
}

interface ExerciseRunRepository {
    suspend fun saveRun(run: ExerciseRun): ExerciseRunSaveResult
    suspend fun getRun(runId: String): ExerciseRunLoadResult
    fun observeRun(runId: String): Flow<ExerciseRunLoadResult>
    fun observeRunsForExercise(exerciseId: String): Flow<ExerciseRunCollection>
    fun observeAllRuns(): Flow<ExerciseRunCollection>
    fun observeRunSummariesForExercise(exerciseId: String): Flow<List<ExerciseRunSummary>>
    fun observeAllRunSummaries(): Flow<List<ExerciseRunSummary>>
    suspend fun deleteRun(runId: String): ExerciseRunDeleteResult
    suspend fun deleteRunsForExercise(exerciseId: String): ExerciseRunDeleteResult
}
