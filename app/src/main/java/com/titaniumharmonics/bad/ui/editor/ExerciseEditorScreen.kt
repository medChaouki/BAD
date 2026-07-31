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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.titaniumharmonics.bad.exercise.MeasurePatternConstraints
import com.titaniumharmonics.bad.ui.theme.BADTheme
import kotlin.math.roundToInt

@Composable
fun ExerciseEditorRoute(
    documentUri: String?,
    defaultExerciseFolderUri: String?,
    onNavigateBack: () -> Unit,
    onPlayExercise: (String) -> Unit,
) {
    val context = LocalContext.current
    val viewModelFactory = remember(context) {
        ExerciseEditorViewModel.factory(context)
    }
    val viewModel: ExerciseEditorViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var playAfterCreatingDocument by rememberSaveable {
        mutableStateOf(false)
    }
    val createExerciseDocument = rememberLauncherForActivityResult(
        contract = CreateExerciseDocumentContract(),
    ) { createdDocumentUri ->
        val shouldPlayAfterSave = playAfterCreatingDocument
        playAfterCreatingDocument = false
        createdDocumentUri?.let { documentUri ->
            ExerciseDocumentCatalog(context).rememberDefaultFolderDocument(documentUri)
            viewModel.saveExercise(
                destinationDocumentUri = documentUri.toString(),
                playAfterSave = shouldPlayAfterSave,
            )
        }
    }

    LaunchedEffect(documentUri) {
        if (documentUri == null) {
            viewModel.createExercise()
        } else {
            viewModel.loadExercise(documentUri)
        }
    }

    LaunchedEffect(uiState.documentUriReadyToPlay) {
        uiState.documentUriReadyToPlay?.let { savedDocumentUri ->
            viewModel.consumePlayRequest()
            onPlayExercise(savedDocumentUri)
        }
    }

    ExerciseEditorScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onExerciseNameChange = viewModel::setExerciseName,
        onTempoBpmChange = viewModel::setTempoBpmText,
        onAddMeasure = viewModel::addMeasure,
        onDeleteMeasure = viewModel::deleteMeasure,
        onDuplicateMeasure = viewModel::duplicateMeasurePattern,
        onClearMeasure = viewModel::clearMeasurePattern,
        onMoveMeasureUp = viewModel::moveMeasurePatternUp,
        onMoveMeasureDown = viewModel::moveMeasurePatternDown,
        onDecreaseMeasureMultiplier = viewModel::decreaseMeasureMultiplier,
        onIncreaseMeasureMultiplier = viewModel::increaseMeasureMultiplier,
        onMeasureSubdivisionChange = viewModel::setMeasureSubdivision,
        onToggleMeasureNote = viewModel::toggleMeasureNote,
        onSave = {
            if (uiState.sourceDocumentUri == null) {
                playAfterCreatingDocument = false
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
        onPlay = {
            if (uiState.sourceDocumentUri == null) {
                playAfterCreatingDocument = true
                createExerciseDocument.launch(
                    CreateExerciseDocumentRequest(
                        fileName = uiState.suggestedFileName(),
                        initialFolderUri = defaultExerciseFolderUri?.let(Uri::parse),
                    ),
                )
            } else {
                viewModel.saveExercise(playAfterSave = true)
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
    onDuplicateMeasure: (Int) -> Unit,
    onClearMeasure: (Int) -> Unit,
    onMoveMeasureUp: (Int) -> Unit,
    onMoveMeasureDown: (Int) -> Unit,
    onDecreaseMeasureMultiplier: (Int) -> Unit,
    onIncreaseMeasureMultiplier: (Int) -> Unit,
    onMeasureSubdivisionChange: (Int, MeasureSubdivision) -> Unit,
    onToggleMeasureNote: (Int, Long) -> Unit,
    onSave: () -> Unit,
    onPlay: () -> Unit,
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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Measure patterns",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = uiState.patternSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MeasureList(
                measures = uiState.measures,
                onDeleteMeasure = onDeleteMeasure,
                onDuplicateMeasure = onDuplicateMeasure,
                onClearMeasure = onClearMeasure,
                onMoveMeasureUp = onMoveMeasureUp,
                onMoveMeasureDown = onMoveMeasureDown,
                onDecreaseMeasureMultiplier = onDecreaseMeasureMultiplier,
                onIncreaseMeasureMultiplier = onIncreaseMeasureMultiplier,
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
                Text("Add Pattern")
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onSave,
                    enabled = uiState.canSave,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        when {
                            uiState.isSaving -> "Saving…"
                            uiState.sourceDocumentUri == null -> "Save exercise"
                            else -> "Overwrite exercise"
                        },
                    )
                }
                Button(
                    onClick = onPlay,
                    enabled = uiState.canSave,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Play exercise")
                }
            }
        }
    }
}

