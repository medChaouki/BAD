package com.titaniumharmonics.bad.ui.practice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.titaniumharmonics.bad.exercise.ExercisePlaybackSettings
import com.titaniumharmonics.bad.exercise.RuntimeExercise
import com.titaniumharmonics.bad.exercise.RuntimeExpectedNote
import com.titaniumharmonics.bad.exercise.RuntimeMeasure
import com.titaniumharmonics.bad.exercise.TimeSignature
import com.titaniumharmonics.bad.timing.ExerciseTiming
import com.titaniumharmonics.bad.ui.theme.BADTheme
import kotlin.math.roundToInt

@Composable
fun PracticeRoute(
    onCreateExercise: () -> Unit,
    onModifyExercise: () -> Unit,
    onLoadExercise: () -> Unit,
    documentUriToLoad: String?,
    onDocumentLoadConsumed: () -> Unit,
    fileOperationsEnabled: Boolean,
    viewModel: PracticeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(documentUriToLoad) {
        documentUriToLoad?.let { documentUri ->
            viewModel.loadExercise(documentUri)
            onDocumentLoadConsumed()
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.stopPlayback()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    PracticeScreen(
        uiState = uiState,
        onCreateExercise = onCreateExercise,
        onModifyExercise = onModifyExercise,
        onLoad = onLoadExercise,
        fileOperationsEnabled = fileOperationsEnabled,
        onUnload = viewModel::unloadExercise,
        onStart = viewModel::startPlayback,
        onStop = viewModel::stopPlayback,
        onPause = viewModel::pausePlayback,
        onResume = viewModel::resumePlayback,
        onRepeat = viewModel::restartPlayback,
        onDecreaseTempo = viewModel::decreaseTempo,
        onIncreaseTempo = viewModel::increaseTempo,
        onCountInEnabledChange = viewModel::setCountInEnabled,
        onDownbeatsOnlyChange = viewModel::setDownbeatsOnly,
        onDecreaseMeasureCount = viewModel::decreaseMeasureCount,
        onIncreaseMeasureCount = viewModel::increaseMeasureCount,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    uiState: PracticeUiState,
    onCreateExercise: () -> Unit,
    onModifyExercise: () -> Unit,
    onLoad: () -> Unit,
    fileOperationsEnabled: Boolean,
    onUnload: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRepeat: () -> Unit,
    onDecreaseTempo: () -> Unit,
    onIncreaseTempo: () -> Unit,
    onCountInEnabledChange: (Boolean) -> Unit,
    onDownbeatsOnlyChange: (Boolean) -> Unit,
    onDecreaseMeasureCount: () -> Unit,
    onIncreaseMeasureCount: () -> Unit,
) {
    val playbackExercise = uiState.playbackExercise
    var playbackSettingsExpanded by rememberSaveable(uiState.exercise?.id) {
        mutableStateOf(false)
    }
    if (playbackExercise != null && uiState.phase.isPlayerVisible()) {
        FullScreenPracticePlayer(
            exercise = playbackExercise,
            uiState = uiState,
            onPause = onPause,
            onResume = onResume,
            onRepeat = onRepeat,
            onStop = onStop,
        )
        return
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "B.A.D.",
                        fontWeight = FontWeight.Black,
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Beat Accuracy Detector",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "With B.A.D., the beat approves you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            EditorSection(
                onCreateExercise = onCreateExercise,
                onModifyExercise = onModifyExercise,
                fileOperationsEnabled = fileOperationsEnabled,
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Practice",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Load an exercise and run a beat inspection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val exercise = playbackExercise
            if (exercise == null) {
                EmptyExerciseCard(
                    uiState = uiState,
                    onLoad = onLoad,
                    fileOperationsEnabled = fileOperationsEnabled,
                )
            } else {
                ExerciseCard(
                    exercise = exercise,
                    playbackSettingsExpanded = playbackSettingsExpanded,
                    onTogglePlaybackSettings = {
                        playbackSettingsExpanded = !playbackSettingsExpanded
                    },
                )
                AnimatedVisibility(visible = playbackSettingsExpanded) {
                    uiState.playbackSettings?.let { playbackSettings ->
                        PlaybackSettingsCard(
                            settings = playbackSettings,
                            enabled = uiState.phase !in setOf(
                                PracticePhase.PREPARING,
                                PracticePhase.COUNTING_IN,
                                PracticePhase.RUNNING,
                                PracticePhase.PAUSED,
                                PracticePhase.RESUME_COUNT_IN,
                            ),
                            onDecreaseTempo = onDecreaseTempo,
                            onIncreaseTempo = onIncreaseTempo,
                            onCountInEnabledChange = onCountInEnabledChange,
                            onDownbeatsOnlyChange = onDownbeatsOnlyChange,
                            onDecreaseMeasureCount = onDecreaseMeasureCount,
                            onIncreaseMeasureCount = onIncreaseMeasureCount,
                        )
                    }
                }
                ExerciseTimeline(
                    exercise = exercise,
                    exerciseElapsedNanos = uiState.exerciseElapsedNanos,
                    previewFirstMeasure = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                )
                SessionStatus(uiState)
                SessionControls(
                    phase = uiState.phase,
                    onStart = onStart,
                    onStop = onStop,
                    onUnload = onUnload,
                )
            }
        }
    }
}

@Composable
private fun FullScreenPracticePlayer(
    exercise: RuntimeExercise,
    uiState: PracticeUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRepeat: () -> Unit,
    onStop: () -> Unit,
) {
    val timing = remember(exercise) { ExerciseTiming(exercise) }
    val timelineElapsedNanos = when (uiState.phase) {
        PracticePhase.PREPARING -> {
            if (exercise.countInMeasures > 0) {
                -timing.quarterNoteDurationNanos
            } else {
                0L
            }
        }
        PracticePhase.COUNTING_IN -> {
            uiState.exerciseElapsedNanos.coerceAtLeast(
                -timing.quarterNoteDurationNanos,
            )
        }
        else -> uiState.exerciseElapsedNanos
    }
    val playerTextColor = if (
        MaterialTheme.colorScheme.background.luminance() < 0.5f
    ) {
        Color.White
    } else {
        Color.Black
    }
    val measureProgressText = if (
        uiState.exerciseElapsedNanos >= 0L &&
        uiState.phase in setOf(
            PracticePhase.RUNNING,
            PracticePhase.PAUSED,
            PracticePhase.RESUME_COUNT_IN,
            PracticePhase.COMPLETED,
        )
    ) {
        "Measure ${timing.measureNumberAt(uiState.exerciseElapsedNanos)} " +
            "of ${exercise.measureCount}"
    } else {
        null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ExerciseTimeline(
            exercise = exercise,
            exerciseElapsedNanos = timelineElapsedNanos,
            modifier = Modifier.fillMaxSize(),
            fullScreen = true,
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = sessionStatusText(uiState),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = playerTextColor,
            )
            measureProgressText?.let { progressText ->
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = playerTextColor,
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = if (uiState.phase == PracticePhase.PAUSED) {
                    onResume
                } else {
                    onPause
                },
                enabled = uiState.phase in setOf(
                    PracticePhase.COUNTING_IN,
                    PracticePhase.RUNNING,
                    PracticePhase.PAUSED,
                ),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    when (uiState.phase) {
                        PracticePhase.PAUSED -> "Resume"
                        PracticePhase.RESUME_COUNT_IN -> "Count-in"
                        else -> "Pause"
                    },
                    color = playerTextColor,
                )
            }
            OutlinedButton(
                onClick = onRepeat,
                enabled = uiState.phase != PracticePhase.PREPARING,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "Repeat",
                    color = playerTextColor,
                )
            }
            OutlinedButton(
                onClick = onStop,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "Stop",
                    color = playerTextColor,
                )
            }
        }
    }
}

