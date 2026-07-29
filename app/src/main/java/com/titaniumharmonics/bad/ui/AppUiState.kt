package com.titaniumharmonics.bad.ui

enum class AppDestination {
    PRACTICE,
    EXERCISE_EDITOR,
}

data class AppUiState(
    val destination: AppDestination = AppDestination.PRACTICE,
    val editorDocumentUri: String? = null,
    val defaultExerciseFolderUri: String? = null,
    val storageInitializationComplete: Boolean = false,
)
