package com.titaniumharmonics.bad.ui.history

import com.titaniumharmonics.bad.history.ExerciseRunSummary

enum class ExerciseHistorySortMode(val displayName: String) {
    NEWEST_FIRST("Newest first"),
    OLDEST_FIRST("Oldest first"),
    BEST_ACCURACY("Best accuracy"),
    LOWEST_TIMING_ERROR("Lowest timing error"),
}

sealed interface ExerciseHistoryUiState {
    data object Loading : ExerciseHistoryUiState

    data class Ready(
        val exerciseId: String,
        val exerciseName: String,
        val totalRunCount: Int,
        val runs: List<ExerciseRunSummary>,
        val availableBpms: List<Double>,
        val selectedBpm: Double?,
        val sortMode: ExerciseHistorySortMode,
        val runPendingDeletion: ExerciseRunSummary? = null,
        val deletingRunId: String? = null,
        val actionErrorMessage: String? = null,
    ) : ExerciseHistoryUiState

    data class Empty(
        val exerciseId: String,
        val exerciseName: String,
    ) : ExerciseHistoryUiState

    data class Error(
        val exerciseId: String,
        val exerciseName: String,
        val message: String,
    ) : ExerciseHistoryUiState
}
