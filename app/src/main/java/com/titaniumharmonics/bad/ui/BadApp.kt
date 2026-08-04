package com.titaniumharmonics.bad.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.titaniumharmonics.bad.ui.editor.ExerciseEditorRoute
import com.titaniumharmonics.bad.ui.library.ExerciseLibraryRoute
import com.titaniumharmonics.bad.ui.practice.PracticeRoute
import com.titaniumharmonics.bad.ui.calibration.TimingCalibrationRoute
import com.titaniumharmonics.bad.ui.settings.SettingsRoute

@Composable
fun BadApp(
    viewModel: AppViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = uiState.destination != AppDestination.PRACTICE) {
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
    }
}
