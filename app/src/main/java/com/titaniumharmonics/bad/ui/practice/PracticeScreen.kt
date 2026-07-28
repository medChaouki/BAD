package com.titaniumharmonics.bad.ui.practice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.titaniumharmonics.bad.exercise.Exercise
import com.titaniumharmonics.bad.exercise.ExerciseFormat
import com.titaniumharmonics.bad.exercise.ExercisePlaybackSettings
import com.titaniumharmonics.bad.exercise.ExpectedNote
import com.titaniumharmonics.bad.exercise.TimeSignature
import com.titaniumharmonics.bad.timing.ExerciseTiming
import com.titaniumharmonics.bad.ui.theme.BADTheme
import kotlin.math.roundToInt

@Composable
fun PracticeRoute(
    viewModel: PracticeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

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
        onLoad = viewModel::loadExercise,
        onUnload = viewModel::unloadExercise,
        onStart = viewModel::startPlayback,
        onStop = viewModel::stopPlayback,
        onDecreaseTempo = viewModel::decreaseTempo,
        onIncreaseTempo = viewModel::increaseTempo,
        onCountInEnabledChange = viewModel::setCountInEnabled,
        onDecreaseMeasureCount = viewModel::decreaseMeasureCount,
        onIncreaseMeasureCount = viewModel::increaseMeasureCount,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    uiState: PracticeUiState,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDecreaseTempo: () -> Unit,
    onIncreaseTempo: () -> Unit,
    onCountInEnabledChange: (Boolean) -> Unit,
    onDecreaseMeasureCount: () -> Unit,
    onIncreaseMeasureCount: () -> Unit,
) {
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

            val exercise = uiState.playbackExercise
            if (exercise == null) {
                EmptyExerciseCard(
                    uiState = uiState,
                    onLoad = onLoad,
                )
            } else {
                ExerciseCard(exercise)
                uiState.playbackSettings?.let { playbackSettings ->
                    PlaybackSettingsCard(
                        settings = playbackSettings,
                        enabled = uiState.phase !in setOf(
                            PracticePhase.PREPARING,
                            PracticePhase.COUNTING_IN,
                            PracticePhase.RUNNING,
                        ),
                        onDecreaseTempo = onDecreaseTempo,
                        onIncreaseTempo = onIncreaseTempo,
                        onCountInEnabledChange = onCountInEnabledChange,
                        onDecreaseMeasureCount = onDecreaseMeasureCount,
                        onIncreaseMeasureCount = onIncreaseMeasureCount,
                    )
                }
                ExerciseTimeline(
                    exercise = exercise,
                    exerciseElapsedNanos = uiState.exerciseElapsedNanos,
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
private fun PlaybackSettingsCard(
    settings: ExercisePlaybackSettings,
    enabled: Boolean,
    onDecreaseTempo: () -> Unit,
    onIncreaseTempo: () -> Unit,
    onCountInEnabledChange: (Boolean) -> Unit,
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
            StepperSetting(
                label = "Measures",
                value = settings.measureCount.toString(),
                decreaseEnabled = enabled &&
                    settings.measureCount > ExercisePlaybackSettings.MIN_MEASURE_COUNT,
                increaseEnabled = enabled &&
                    settings.measureCount < ExercisePlaybackSettings.MAX_MEASURE_COUNT,
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
private fun EmptyExerciseCard(
    uiState: PracticeUiState,
    onLoad: () -> Unit,
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
                enabled = uiState.phase != PracticePhase.LOADING,
            ) {
                Text(if (uiState.phase == PracticePhase.ERROR) "Try again" else "Load exercise")
            }
        }
    }
}

@Composable
private fun ExerciseCard(exercise: Exercise) {
    Card(
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
    exercise: Exercise,
    exerciseElapsedNanos: Long,
    modifier: Modifier = Modifier,
) {
    val timing = remember(exercise) { ExerciseTiming(exercise) }
    val density = LocalDensity.current
    val pixelsPerSecond = with(density) { 170.dp.toPx() }
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val measureColor = MaterialTheme.colorScheme.outline
    val judgementColor = MaterialTheme.colorScheme.error
    val noteColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.tertiary
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
        ) {
            val laneY = size.height * 0.55f
            val judgementX = size.width * 0.28f
            drawLine(
                color = lineColor,
                start = Offset(0f, laneY),
                end = Offset(size.width, laneY),
                strokeWidth = 3f,
                cap = StrokeCap.Round,
            )

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

            exercise.notes.forEach { note ->
                val x = timelineX(
                    eventTimeNanos = timing.ticksToNanos(note.positionTicks),
                    exerciseElapsedNanos = exerciseElapsedNanos,
                    judgementX = judgementX,
                    pixelsPerSecond = pixelsPerSecond,
                )
                if (x in -24f..size.width + 24f) {
                    drawCircle(
                        color = if (note.accent) accentColor else noteColor,
                        radius = if (note.accent) 15f else 11f,
                        center = Offset(x, laneY),
                    )
                }
            }

            drawLine(
                color = judgementColor,
                start = Offset(judgementX, 0f),
                end = Offset(judgementX, size.height),
                strokeWidth = 6f,
                cap = StrokeCap.Round,
            )
        }
    }
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
    val statusText = when (uiState.phase) {
        PracticePhase.READY -> "Ready for inspection."
        PracticePhase.PREPARING -> "Calibrating confidence…"
        PracticePhase.COUNTING_IN -> "Count-in: ${uiState.countInBeatsRemaining}"
        PracticePhase.RUNNING -> "Inspection in progress."
        PracticePhase.COMPLETED -> "Inspection complete."
        PracticePhase.ERROR -> "Rhythm subsystem objected."
        PracticePhase.UNLOADED -> "No exercise loaded."
        PracticePhase.LOADING -> "Loading exercise…"
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = statusText,
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
    val previewExercise = Exercise(
        formatVersion = ExerciseFormat.CURRENT_VERSION,
        id = "preview",
        name = "Quarter Note Inspection",
        description = "Four measures of quarter notes.",
        tempoBpm = 100.0,
        timeSignature = TimeSignature(4, 4),
        countInMeasures = 1,
        measureCount = 4,
        ticksPerQuarterNote = 480,
        notes = (0 until 16).map { noteIndex ->
            ExpectedNote(
                positionTicks = noteIndex * 480L,
                accent = noteIndex % 4 == 0,
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
            onLoad = {},
            onUnload = {},
            onStart = {},
            onStop = {},
            onDecreaseTempo = {},
            onIncreaseTempo = {},
            onCountInEnabledChange = {},
            onDecreaseMeasureCount = {},
            onIncreaseMeasureCount = {},
        )
    }
}