@Composable
private fun MeasureList(
    measures: List<EditorMeasureUiState>,
    onDeleteMeasure: (Int) -> Unit,
    onDuplicateMeasure: (Int) -> Unit,
    onClearMeasure: (Int) -> Unit,
    onMoveMeasureUp: (Int) -> Unit,
    onMoveMeasureDown: (Int) -> Unit,
    onDecreaseMeasureMultiplier: (Int) -> Unit,
    onIncreaseMeasureMultiplier: (Int) -> Unit,
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
                        canMoveUp = index > 0,
                        canMoveDown = index < measures.lastIndex,
                        onDelete = { onDeleteMeasure(measure.id) },
                        onDuplicate = { onDuplicateMeasure(measure.id) },
                        onClear = { onClearMeasure(measure.id) },
                        onMoveUp = { onMoveMeasureUp(measure.id) },
                        onMoveDown = { onMoveMeasureDown(measure.id) },
                        onDecreaseMultiplier = {
                            onDecreaseMeasureMultiplier(measure.id)
                        },
                        onIncreaseMultiplier = {
                            onIncreaseMeasureMultiplier(measure.id)
                        },
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
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onClear: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDecreaseMultiplier: () -> Unit,
    onIncreaseMultiplier: () -> Unit,
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
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
            onDuplicate = onDuplicate,
            onClear = onClear,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onDecreaseMultiplier = onDecreaseMultiplier,
            onIncreaseMultiplier = onIncreaseMultiplier,
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
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onDuplicate: () -> Unit,
    onClear: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDecreaseMultiplier: () -> Unit,
    onIncreaseMultiplier: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = measure.expandedMeasureLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                MultiplierControl(
                    multiplier = measure.multiplier,
                    onDecrease = onDecreaseMultiplier,
                    onIncrease = onIncreaseMultiplier,
                )
                PatternActionsMenu(
                    canMoveUp = canMoveUp,
                    canMoveDown = canMoveDown,
                    onDuplicate = onDuplicate,
                    onClear = onClear,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                )
            }
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
private fun PatternActionsMenu(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onDuplicate: () -> Unit,
    onClear: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(40.dp),
        ) {
            Text(
                text = "⋮",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Duplicate") },
                onClick = {
                    expanded = false
                    onDuplicate()
                },
            )
            DropdownMenuItem(
                text = { Text("Clear") },
                onClick = {
                    expanded = false
                    onClear()
                },
            )
            DropdownMenuItem(
                text = { Text("Move up") },
                enabled = canMoveUp,
                onClick = {
                    expanded = false
                    onMoveUp()
                },
            )
            DropdownMenuItem(
                text = { Text("Move down") },
                enabled = canMoveDown,
                onClick = {
                    expanded = false
                    onMoveDown()
                },
            )
        }
    }
}

@Composable
private fun MultiplierControl(
    multiplier: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TextButton(
            onClick = onDecrease,
            enabled = multiplier > MeasurePatternConstraints.MIN_MULTIPLIER,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(40.dp),
        ) {
            Text("−")
        }
        Text(
            text = "×$multiplier",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(40.dp),
        )
        TextButton(
            onClick = onIncrease,
            enabled = multiplier < MeasurePatternConstraints.MAX_MULTIPLIER,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(40.dp),
        ) {
            Text("+")
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
                        multiplier = 2,
                    ),
                    EditorRhythmGrid.buildMeasure(
                        id = 2,
                        editedMeasureIndex = 1,
                        subdivision = MeasureSubdivision.SIXTEENTH,
                    ),
                ).withExpandedMeasureRanges(),
            ),
            onNavigateBack = {},
            onExerciseNameChange = {},
            onTempoBpmChange = {},
            onAddMeasure = {},
            onDeleteMeasure = {},
            onDuplicateMeasure = {},
            onClearMeasure = {},
            onMoveMeasureUp = {},
            onMoveMeasureDown = {},
            onDecreaseMeasureMultiplier = {},
            onIncreaseMeasureMultiplier = {},
            onMeasureSubdivisionChange = { _, _ -> },
            onToggleMeasureNote = { _, _ -> },
            onSave = {},
            onPlay = {},
        )
    }
}

private val ExerciseEditorUiState.patternSummary: String
    get() {
        val patternLabel = if (patternCount == 1) "pattern" else "patterns"
        val measureLabel = if (totalExpandedMeasureCount == 1) "measure" else "measures"
        return "$patternCount $patternLabel · " +
            "$totalExpandedMeasureCount $measureLabel"
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
