package com.titaniumharmonics.bad.history

import com.titaniumharmonics.bad.audio.result.PracticeResult
import com.titaniumharmonics.bad.audio.result.ProductionGraphModel

fun interface ExerciseRunIdGenerator {
    fun newRunId(): String

    companion object {
        val UUID: ExerciseRunIdGenerator = ExerciseRunIdGenerator {
            java.util.UUID.randomUUID().toString()
        }
    }
}

/** Maps an already-complete session result into immutable historical truth without reinterpretation. */
class ExerciseRunFactory(
    private val appVersion: String,
) {
    init {
        require(appVersion.isNotBlank()) { "appVersion must not be blank." }
    }

    fun create(
        runId: String,
        startedAtEpochMillis: Long,
        completedAtEpochMillis: Long,
        practiceResult: PracticeResult,
        productionGraph: ProductionGraphModel,
    ): ExerciseRun = ExerciseRun(
        runId = runId,
        startedAtEpochMillis = startedAtEpochMillis,
        completedAtEpochMillis = completedAtEpochMillis,
        practiceResult = practiceResult,
        productionGraph = productionGraph,
        appVersion = appVersion,
    )
}

sealed interface ExerciseRunSaveState {
    val runId: String?

    data object NotSaved : ExerciseRunSaveState {
        override val runId: String? = null
    }

    data class Saving(override val runId: String) : ExerciseRunSaveState
    data class Saved(override val runId: String) : ExerciseRunSaveState
    data class SaveFailed(
        override val runId: String,
        val error: ExerciseRunPersistenceError,
    ) : ExerciseRunSaveState
}

internal fun ExerciseRunSaveState.canStartSave(runId: String): Boolean = when (this) {
    ExerciseRunSaveState.NotSaved -> true
    is ExerciseRunSaveState.SaveFailed -> this.runId == runId
    is ExerciseRunSaveState.Saving,
    is ExerciseRunSaveState.Saved,
    -> false
}

/** Lifecycle-independent exactly-once coordinator for one immutable completed attempt. */
class ExerciseRunSaveCoordinator(
    private val repository: ExerciseRunRepository,
) {
    private val lock = Any()
    private var candidate: ExerciseRun? = null
    private var currentState: ExerciseRunSaveState = ExerciseRunSaveState.NotSaved

    val state: ExerciseRunSaveState
        get() = synchronized(lock) { currentState }

    fun stage(run: ExerciseRun): ExerciseRunSaveState = synchronized(lock) {
        if (candidate?.runId != run.runId) {
            candidate = run
            currentState = ExerciseRunSaveState.NotSaved
        }
        currentState
    }

    suspend fun save(): ExerciseRunSaveState {
        val run = synchronized(lock) {
            val staged = candidate ?: return ExerciseRunSaveState.NotSaved
            if (!currentState.canStartSave(staged.runId)) return currentState
            currentState = ExerciseRunSaveState.Saving(staged.runId)
            staged
        }
        return try {
            val next = when (val result = repository.saveRun(run)) {
                ExerciseRunSaveResult.Saved -> ExerciseRunSaveState.Saved(run.runId)
                is ExerciseRunSaveResult.Failed -> ExerciseRunSaveState.SaveFailed(
                    run.runId,
                    result.error,
                )
            }
            synchronized(lock) {
                if (candidate?.runId == run.runId) currentState = next
                currentState
            }
        } catch (exception: kotlinx.coroutines.CancellationException) {
            synchronized(lock) {
                if (candidate?.runId == run.runId &&
                    currentState is ExerciseRunSaveState.Saving
                ) {
                    currentState = ExerciseRunSaveState.NotSaved
                }
            }
            throw exception
        }
    }

    fun reset() = synchronized(lock) {
        candidate = null
        currentState = ExerciseRunSaveState.NotSaved
    }
}
