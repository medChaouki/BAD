package com.titaniumharmonics.bad.ui

import com.titaniumharmonics.bad.audio.calibration.TimingCalibration
import com.titaniumharmonics.bad.audio.result.PracticeResult
import com.titaniumharmonics.bad.audio.result.ProductionGraphModel
import com.titaniumharmonics.bad.history.ExerciseRun

sealed interface ResultsSource {
    data object CurrentRun : ResultsSource
    data class SavedRun(val runId: String) : ResultsSource
}

data class ResultsPresentationModel(
    val result: PracticeResult,
    val graphModel: ProductionGraphModel,
    val source: ResultsSource,
    val retryDocumentUri: String? = null,
) {
    val retryAvailable: Boolean
        get() = source == ResultsSource.CurrentRun || retryDocumentUri != null
}

sealed interface ResultsPresentationState {
    data object None : ResultsPresentationState
    data class Loading(val runId: String) : ResultsPresentationState
    data class Ready(val model: ResultsPresentationModel) : ResultsPresentationState
    data class LoadFailed(val runId: String, val message: String) : ResultsPresentationState
}

internal fun ExerciseRun.toSavedResultsPresentation(
    retryDocumentUri: String?,
): ResultsPresentationModel = ResultsPresentationModel(
    result = practiceResult,
    graphModel = productionGraph,
    source = ResultsSource.SavedRun(runId),
    retryDocumentUri = retryDocumentUri,
)

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
    val resultsPresentation: ResultsPresentationState = ResultsPresentationState.None,
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
    resultsPresentation = ResultsPresentationState.None,
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
    resultsPresentation = ResultsPresentationState.None,
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
    resultsPresentation = ResultsPresentationState.Ready(
        ResultsPresentationModel(
            result = result,
            graphModel = graphModel,
            source = ResultsSource.CurrentRun,
        ),
    ),
    resultsDetailVisible = false,
    resultsDebugVisible = false,
)

internal fun AppUiState.openProcessing(): AppUiState = copy(
    destination = AppDestination.PROCESSING,
    resultsPresentation = ResultsPresentationState.None,
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
            resultsPresentation = ResultsPresentationState.None,
        )
    }
}
