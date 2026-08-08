package com.titaniumharmonics.bad.ui

import com.titaniumharmonics.bad.audio.calibration.TimingCalibration
import com.titaniumharmonics.bad.audio.result.PracticeResult
import com.titaniumharmonics.bad.audio.result.ProductionGraphModel

enum class AppDestination {
    PRACTICE,
    EXERCISE_LIBRARY,
    EXERCISE_EDITOR,
    SETTINGS,
    TIMING_CALIBRATION,
    PROCESSING,
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
    val productionGraph: ProductionGraphModel? = null,
    val resultsDetailVisible: Boolean = false,
    val resultsDebugVisible: Boolean = false,
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
    productionGraph = null,
    resultsDetailVisible = false,
    resultsDebugVisible = false,
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
    productionGraph = null,
    resultsDetailVisible = false,
    resultsDebugVisible = false,
)

internal fun AppUiState.openSettings(): AppUiState = copy(
    destination = AppDestination.SETTINGS,
)

internal fun AppUiState.openResults(
    result: PracticeResult,
    graphModel: ProductionGraphModel,
): AppUiState = copy(
    destination = AppDestination.RESULTS,
    practiceResult = result,
    productionGraph = graphModel,
    resultsDetailVisible = false,
    resultsDebugVisible = false,
)

internal fun AppUiState.openProcessing(): AppUiState = copy(
    destination = AppDestination.PROCESSING,
    practiceResult = null,
    productionGraph = null,
    resultsDetailVisible = false,
    resultsDebugVisible = false,
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
    AppDestination.PROCESSING -> this
    AppDestination.RESULTS -> if (resultsDetailVisible) {
        copy(resultsDetailVisible = false)
    } else if (resultsDebugVisible) {
        copy(resultsDebugVisible = false)
    } else {
        copy(
            destination = AppDestination.PRACTICE,
            practiceResult = null,
            productionGraph = null,
        )
    }
}
