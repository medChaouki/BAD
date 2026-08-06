package com.titaniumharmonics.bad.ui

import com.titaniumharmonics.bad.audio.calibration.TimingCalibration
import com.titaniumharmonics.bad.audio.result.PracticeResult

enum class AppDestination {
    PRACTICE,
    EXERCISE_LIBRARY,
    EXERCISE_EDITOR,
    SETTINGS,
    TIMING_CALIBRATION,
    RESULTS,
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
    val activeTimingCalibration: TimingCalibration? = null,
    val practiceResult: PracticeResult? = null,
    val resultsDetailVisible: Boolean = false,
)

internal fun initialAppUiState(calibration: TimingCalibration?): AppUiState = AppUiState(
    destination = if (calibration == null) {
        AppDestination.TIMING_CALIBRATION
    } else {
        AppDestination.PRACTICE
    },
    activeTimingCalibration = calibration,
)

internal fun AppUiState.openExerciseLibrary(
    purpose: ExerciseLibraryPurpose,
): AppUiState = copy(
    destination = AppDestination.EXERCISE_LIBRARY,
    exerciseLibraryPurpose = purpose,
    editorDocumentUri = null,
    practiceResult = null,
    resultsDetailVisible = false,
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
    practiceResult = null,
    resultsDetailVisible = false,
)

internal fun AppUiState.openSettings(): AppUiState = copy(
    destination = AppDestination.SETTINGS,
)

internal fun AppUiState.openResults(result: PracticeResult): AppUiState = copy(
    destination = AppDestination.RESULTS,
    practiceResult = result,
    resultsDetailVisible = false,
)

internal fun AppUiState.navigateBack(): AppUiState = when (destination) {
    AppDestination.PRACTICE -> this
    AppDestination.EXERCISE_LIBRARY -> copy(destination = AppDestination.PRACTICE)
    AppDestination.EXERCISE_EDITOR -> copy(
        destination = editorReturnDestination,
        editorDocumentUri = null,
    )
    AppDestination.SETTINGS,
    AppDestination.TIMING_CALIBRATION,
    -> copy(destination = AppDestination.PRACTICE)
    AppDestination.RESULTS -> if (resultsDetailVisible) {
        copy(resultsDetailVisible = false)
    } else {
        copy(
            destination = AppDestination.PRACTICE,
            practiceResult = null,
        )
    }
}
