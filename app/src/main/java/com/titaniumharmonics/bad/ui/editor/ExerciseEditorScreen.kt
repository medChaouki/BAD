package com.titaniumharmonics.bad.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.titaniumharmonics.bad.exercise.CreateExerciseDocumentContract
import com.titaniumharmonics.bad.exercise.CreateExerciseDocumentRequest
import com.titaniumharmonics.bad.exercise.ExerciseDocumentCatalog
import com.titaniumharmonics.bad.exercise.MeasureSubdivision
import com.titaniumharmonics.bad.ui.theme.BADTheme
import kotlin.math.roundToInt

@Composable
fun ExerciseEditorRoute(
    documentUri: String?,
    defaultExerciseFolderUri: String?,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val viewModelFactory = remember(context) {
        ExerciseEditorViewModel.factory(context)
    }
    val viewModel: ExerciseEditorViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val createExerciseDocument = rememberLauncherForActivityResult(
        contract = CreateExerciseDocumentContract(),
    ) { createdDocumentUri ->
        createdDocumentUri?.let { documentUri ->
            ExerciseDocumentCatalog(context).rememberDefaultFolderDocument(documentUri)
            viewModel.saveExercise(documentUri.toString())
        }
    }

    LaunchedEffect(documentUri) {
        if (documentUri == null) {
            viewModel.createExercise()
        } else {
            viewModel.loadExercise(documentUri)
        }
    }

    ExerciseEditorScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onExerciseNameChange = viewModel::setExerciseName,
        onTempoBpmChange = viewModel::setTempoBpmText,
        onAddMeasure = viewModel::addMeasure,
        onDeleteMeasure = viewModel::deleteMeasure,
        onMeasureSubdivisionChange = viewModel::setMeasureSubdivision,
        onToggleMeasureNote = viewModel::toggleMeasureNote,
        onSave = {
            if (uiState.sourceDocumentUri == null) {
                createExerciseDocument.launch(
                    CreateExerciseDocumentRequest(
                        fileName = uiState.suggestedFileName(),
                        initialFolderUri = defaultExerciseFolderUri?.let(Uri::parse),
                    ),
                )
            } else {
                viewModel.saveExercise()
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditorScreen(
    uiState: ExerciseEditorUiState,
    onNavigateBack: () -> Unit,
    onExerciseNameChange: (String) -> Unit,
    onTempoBpmChange: (String) -> Unit,
    onAddMeasure: () -> Unit,
    onDeleteMeasure: (Int) -> Unit,
    onMeasureSubdivisionChange: (Int, MeasureSubdivision) -> Unit,
    onToggleMeasureNote: (Int, Long) -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Exercise Editor",
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
            OutlinedTextField(
                value = uiState.exerciseName,
                onValueChange = onExerciseNameChange,
                label = { Text("Exercise name") },
                singleLine = true,
                enabled = !uiState.isLoading && !uiState.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.tempoBpmText,
                onValueChange = onTempoBpmChange,
                label = { Text("BPM") },
                singleLine = true,
                enabled = !uiState.isLoading && !uiState.isSaving,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Measures",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            MeasureList(
                measures = uiState.measures,
                onDeleteMeasure = onDeleteMeasure,
                onMeasureSubdivisionChange = onMeasureSubdivisionChange,
                onToggleMeasureNote = onToggleMeasureNote,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            Button(
                onClick = onAddMeasure,
                enabled = !uiState.isLoading && !uiState.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add Measure")
            }
            uiState.errorMessage?.let { errorMessage ->
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            uiState.message?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = onSave,
                enabled = uiState.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        uiState.isSaving -> "Saving…"
                        uiState.sourceDocumentUri == null -> "Save exercise"
                        else -> "Overwrite exercise"
                    },
                )
            }
        }
    }
}

