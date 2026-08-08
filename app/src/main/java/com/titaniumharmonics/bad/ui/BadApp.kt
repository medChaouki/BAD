package com.titaniumharmonics.bad.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.titaniumharmonics.bad.ui.editor.ExerciseEditorRoute
import com.titaniumharmonics.bad.ui.history.ExerciseHistoryRoute
import com.titaniumharmonics.bad.ui.library.ExerciseLibraryRoute
import com.titaniumharmonics.bad.ui.practice.PracticeRoute
import com.titaniumharmonics.bad.ui.practice.PracticeViewModel
import com.titaniumharmonics.bad.ui.practice.DebugAnalysisRoute
import com.titaniumharmonics.bad.ui.results.ResultsScreen
import com.titaniumharmonics.bad.ui.results.SavedRunLoadScreen
import com.titaniumharmonics.bad.ui.processing.ProcessingRoute
import com.titaniumharmonics.bad.ui.calibration.TimingCalibrationRoute
import com.titaniumharmonics.bad.ui.settings.SettingsRoute
import com.titaniumharmonics.bad.BuildConfig
import com.titaniumharmonics.bad.history.ExerciseRunSaveState

@Composable
fun BadApp(
    viewModel: AppViewModel = viewModel(),
    practiceViewModel: PracticeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = uiState.destination != AppDestination.PRACTICE) {
        val results = uiState.resultsPresentation as? ResultsPresentationState.Ready
        if (uiState.destination == AppDestination.RESULTS &&
            results?.model?.source == ResultsSource.CurrentRun &&
            !uiState.resultsDetailVisible && !uiState.resultsDebugVisible
        ) {
            practiceViewModel.prepareForNextRun()
        }
        viewModel.navigateBack()
    }

    when (uiState.destination) {
        AppDestination.PRACTICE -> {
            PracticeRoute(
                onCreateExercise = viewModel::createExercise,
                onModifyExercise = viewModel::openExerciseLibraryForModify,
                onLoadExercise = viewModel::openExerciseLibraryForPractice,
                documentUriToLoad = uiState.practiceDocumentUriToLoad,
                startAfterLoad = uiState.startPracticeAfterLoad,
                onDocumentLoadConsumed = viewModel::consumePracticeDocumentToLoad,
                fileOperationsEnabled = uiState.storageInitializationComplete,
                onOpenSettings = viewModel::openSettings,
                onProcessingStarted = viewModel::openProcessing,
                viewModel = practiceViewModel,
            )
        }
        AppDestination.EXERCISE_LIBRARY -> {
            ExerciseLibraryRoute(
                defaultExerciseFolderUri = uiState.defaultExerciseFolderUri,
                purpose = uiState.exerciseLibraryPurpose,
                onOpenExercise = viewModel::openLibraryExercise,
                onOpenHistory = viewModel::openExerciseHistory,
                onNavigateBack = viewModel::navigateBack,
            )
        }
        AppDestination.EXERCISE_HISTORY -> {
            val exerciseId = uiState.historyExerciseId
            if (exerciseId == null) {
                LaunchedEffect(Unit) { viewModel.navigateBack() }
            } else {
                ExerciseHistoryRoute(
                    exerciseId = exerciseId,
                    onOpenRun = viewModel::openSavedRunFromHistory,
                    onNavigateBack = viewModel::navigateBack,
                )
            }
        }
        AppDestination.EXERCISE_EDITOR -> {
            ExerciseEditorRoute(
                documentUri = uiState.editorDocumentUri,
                defaultExerciseFolderUri = uiState.defaultExerciseFolderUri,
                onNavigateBack = viewModel::navigateBack,
                onPlayExercise = viewModel::playEditorExercise,
            )
        }
        AppDestination.SETTINGS -> {
            SettingsRoute(
                activeTimingCalibration = uiState.activeTimingCalibration,
                onOpenTimingCalibration = viewModel::openTimingCalibration,
                onNavigateBack = viewModel::navigateBack,
            )
        }
        AppDestination.TIMING_CALIBRATION -> {
            TimingCalibrationRoute(
                onNavigateBack = viewModel::navigateBack,
                onCalibrationChanged = viewModel::timingCalibrationChanged,
            )
        }
        AppDestination.PROCESSING -> {
            val processingUiState by practiceViewModel.uiState.collectAsStateWithLifecycle()
            ProcessingRoute(
                uiState = processingUiState,
                onResultsReady = viewModel::openResults,
                onRetry = {
                    if (processingUiState.recordedSession == null) {
                        practiceViewModel.prepareForNextRun()
                        viewModel.leaveResultsForPractice()
                    } else {
                        practiceViewModel.retryProcessing()
                    }
                },
            )
        }
        AppDestination.RESULTS -> {
            val practiceUiState by practiceViewModel.uiState.collectAsStateWithLifecycle()
            when (val presentation = uiState.resultsPresentation) {
                ResultsPresentationState.None -> {
                    LaunchedEffect(Unit) { viewModel.leaveResultsForPractice() }
                }
                is ResultsPresentationState.Loading -> SavedRunLoadScreen(
                    message = "",
                    loading = true,
                    onBack = viewModel::navigateBack,
                )
                is ResultsPresentationState.LoadFailed -> SavedRunLoadScreen(
                    message = presentation.message,
                    loading = false,
                    onBack = viewModel::navigateBack,
                )
                is ResultsPresentationState.Ready -> {
                    val model = presentation.model
                    val currentRun = model.source == ResultsSource.CurrentRun
                    if (BuildConfig.DEBUG && currentRun && uiState.resultsDebugVisible) {
                        DebugAnalysisRoute(
                            onNavigateBack = viewModel::navigateBack,
                            viewModel = practiceViewModel,
                        )
                    } else {
                        val saveState = when (val source = model.source) {
                            ResultsSource.CurrentRun -> practiceUiState.runSaveState
                            is ResultsSource.SavedRun -> ExerciseRunSaveState.Saved(source.runId)
                        }
                        ResultsScreen(
                            result = model.result,
                            graphModel = model.graphModel,
                            showDetails = uiState.resultsDetailVisible,
                            onOpenDetails = viewModel::showResultDetails,
                            onBack = {
                                if (currentRun && !uiState.resultsDetailVisible &&
                                    !uiState.resultsDebugVisible
                                ) {
                                    practiceViewModel.prepareForNextRun()
                                }
                                viewModel.navigateBack()
                            },
                            onRetry = {
                                if (currentRun) {
                                    viewModel.leaveResultsForPractice()
                                    practiceViewModel.retryExercise()
                                } else {
                                    viewModel.retrySavedRun()
                                }
                            },
                            retryEnabled = model.retryAvailable,
                            onReturnToPractice = {
                                if (currentRun) practiceViewModel.prepareForNextRun()
                                viewModel.leaveResultsForPractice()
                            },
                            onReturnToLibrary = {
                                practiceViewModel.unloadExercise()
                                viewModel.openExerciseLibraryForPractice()
                            },
                            saveState = saveState,
                            onRetrySave = if (currentRun) {
                                practiceViewModel::retryRunSave
                            } else {
                                null
                            },
                            onOpenDebug = if (BuildConfig.DEBUG && currentRun) {
                                viewModel::showResultDebug
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}
