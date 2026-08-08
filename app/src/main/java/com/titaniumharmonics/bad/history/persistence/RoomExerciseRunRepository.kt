package com.titaniumharmonics.bad.history.persistence

import android.content.Context
import com.titaniumharmonics.bad.history.ExerciseRun
import com.titaniumharmonics.bad.history.ExerciseRunCollection
import com.titaniumharmonics.bad.history.ExerciseRunDeleteResult
import com.titaniumharmonics.bad.history.ExerciseRunLoadResult
import com.titaniumharmonics.bad.history.ExerciseRunPersistenceError
import com.titaniumharmonics.bad.history.ExerciseRunRepository
import com.titaniumharmonics.bad.history.ExerciseRunSaveResult
import com.titaniumharmonics.bad.history.ExerciseRunSummaryCollection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomExerciseRunRepository internal constructor(
    private val dao: ExerciseRunDao,
    private val persistenceDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ExerciseRunRepository {

    override suspend fun saveRun(run: ExerciseRun): ExerciseRunSaveResult =
        withContext(persistenceDispatcher) {
            val entity = try {
                ExerciseRunMapper.toEntity(run)
            } catch (exception: Throwable) {
                return@withContext ExerciseRunSaveResult.Failed(
                    exception.toError(run.runId, Operation.SAVE),
                )
            }
            try {
                val existing = dao.getById(run.runId)
                when {
                    existing == entity -> ExerciseRunSaveResult.Saved
                    existing != null -> ExerciseRunSaveResult.Failed(
                        ExerciseRunPersistenceError.SaveFailure(run.runId),
                    )
                    else -> {
                        dao.insert(entity)
                        ExerciseRunSaveResult.Saved
                    }
                }
            } catch (exception: Throwable) {
                // A concurrent identical insert is also an idempotent success.
                try {
                    if (dao.getById(run.runId) == entity) {
                        ExerciseRunSaveResult.Saved
                    } else {
                        ExerciseRunSaveResult.Failed(
                            exception.toError(run.runId, Operation.SAVE),
                        )
                    }
                } catch (verificationFailure: Throwable) {
                    ExerciseRunSaveResult.Failed(
                        verificationFailure.toError(run.runId, Operation.SAVE),
                    )
                }
            }
        }

    override suspend fun getRun(runId: String): ExerciseRunLoadResult =
        withContext(persistenceDispatcher) {
            if (runId.isBlank()) {
                return@withContext ExerciseRunLoadResult.Failed(
                    ExerciseRunPersistenceError.LoadFailure(runId),
                )
            }
            try {
                dao.getById(runId)?.toLoadResult()
                    ?: ExerciseRunLoadResult.Failed(
                        ExerciseRunPersistenceError.MissingRun(runId),
                    )
            } catch (exception: Throwable) {
                ExerciseRunLoadResult.Failed(exception.toError(runId, Operation.LOAD))
            }
        }

    override fun observeRun(runId: String): Flow<ExerciseRunLoadResult> {
        if (runId.isBlank()) {
            return flowOf(
                ExerciseRunLoadResult.Failed(
                    ExerciseRunPersistenceError.LoadFailure(runId),
                ),
            )
        }
        return dao.observeById(runId)
            .map { entity ->
                entity?.toLoadResult()
                    ?: ExerciseRunLoadResult.Failed(
                        ExerciseRunPersistenceError.MissingRun(runId),
                    )
            }
            .catch { exception ->
                emit(ExerciseRunLoadResult.Failed(exception.toError(runId, Operation.LOAD)))
            }
            .flowOn(persistenceDispatcher)
    }

    override fun observeRunsForExercise(exerciseId: String): Flow<ExerciseRunCollection> {
        if (exerciseId.isBlank()) {
            return flowOf(
                ExerciseRunCollection(
                    emptyList(),
                    listOf(ExerciseRunPersistenceError.LoadFailure(null)),
                ),
            )
        }
        return dao.observeForExercise(exerciseId).decodedCollection()
    }

    override fun observeAllRuns(): Flow<ExerciseRunCollection> =
        dao.observeAll().decodedCollection()

    override fun observeRunSummariesForExercise(
        exerciseId: String,
    ): Flow<ExerciseRunSummaryCollection> {
        if (exerciseId.isBlank()) {
            return flowOf(
                ExerciseRunSummaryCollection(
                    emptyList(),
                    ExerciseRunPersistenceError.LoadFailure(null),
                ),
            )
        }
        return dao.observeSummariesForExercise(exerciseId)
            .map { rows ->
                ExerciseRunSummaryCollection(rows.map(ExerciseRunMapper::toSummary))
            }
            .catch { exception ->
                emit(
                    ExerciseRunSummaryCollection(
                        emptyList(),
                        exception.toError(null, Operation.LOAD),
                    ),
                )
            }
            .flowOn(persistenceDispatcher)
    }

    override fun observeAllRunSummaries(): Flow<ExerciseRunSummaryCollection> =
        dao.observeAllSummaries()
            .map { rows ->
                ExerciseRunSummaryCollection(rows.map(ExerciseRunMapper::toSummary))
            }
            .catch { exception ->
                emit(
                    ExerciseRunSummaryCollection(
                        emptyList(),
                        exception.toError(null, Operation.LOAD),
                    ),
                )
            }
            .flowOn(persistenceDispatcher)

    override suspend fun deleteRun(runId: String): ExerciseRunDeleteResult =
        withContext(persistenceDispatcher) {
            try {
                if (dao.deleteById(runId) == 0) {
                    ExerciseRunDeleteResult.Failed(
                        ExerciseRunPersistenceError.MissingRun(runId),
                    )
                } else {
                    ExerciseRunDeleteResult.Deleted
                }
            } catch (exception: Throwable) {
                ExerciseRunDeleteResult.Failed(exception.toError(runId, Operation.DELETE))
            }
        }

    override suspend fun deleteRunsForExercise(exerciseId: String): ExerciseRunDeleteResult =
        withContext(persistenceDispatcher) {
            try {
                dao.deleteForExercise(exerciseId)
                ExerciseRunDeleteResult.Deleted
            } catch (exception: Throwable) {
                ExerciseRunDeleteResult.Failed(exception.toError(null, Operation.DELETE))
            }
        }

    private fun Flow<List<ExerciseRunEntity>>.decodedCollection(): Flow<ExerciseRunCollection> =
        map { entities ->
            val runs = ArrayList<ExerciseRun>(entities.size)
            val failures = ArrayList<ExerciseRunPersistenceError>()
            entities.forEach { entity ->
                when (val loaded = entity.toLoadResult()) {
                    is ExerciseRunLoadResult.Found -> runs += loaded.run
                    is ExerciseRunLoadResult.Failed -> failures += loaded.error
                }
            }
            ExerciseRunCollection(runs, failures)
        }.catch { exception ->
            emit(
                ExerciseRunCollection(
                    emptyList(),
                    listOf(exception.toError(null, Operation.LOAD)),
                ),
            )
        }.flowOn(persistenceDispatcher)

    private fun ExerciseRunEntity.toLoadResult(): ExerciseRunLoadResult = try {
        ExerciseRunLoadResult.Found(ExerciseRunMapper.fromEntity(this))
    } catch (exception: Throwable) {
        ExerciseRunLoadResult.Failed(exception.toError(runId, Operation.LOAD))
    }

    private fun Throwable.toError(
        runId: String?,
        operation: Operation,
    ): ExerciseRunPersistenceError {
        if (this is CancellationException) throw this
        return when (this) {
            is UnsupportedRunSchemaException -> ExerciseRunPersistenceError.UnsupportedSchema(
                checkNotNull(runId), schemaVersion,
            )
            is InvalidProductionGraphPayloadException ->
                ExerciseRunPersistenceError.InvalidGraphPayload(checkNotNull(runId))
            is RunPayloadTooLargeException -> ExerciseRunPersistenceError.PayloadTooLarge(
                checkNotNull(runId), sizeBytes, ExerciseRunPayloadCodec.MAXIMUM_PAYLOAD_BYTES,
            )
            is CorruptedRunPayloadException ->
                ExerciseRunPersistenceError.CorruptedPayload(checkNotNull(runId))
            else -> when {
                isMigrationFailure() ->
                    ExerciseRunPersistenceError.DatabaseMigrationFailure(runId)
                operation == Operation.SAVE ->
                    ExerciseRunPersistenceError.SaveFailure(checkNotNull(runId))
                operation == Operation.DELETE -> ExerciseRunPersistenceError.DeleteFailure(runId)
                else -> ExerciseRunPersistenceError.LoadFailure(runId)
            }
        }
    }

    private fun Throwable.isMigrationFailure(): Boolean =
        generateSequence(this) { it.cause }.any { cause ->
            val message = cause.message.orEmpty().lowercase()
            "migration" in message || "room cannot verify the data integrity" in message ||
                "schema validation" in message
        }

    private enum class Operation { SAVE, LOAD, DELETE }

    companion object {
        fun create(context: Context): ExerciseRunRepository = RoomExerciseRunRepository(
            ExerciseRunDatabase.getInstance(context).exerciseRunDao(),
        )
    }
}
