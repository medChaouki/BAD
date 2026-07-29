package com.titaniumharmonics.bad.ui.library

import com.titaniumharmonics.bad.exercise.ExerciseLibraryItem

data class ExerciseLibraryUiState(
    val exercises: List<ExerciseLibraryItem> = emptyList(),
    val isLoading: Boolean = false,
    val deletingDocumentUri: String? = null,
    val exercisePendingDeletion: ExerciseLibraryItem? = null,
    val errorMessage: String? = null,
)