@Composable
private fun MeasureList(
    measures: List<EditorMeasureUiState>,
    onDeleteMeasure: (Int) -> Unit,
    onMeasureSubdivisionChange: (Int, MeasureSubdivision) -> Unit,
    onToggleMeasureNote: (Int, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        if (measures.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No measures yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(
                    items = measures,
                    key = { _, measure -> measure.id },
                ) { index, _ ->
                    val measure = measures[index]
                    SwipeToDeleteMeasure(
                        measure = measure,
                        measureNumber = index + 1,
                        onDelete = { onDeleteMeasure(measure.id) },
                        onSubdivisionChange = { subdivision ->
                            onMeasureSubdivisionChange(measure.id, subdivision)
                        },
                        onToggleNote = { positionWithinMeasureTicks ->
                            onToggleMeasureNote(
                                measure.id,
                                positionWithinMeasureTicks,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeToDeleteMeasure(
    measure: EditorMeasureUiState,
    measureNumber: Int,
    onDelete: () -> Unit,
    onSubdivisionChange: (MeasureSubdivision) -> Unit,
    onToggleNote: (Long) -> Unit,
) {
    val deleteActionWidth = 104.dp
    val deleteActionWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        deleteActionWidth.toPx()
    }
    var offsetX by remember(measure.id) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds(),
    ) {
        if (offsetX < 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier = Modifier
                        .width(deleteActionWidth)
                        .fillMaxHeight()
                        .background(
                            color = MaterialTheme.colorScheme.error,
                            shape = MaterialTheme.shapes.medium,
                        )
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.onError,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        MeasureGridCard(
            measure = measure,
            measureNumber = measureNumber,
            onSubdivisionChange = onSubdivisionChange,
            onToggleNote = onToggleNote,
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(measure.id, deleteActionWidthPx) {
                    detectHorizontalDragGestures(
                        onDragCancel = {
                            offsetX = 0f
                        },
                        onDragEnd = {
                            offsetX = if (offsetX <= -deleteActionWidthPx / 2f) {
                                -deleteActionWidthPx
                            } else {
                                0f
                            }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount)
                            .coerceIn(-deleteActionWidthPx, 0f)
                    }
                },
        )
    }
}

@Composable
private fun MeasureGridCard(
    measure: EditorMeasureUiState,
    measureNumber: Int,
    onSubdivisionChange: (MeasureSubdivision) -> Unit,
    onToggleNote: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Measure $measureNumber",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            SubdivisionSelector(
                selectedSubdivision = measure.subdivision,
                onSubdivisionChange = onSubdivisionChange,
            )
            measure.gridUnavailableReason?.let { reason ->
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (measure.gridUnavailableReason == null) {
                RhythmicGrid(
                    measure = measure,
                    onToggleNote = onToggleNote,
                )
            }
            if (measure.unmappedNoteCount > 0) {
                Text(
                    text = if (measure.unmappedNoteCount == 1) {
                        "This measure contains 1 note outside the selected grid."
                    } else {
                        "This measure contains ${measure.unmappedNoteCount} notes " +
                            "outside the selected grid."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SubdivisionSelector(
    selectedSubdivision: MeasureSubdivision,
    onSubdivisionChange: (MeasureSubdivision) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Subdivision",
            style = MaterialTheme.typography.labelLarge,
        )
        MeasureSubdivision.entries.chunked(2).forEach { rowSubdivisions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowSubdivisions.forEach { subdivision ->
                    FilterChip(
                        selected = subdivision == selectedSubdivision,
                        onClick = { onSubdivisionChange(subdivision) },
                        label = { Text(subdivision.displayName) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RhythmicGrid(
    measure: EditorMeasureUiState,
    onToggleNote: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (1..4).forEach { beatNumber ->
            val beatSlots = measure.slots.filter { slot ->
                slot.beatNumber == beatNumber
            }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                ) {
                    beatSlots.forEach { slot ->
                        RhythmSlot(
                            slot = slot,
                            onToggle = {
                                onToggleNote(slot.positionWithinMeasureTicks)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RhythmSlot(
    slot: EditorRhythmSlotUiState,
    onToggle: () -> Unit,
) {
    Column(
        modifier = Modifier.width(44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (slot.isAccented) 28.dp else 24.dp)
                .clickable(onClick = onToggle)
                .background(
                    color = if (slot.hasNote) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                    shape = CircleShape,
                )
                .border(
                    width = 2.dp,
                    color = if (slot.hasNote) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    shape = CircleShape,
                ),
        )
        Text(
            text = slot.countLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (slot.isBeatStart) {
                FontWeight.Bold
            } else {
                FontWeight.Normal
            },
        )
    }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 800)
@Composable
private fun ExerciseEditorScreenPreview() {
    BADTheme {
        ExerciseEditorScreen(
            uiState = ExerciseEditorUiState(
                exerciseName = "Quarter Note Inspection",
                tempoBpmText = "100",
                measures = listOf(
                    EditorRhythmGrid.buildMeasure(
                        id = 1,
                        editedMeasureIndex = 0,
                    ),
                    EditorRhythmGrid.buildMeasure(
                        id = 2,
                        editedMeasureIndex = 1,
                        subdivision = MeasureSubdivision.SIXTEENTH,
                    ),
                ),
            ),
            onNavigateBack = {},
            onExerciseNameChange = {},
            onTempoBpmChange = {},
            onAddMeasure = {},
            onDeleteMeasure = {},
            onMeasureSubdivisionChange = { _, _ -> },
            onToggleMeasureNote = { _, _ -> },
            onSave = {},
        )
    }
}

private fun ExerciseEditorUiState.suggestedFileName(): String {
    val baseName = exerciseName
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "exercise" }
    return "$baseName.json"
}
