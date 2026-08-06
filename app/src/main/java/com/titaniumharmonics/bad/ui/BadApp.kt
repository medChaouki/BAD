package com.titaniumharmonics.bad.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.titaniumharmonics.bad.ui.editor.ExerciseEditorRoute
import com.titaniumharmonics.bad.ui.library.ExerciseLibraryRoute
import com.titaniumharmonics.bad.ui.practice.PracticeRoute
import com.titaniumharmonics.bad.ui.practice.PracticeViewModel
import com.titaniumharmonics.bad.ui.practice.DebugAnalysisRoute
import com.titaniumharmonics.bad.ui.results.ResultsScreen
import com.titaniumharmonics.bad.ui.calibration.TimingCalibrationRoute
import com.titaniumharmonics.bad.ui.settings.SettingsRoute
import com.titaniumharmonics.bad.BuildConfig

@Composable
fun BadApp(
    viewModel: AppViewModel = viewModel(),
    practiceViewModel: PracticeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = uiState.destination != AppDestination.PRACTICE) {
        if (uiState.destination == AppDestination.RESULTS &&
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
                onResultsReady = viewModel::openResults,
                viewModel = practiceViewModel,
            )
        }
        AppDestination.EXERCISE_LIBRARY -> {
            ExerciseLibraryRoute(
                defaultExerciseFolderUri = uiState.defaultExerciseFolderUri,
                purpose = uiState.exerciseLibraryPurpose,
                onOpenExercise = viewModel::openLibraryExercise,
                onNavigateBack = viewModel::navigateBack,
            )
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
        AppDestination.RESULTS -> {
            val result = uiState.practiceResult
            val graph = uiState.productionGraph
            if (result == null || graph == null) {
                LaunchedEffect(Unit) { viewModel.leaveResultsForPractice() }
            } else if (BuildConfig.DEBUG && uiState.resultsDebugVisible) {
                DebugAnalysisRoute(
                    onNavigateBack = viewModel::navigateBack,
                    viewModel = practiceViewModel,
                )
            } else {
                ResultsScreen(
                    result = result,
                    graphModel = graph,
                    showDetails = uiState.resultsDetailVisible,
                    onOpenDetails = viewModel::showResultDetails,
                    onBack = {
                        if (!uiState.resultsDetailVisible && !uiState.resultsDebugVisible) {
                            practiceViewModel.prepareForNextRun()
                        }
                        viewModel.navigateBack()
                    },
                    onRetry = {
                        viewModel.leaveResultsForPractice()
                        practiceViewModel.retryExercise()
                    },
                    onReturnToPractice = {
                        practiceViewModel.prepareForNextRun()
                        viewModel.leaveResultsForPractice()
                    },
                    onReturnToLibrary = {
                        practiceViewModel.unloadExercise()
                        viewModel.openExerciseLibraryForPractice()
                    },
                    onOpenDebug = if (BuildConfig.DEBUG) viewModel::showResultDebug else null,
                )
            }
        }
    }
}