@Composable
private fun PlaybackSettingsCard(
    settings: ExercisePlaybackSettings,
    enabled: Boolean,
    onDecreaseTempo: () -> Unit,
    onIncreaseTempo: () -> Unit,
    onCountInEnabledChange: (Boolean) -> Unit,
    onDownbeatsOnlyChange: (Boolean) -> Unit,
    onDecreaseMeasureCount: () -> Unit,
    onIncreaseMeasureCount: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Playback settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            StepperSetting(
                label = "Tempo",
                value = "${settings.tempoBpm} BPM",
                decreaseEnabled = enabled &&
                    settings.tempoBpm > ExercisePlaybackSettings.MIN_TEMPO_BPM,
                increaseEnabled = enabled &&
                    settings.tempoBpm < ExercisePlaybackSettings.MAX_TEMPO_BPM,
                onDecrease = onDecreaseTempo,
                onIncrease = onIncreaseTempo,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Count-in",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = if (settings.countInEnabled) "Enabled" else "Disabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.countInEnabled,
                    onCheckedChange = onCountInEnabledChange,
                    enabled = enabled,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "First note only",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = if (settings.downbeatsOnly) "Enabled" else "Disabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.downbeatsOnly,
                    onCheckedChange = onDownbeatsOnlyChange,
                    enabled = enabled,
                )
            }
            StepperSetting(
                label = "Measures",
                value = settings.measureCount.toString(),
                decreaseEnabled = enabled &&
                    settings.measureCount > ExercisePlaybackSettings.MIN_MEASURE_COUNT,
                increaseEnabled = enabled &&
                    settings.measureCount < settings.maximumMeasureCount,
                onDecrease = onDecreaseMeasureCount,
                onIncrease = onIncreaseMeasureCount,
            )
        }
    }
}

