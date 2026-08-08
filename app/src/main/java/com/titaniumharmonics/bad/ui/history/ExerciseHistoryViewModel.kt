package com.titaniumharmonics.bad.ui.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.titaniumharmonics.bad.exercise.DefaultExerciseLibraryRepository
import com.titaniumharmonics.bad.exercise.ExerciseLibraryRepository
import com.titaniumharmonics.bad.history.ExerciseRunDeleteResult
import com.titaniumharmonics.bad.history.ExerciseRunPersistenceError
import com.titaniumharmonics.bad.history.ExerciseRunRepository
import com.titaniumharmonics.bad.history.ExerciseRunSummary
import com.titaniumharmonics.bad.history.persistence.RoomExerciseRunRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExerciseHistoryViewModel internal constructor(
    private val exerciseId: String,
    private val runRepository: ExerciseRunRepository,
    private val exerciseLibraryRepository: ExerciseLibraryRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<ExerciseHistoryUiState>(
        ExerciseHistoryUiState.Loading,
    )
    val uiState: StateFlow<ExerciseHistoryUiState> = mutableUiState.asStateFlow()

    private var summaries: List<ExerciseRunSummary> = emptyList()
    private var exerciseName: String = "Exercise history"
    private var selectedBpm: Double? = null
    private var sortMode: ExerciseHistorySortMode = ExerciseHistorySortMode.NEWEST_FIRST
    private var runPendingDeletion: ExerciseRunSummary? = null
    private var deletingRunId: String? = null
    private var actionErrorMessage: String? = null
    private var observationJob: Job? = null
    private var deletionJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        observationJob?.cancel()
        mutableUiState.value = ExerciseHistoryUiState.Loading
        observationJob = viewModelScope.launch {
            val currentExerciseName = withContext(Dispatchers.IO) {
                runCatching {
                    exerciseLibraryRepository.findExercise(exerciseId)?.exerciseName
                }.getOrNull()
            }
            runRepository.observeRunSummariesForExercise(exerciseId).collect { result ->
                val failure = result.failure
                if (failure != null) {
                    mutableUiState.value = ExerciseHistoryUiState.Error(
                        exerciseId = exerciseId,
                        exerciseName = currentExerciseName ?: exerciseName,
                        message = failure.historyMessage(),
                    )
                } else {
                    summaries = result.summaries
                    exerciseName = currentExerciseName
                        ?: summaries.firstOrNull()?.exerciseNameSnapshot
                        ?: "Exercise history"
                    val availableBpms = availableHistoryBpms(summaries)
                    if (selectedBpm != null && availableBpms.none {
                            it.toBits() == selectedBpm?.toBits()
                        }
                    ) {
                        selectedBpm = null
                    }
                    publish()
                }
            }
        }
    }

    fun selectBpm(bpm: Double?) {
        selectedBpm = bpm
        publish()
    }

    fun selectSortMode(mode: ExerciseHistorySortMode) {
        sortMode = mode
        publish()
    }

    fun requestRunDeletion(runId: String) {
        if (deletingRunId != null) return
        runPendingDeletion = summaries.firstOrNull { it.runId == runId } ?: return
        actionErrorMessage = null
        publish()
    }

    fun cancelRunDeletion() {
        if (deletingRunId != null) return
        runPendingDeletion = null
        publish()
    }

    fun confirmRunDeletion() {
        if (deletionJob?.isActive == true) return
        val pending = runPendingDeletion ?: return
        deletingRunId = pending.runId
        actionErrorMessage = null
        publish()
        deletionJob = viewModelScope.launch {
            try {
                when (val result = runRepository.deleteRun(pending.runId)) {
                    ExerciseRunDeleteResult.Deleted -> Unit
                    is ExerciseRunDeleteResult.Failed -> {
                        if (result.error !is ExerciseRunPersistenceError.MissingRun) {
                            actionErrorMessage = "Unable to delete this saved run."
                        }
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                actionErrorMessage = "Unable to delete this saved run."
            } finally {
                runPendingDeletion = null
                deletingRunId = null
                publish()
            }
        }
    }

    private fun publish() {
        mutableUiState.value = if (summaries.isEmpty()) {
            ExerciseHistoryUiState.Empty(exerciseId, exerciseName)
        } else {
            val bpms = availableHistoryBpms(summaries)
            ExerciseHistoryUiState.Ready(
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                totalRunCount = summaries.size,
                runs = presentHistoryRuns(summaries, selectedBpm, sortMode),
                availableBpms = bpms,
                selectedBpm = selectedBpm,
                sortMode = sortMode,
                runPendingDeletion = runPendingDeletion,
                deletingRunId = deletingRunId,
                actionErrorMessage = actionErrorMessage,
            )
        }
    }

    override fun onCleared() {
        observationJob?.cancel()
        deletionJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(context: Context, exerciseId: String): ViewModelProvider.Factory {
            val applicationContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T {
                    require(modelClass.isAssignableFrom(ExerciseHistoryViewModel::class.java))
                    return ExerciseHistoryViewModel(
                        exerciseId = exerciseId,
                        runRepository = RoomExerciseRunRepository.create(applicationContext),
                        exerciseLibraryRepository = DefaultExerciseLibraryRepository(
                            applicationContext,
                        ),
                    ) as T
                }
            }
        }
    }
}

private fun ExerciseRunPersistenceError.historyMessage(): String = when (this) {
    is ExerciseRunPersistenceError.DatabaseMigrationFailure ->
        "Saved-run history is unavailable because the database could not be opened safely."
    else -> "Saved-run history could not be loaded."
}
