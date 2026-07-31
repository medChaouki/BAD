package com.titaniumharmonics.bad.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.titaniumharmonics.bad.exercise.ExerciseDocumentCatalog
import com.titaniumharmonics.bad.exercise.ExerciseLibraryItem
import com.titaniumharmonics.bad.exercise.OpenExerciseDocumentContract
import com.titaniumharmonics.bad.exercise.OpenExerciseDocumentRequest
import com.titaniumharmonics.bad.ui.ExerciseLibraryPurpose

@Composable
fun ExerciseLibraryRoute(
    defaultExerciseFolderUri: String?,
    purpose: ExerciseLibraryPurpose,
    onOpenExercise: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModelFactory = remember(context) {
        ExerciseLibraryViewModel.factory(context)
    }
    val viewModel: ExerciseLibraryViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val browsePicker = rememberLauncherForActivityResult(
        contract = OpenExerciseDocumentContract(),
    ) { documentUri ->
        documentUri?.let { selectedUri ->
            ExerciseDocumentCatalog(context)
                .rememberDefaultFolderDocument(selectedUri)
            onOpenExercise(selectedUri.toString())
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    ExerciseLibraryScreen(
        uiState = uiState,
        purpose = purpose,
        onOpenExercise = { exercise -> onOpenExercise(exercise.documentUri) },
        onRequestDeletion = { exercise ->
            viewModel.requestDeletion(exercise.documentUri)
        },
        onCancelDeletion = viewModel::cancelDeletion,
        onConfirmDeletion = viewModel::confirmDeletion,
        onBrowse = {
            browsePicker.launch(
                OpenExerciseDocumentRequest(
                    mimeTypes = EXERCISE_MIME_TYPES,
                    initialFolderUri = defaultExerciseFolderUri?.let(Uri::parse),
                ),
            )
        },
        onNavigateBack = onNavigateBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseLibraryScreen(
    uiState: ExerciseLibraryUiState,
    purpose: ExerciseLibraryPurpose,
    onOpenExercise: (ExerciseLibraryItem) -> Unit,
    onRequestDeletion: (ExerciseLibraryItem) -> Unit,
    onCancelDeletion: () -> Unit,
    onConfirmDeletion: () -> Unit,
    onBrowse: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Exercise Library",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text("Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = when (purpose) {
                    ExerciseLibraryPurpose.PRACTICE ->
                        "Tap to load for practice. Press and hold to delete."
                    ExerciseLibraryPurpose.MODIFY ->
                        "Tap to modify. Press and hold to delete."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ExerciseLibraryList(
                uiState = uiState,
                onOpenExercise = onOpenExercise,
                onRequestDeletion = onRequestDeletion,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            uiState.errorMessage?.let { errorMessage ->
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedButton(
                onClick = onBrowse,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Browse other folders")
            }
        }
    }

    uiState.exercisePendingDeletion?.let { exercise ->
        AlertDialog(
            onDismissRequest = onCancelDeletion,
            title = { Text("Delete exercise file?") },
            text = {
                Text(
                    "\"${exercise.exerciseName}\" will be permanently deleted.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmDeletion,
                    enabled = uiState.deletingDocumentUri == null,
                ) {
                    Text(
                        text = if (uiState.deletingDocumentUri == null) {
                            "Delete"
                        } else {
                            "Deleting…"
                        },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onCancelDeletion,
                    enabled = uiState.deletingDocumentUri == null,
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ExerciseLibraryList(
    uiState: ExerciseLibraryUiState,
    onOpenExercise: (ExerciseLibraryItem) -> Unit,
    onRequestDeletion: (ExerciseLibraryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        uiState.isLoading -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        uiState.exercises.isEmpty() -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Text(
                    text = "No B.A.D. exercise files found.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        else -> {
            LazyColumn(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = uiState.exercises,
                    key = ExerciseLibraryItem::documentUri,
                ) { exercise ->
                    ExerciseLibraryCard(
                        exercise = exercise,
                        onOpen = { onOpenExercise(exercise) },
                        onLongPress = { onRequestDeletion(exercise) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExerciseLibraryCard(
    exercise: ExerciseLibraryItem,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onLongPress,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = exercise.exerciseName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = exercise.fileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${exercise.tempoBpm.toDisplayText()} BPM • " +
                    "${exercise.patternCount.toPatternCountText()} \u2022 " +
                    exercise.expandedMeasureCount.toMeasureCountText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Double.toDisplayText(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

private fun Int.toMeasureCountText(): String =
    if (this == 1) "1 measure" else "$this measures"

private fun Int.toPatternCountText(): String =
    if (this == 1) "1 pattern" else "$this patterns"

private val EXERCISE_MIME_TYPES = arrayOf(
    "application/json",
    "text/json",
    "text/plain",
)