@Composable
private fun StepperSetting(
    label: String,
    value: String,
    decreaseEnabled: Boolean,
    increaseEnabled: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(
            onClick = onDecrease,
            enabled = decreaseEnabled,
        ) {
            Text("−")
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .width(88.dp)
                .padding(horizontal = 8.dp),
        )
        OutlinedButton(
            onClick = onIncrease,
            enabled = increaseEnabled,
        ) {
            Text("+")
        }
    }
}

@Composable
private fun EditorSection(
    onCreateExercise: () -> Unit,
    onModifyExercise: () -> Unit,
    fileOperationsEnabled: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Create or modify exercises",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Build a new exercise or continue editing one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onCreateExercise,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Create")
                }
                OutlinedButton(
                    onClick = onModifyExercise,
                    enabled = fileOperationsEnabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Modify existing")
                }
            }
        }
    }
}

@Composable
private fun EmptyExerciseCard(
    uiState: PracticeUiState,
    onLoad: () -> Unit,
    fileOperationsEnabled: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = when (uiState.phase) {
                    PracticePhase.LOADING -> "Loading exercise…"
                    PracticePhase.ERROR -> "Exercise rejected."
                    else -> "No exercise loaded."
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            uiState.errorMessage?.let { errorMessage ->
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = onLoad,
                enabled = fileOperationsEnabled && uiState.phase != PracticePhase.LOADING,
            ) {
                Text(if (uiState.phase == PracticePhase.ERROR) "Try again" else "Load exercise")
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: RuntimeExercise,
    playbackSettingsExpanded: Boolean,
    onTogglePlaybackSettings: () -> Unit,
) {
    Card(
        onClick = onTogglePlaybackSettings,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = exercise.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                ExerciseFact(
                    label = "TEMPO",
                    value = "${exercise.tempoBpm.roundToInt()} BPM",
                )
                ExerciseFact(
                    label = "METER",
                    value = "${exercise.timeSignature.numerator}/${exercise.timeSignature.denominator}",
                )
                ExerciseFact(
                    label = "MEASURES",
                    value = exercise.measureCount.toString(),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (playbackSettingsExpanded) {
                        "Hide playback settings"
                    } else {
                        "Show playback settings"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (playbackSettingsExpanded) "▲" else "▼",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ExerciseFact(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ExerciseTimeline(
    exercise: RuntimeExercise,
    exerciseElapsedNanos: Long,
    modifier: Modifier = Modifier,
    fullScreen: Boolean = false,
    previewFirstMeasure: Boolean = false,
) {
    val timing = remember(exercise) { ExerciseTiming(exercise) }
    val density = LocalDensity.current
    val pixelsPerSecond = with(density) { 170.dp.toPx() }
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val measureColor = MaterialTheme.colorScheme.outline
    val judgementColor = MaterialTheme.colorScheme.error
    val noteColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.tertiary
    val beatHighlightColor = Color(0xFF43A047)
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow

    Card(
        modifier = modifier,
        shape = if (fullScreen) RoundedCornerShape(0.dp) else MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
        ) {
            val laneY = size.height * 0.55f
            val judgementX = size.width * 0.28f
            val judgementLineHalfHeight =
                ACCENT_NOTE_RADIUS_PX * JUDGEMENT_LINE_DIAMETER_MULTIPLIER
            val previewMeasureStartX =
                size.width * PREVIEW_MEASURE_START_FRACTION
            val previewMeasureEndX =
                size.width * PREVIEW_MEASURE_END_FRACTION
            val previewNoteStartX =
                size.width * PREVIEW_NOTE_START_FRACTION
            val previewNoteEndX =
                size.width * PREVIEW_NOTE_END_FRACTION
            val previewNotes = exercise.notes.takeWhile { note ->
                note.positionTicks < timing.measureDurationTicks
            }
            val firstPreviewNoteTicks = previewNotes.firstOrNull()?.positionTicks
            val lastPreviewNoteTicks = previewNotes.lastOrNull()?.positionTicks
            drawLine(
                color = lineColor,
                start = Offset(0f, laneY),
                end = Offset(size.width, laneY),
                strokeWidth = 3f,
                cap = StrokeCap.Round,
            )

            if (previewFirstMeasure) {
                listOf(previewMeasureStartX, previewMeasureEndX).forEach { x ->
                    drawLine(
                        color = measureColor,
                        start = Offset(x, laneY - 42f),
                        end = Offset(x, laneY + 42f),
                        strokeWidth = 2f,
                    )
                }
            } else {
                val measureDurationNanos =
                    timing.exerciseDurationNanos / exercise.measureCount
                repeat(exercise.measureCount + 1) { measureIndex ->
                    val measureTimeNanos = measureDurationNanos * measureIndex
                    val x = timelineX(
                        eventTimeNanos = measureTimeNanos,
                        exerciseElapsedNanos = exerciseElapsedNanos,
                        judgementX = judgementX,
                        pixelsPerSecond = pixelsPerSecond,
                    )
                    if (x in 0f..size.width) {
                        drawLine(
                            color = measureColor,
                            start = Offset(x, laneY - 42f),
                            end = Offset(x, laneY + 42f),
                            strokeWidth = 2f,
                        )
                    }
                }
            }

            exercise.notes.forEach { note ->
                if (
                    previewFirstMeasure &&
                    note.positionTicks >= timing.measureDurationTicks
                ) {
                    return@forEach
                }
                val x = if (previewFirstMeasure) {
                    previewNoteX(
                        positionTicks = note.positionTicks,
                        firstNoteTicks = firstPreviewNoteTicks ?: note.positionTicks,
                        lastNoteTicks = lastPreviewNoteTicks ?: note.positionTicks,
                        startX = previewNoteStartX,
                        endX = previewNoteEndX,
                    )
                } else {
                    timelineX(
                        eventTimeNanos = timing.ticksToNanos(note.positionTicks),
                        exerciseElapsedNanos = exerciseElapsedNanos,
                        judgementX = judgementX,
                        pixelsPerSecond = pixelsPerSecond,
                    )
                }
                if (x in 0f..size.width) {
                    drawCircle(
                        color = if (note.accent) accentColor else noteColor,
                        radius = if (note.accent) {
                            ACCENT_NOTE_RADIUS_PX
                        } else {
                            NOTE_RADIUS_PX
                        },
                        center = Offset(x, laneY),
                    )
                }
            }

            if (!previewFirstMeasure) {
                drawLine(
                    color = judgementColor,
                    start = Offset(
                        judgementX,
                        (laneY - judgementLineHalfHeight).coerceAtLeast(0f),
                    ),
                    end = Offset(
                        judgementX,
                        (laneY + judgementLineHalfHeight).coerceAtMost(size.height),
                    ),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round,
                )
            }

            if (!previewFirstMeasure) {
                timing.highlightedBeatTimeNanos(exerciseElapsedNanos)?.let { beatTimeNanos ->
                    val highlightX = timelineX(
                        eventTimeNanos = beatTimeNanos,
                        exerciseElapsedNanos = exerciseElapsedNanos,
                        judgementX = judgementX,
                        pixelsPerSecond = pixelsPerSecond,
                    )
                    drawCircle(
                        color = beatHighlightColor,
                        radius = 20f,
                        center = Offset(highlightX, laneY),
                        style = Stroke(width = 4f),
                    )
                }
            }
        }
    }
}

private fun previewNoteX(
    positionTicks: Long,
    firstNoteTicks: Long,
    lastNoteTicks: Long,
    startX: Float,
    endX: Float,
): Float {
    if (firstNoteTicks == lastNoteTicks) return (startX + endX) / 2f
    val relativePosition =
        (positionTicks - firstNoteTicks).toDouble() /
            (lastNoteTicks - firstNoteTicks).toDouble()
    return startX + ((endX - startX) * relativePosition).toFloat()
}

private fun timelineX(
    eventTimeNanos: Long,
    exerciseElapsedNanos: Long,
    judgementX: Float,
    pixelsPerSecond: Float,
): Float {
    val timeUntilEventSeconds =
        (eventTimeNanos.toDouble() - exerciseElapsedNanos.toDouble()) /
            1_000_000_000.0
    return judgementX + (timeUntilEventSeconds * pixelsPerSecond).toFloat()
}

@Composable
private fun SessionStatus(uiState: PracticeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = sessionStatusText(uiState),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        uiState.errorMessage?.let { errorMessage ->
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SessionControls(
    phase: PracticePhase,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onUnload: () -> Unit,
) {
    val playbackActive = phase in setOf(
        PracticePhase.PREPARING,
        PracticePhase.COUNTING_IN,
        PracticePhase.RUNNING,
        PracticePhase.PAUSED,
        PracticePhase.RESUME_COUNT_IN,
    )

    Row(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = if (playbackActive) onStop else onStart,
            enabled = phase !in setOf(PracticePhase.LOADING, PracticePhase.UNLOADED),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                when {
                    playbackActive -> "Stop"
                    phase == PracticePhase.COMPLETED -> "Run again"
                    phase == PracticePhase.ERROR -> "Retry playback"
                    else -> "Run inspection"
                },
            )
        }
        Spacer(Modifier.width(12.dp))
        OutlinedButton(
            onClick = onUnload,
            modifier = Modifier.weight(1f),
        ) {
            Text("Unload")
        }
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun PracticeScreenPreview() {
    val previewExercise = RuntimeExercise(
        id = "preview",
        name = "Quarter Note Inspection",
        description = "Four measures of quarter notes.",
        tempoBpm = 100.0,
        timeSignature = TimeSignature(4, 4),
        countInMeasures = 1,
        ticksPerQuarterNote = 480,
        measures = List(4) { measureIndex ->
            val measureStartTick = measureIndex * 1_920L
            RuntimeMeasure(
                index = measureIndex,
                startTick = measureStartTick,
                durationTicks = 1_920L,
                notes = List(4) { noteIndex ->
                    val localPositionTicks = noteIndex * 480L
                    RuntimeExpectedNote(
                        measureIndex = measureIndex,
                        positionInMeasureTicks = localPositionTicks,
                        positionTicks = measureStartTick + localPositionTicks,
                        accent = noteIndex == 0,
                    )
                },
            )
        },
    )

    BADTheme {
        PracticeScreen(
            uiState = PracticeUiState(
                exercise = previewExercise,
                playbackSettings = ExercisePlaybackSettings.fromExercise(previewExercise),
                phase = PracticePhase.READY,
                exerciseElapsedNanos = -2_400_000_000L,
            ),
            onCreateExercise = {},
            onModifyExercise = {},
            onLoad = {},
            fileOperationsEnabled = true,
            onUnload = {},
            onStart = {},
            onStop = {},
            onPause = {},
            onResume = {},
            onRepeat = {},
            onDecreaseTempo = {},
            onIncreaseTempo = {},
            onCountInEnabledChange = {},
            onDownbeatsOnlyChange = {},
            onDecreaseMeasureCount = {},
            onIncreaseMeasureCount = {},
        )
    }
}

private fun sessionStatusText(uiState: PracticeUiState): String = when (uiState.phase) {
    PracticePhase.READY -> "Ready for inspection."
    PracticePhase.PREPARING -> "Calibrating confidence…"
    PracticePhase.COUNTING_IN -> "Count-in: ${uiState.countInBeatsRemaining}"
    PracticePhase.RUNNING -> "Inspection in progress."
    PracticePhase.PAUSED -> "Inspection paused."
    PracticePhase.RESUME_COUNT_IN -> "Count-in: ${uiState.countInBeatsRemaining}"
    PracticePhase.COMPLETED -> "Inspection complete."
    PracticePhase.ERROR -> "Rhythm subsystem objected."
    PracticePhase.UNLOADED -> "No exercise loaded."
    PracticePhase.LOADING -> "Loading exercise…"
}

private fun PracticePhase.isPlayerVisible(): Boolean = this in setOf(
    PracticePhase.PREPARING,
    PracticePhase.COUNTING_IN,
    PracticePhase.RUNNING,
    PracticePhase.PAUSED,
    PracticePhase.RESUME_COUNT_IN,
    PracticePhase.COMPLETED,
)

private const val NOTE_RADIUS_PX = 11f
private const val ACCENT_NOTE_RADIUS_PX = 15f
private const val JUDGEMENT_LINE_DIAMETER_MULTIPLIER = 10f
private const val PREVIEW_MEASURE_START_FRACTION = 0.08f
private const val PREVIEW_MEASURE_END_FRACTION = 0.92f
private const val PREVIEW_NOTE_START_FRACTION = 0.20f
private const val PREVIEW_NOTE_END_FRACTION = 0.80f
