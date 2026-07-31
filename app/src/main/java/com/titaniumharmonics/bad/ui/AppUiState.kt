package com.titaniumharmonics.bad.ui

enum class AppDestination {
    PRACTICE,
    EXERCISE_LIBRARY,
    EXERCISE_EDITOR,
}

enum class ExerciseLibraryPurpose {
    PRACTICE,
    MODIFY,
}

data class AppUiState(
    val destination: AppDestination = AppDestination.PRACTICE,
    val exerciseLibraryPurpose: ExerciseLibraryPurpose = ExerciseLibraryPurpose.MODIFY,
    val practiceDocumentUriToLoad: String? = null,
    val startPracticeAfterLoad: Boolean = false,
    val editorDocumentUri: String? = null,
    val editorReturnDestination: AppDestination = AppDestination.PRACTICE,
    val defaultExerciseFolderUri: String? = null,
    val storageInitializationComplete: Boolean = false,
)

internal fun AppUiState.openExerciseLibrary(
    purpose: ExerciseLibraryPurpose,
): AppUiState = copy(
    destination = AppDestination.EXERCISE_LIBRARY,
    exerciseLibraryPurpose = purpose,
    editorDocumentUri = null,
)

internal fun AppUiState.openLibraryExercise(documentUri: String): AppUiState =
    when (exerciseLibraryPurpose) {
        ExerciseLibraryPurpose.PRACTICE -> copy(
            destination = AppDestination.PRACTICE,
            practiceDocumentUriToLoad = documentUri,
            startPracticeAfterLoad = false,
        )
        ExerciseLibraryPurpose.MODIFY -> copy(
            destination = AppDestination.EXERCISE_EDITOR,
            editorDocumentUri = documentUri,
            editorReturnDestination = AppDestination.EXERCISE_LIBRARY,
        )
    }

internal fun AppUiState.playEditorExercise(documentUri: String): AppUiState = copy(
    destination = AppDestination.PRACTICE,
    practiceDocumentUriToLoad = documentUri,
    startPracticeAfterLoad = true,
    editorDocumentUri = null,
)
