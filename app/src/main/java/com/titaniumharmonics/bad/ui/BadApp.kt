package com.titaniumharmonics.bad.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.titaniumharmonics.bad.ui.editor.ExerciseEditorRoute
import com.titaniumharmonics.bad.ui.library.ExerciseLibraryRoute
import com.titaniumharmonics.bad.ui.practice.PracticeRoute

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
                onDocumentLoadConsumed = viewModel::consumePracticeDocumentToLoad,
                fileOperationsEnabled = uiState.storageInitializationComplete,
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
            )
        }
    }
}
