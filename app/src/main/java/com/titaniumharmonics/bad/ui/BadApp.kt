package com.titaniumharmonics.bad.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.titaniumharmonics.bad.exercise.OpenExerciseDocumentContract
import com.titaniumharmonics.bad.exercise.OpenExerciseDocumentRequest
import com.titaniumharmonics.bad.ui.editor.ExerciseEditorRoute
import com.titaniumharmonics.bad.ui.practice.PracticeRoute

@Composable
fun BadApp(
    viewModel: AppViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val modifyExercisePicker = rememberLauncherForActivityResult(
        contract = OpenExerciseDocumentContract(),
    ) { documentUri ->
        documentUri?.let { viewModel.modifyExercise(it.toString()) }
    }

    BackHandler(enabled = uiState.destination == AppDestination.EXERCISE_EDITOR) {
        viewModel.closeExerciseEditor()
    }

    when (uiState.destination) {
        AppDestination.PRACTICE -> {
            PracticeRoute(
                onCreateExercise = viewModel::createExercise,
                onModifyExercise = {
                    modifyExercisePicker.launch(
                        OpenExerciseDocumentRequest(
                            mimeTypes = EXERCISE_MIME_TYPES,
                            initialFolderUri = uiState.defaultExerciseFolderUri?.let(Uri::parse),
                        ),
                    )
                },
                defaultExerciseFolderUri = uiState.defaultExerciseFolderUri,
                fileOperationsEnabled = uiState.storageInitializationComplete,
            )
        }
        AppDestination.EXERCISE_EDITOR -> {
            ExerciseEditorRoute(
                documentUri = uiState.editorDocumentUri,
                defaultExerciseFolderUri = uiState.defaultExerciseFolderUri,
                onNavigateBack = viewModel::closeExerciseEditor,
            )
        }
    }
}

private val EXERCISE_MIME_TYPES = arrayOf(
    "application/json",
    "text/json",
    "text/plain",
)
