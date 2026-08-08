package com.titaniumharmonics.bad.ui.calibration

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.titaniumharmonics.bad.BuildConfig
import com.titaniumharmonics.bad.audio.calibration.CalibrationDiagnostics
import com.titaniumharmonics.bad.audio.calibration.CalibrationFailureReason
import com.titaniumharmonics.bad.audio.calibration.CalibrationPhase
import com.titaniumharmonics.bad.audio.calibration.CalibrationRouteStatus
import com.titaniumharmonics.bad.audio.calibration.TimingCalibration
import com.titaniumharmonics.bad.audio.calibration.TimingCalibrationMath
import com.titaniumharmonics.bad.audio.calibration.TimingCalibrationUiState
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun TimingCalibrationRoute(
    onNavigateBack: () -> Unit,
    onCalibrationChanged: (TimingCalibration?) -> Unit,
    viewModel: TimingCalibrationViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.startCalibration(granted) }

    LaunchedEffect(state.activeCalibration) {
        onCalibrationChanged(state.activeCalibration)
    }
    DisposableEffect(Unit) {
        onDispose(viewModel::leaveScreen)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.cancelCalibration()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    TimingCalibrationScreen(
        state = state,
        onCheckAgain = viewModel::checkRoute,
        onConfirmUncertainRoute = viewModel::confirmUncertainRoute,
        onStart = {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) viewModel.startCalibration(true) else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onCancelCalibration = viewModel::cancelCalibration,
        onAcceptCalibration = viewModel::acceptCalibration,
        onRejectCalibration = viewModel::rejectCalibration,
        onReset = viewModel::resetCalibration,
        onPlayDebugRecording = viewModel::playDebugRecording,
        onStopDebugRecording = viewModel::stopDebugPlayback,
        onNavigateBack = onNavigateBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimingCalibrationScreen(
    state: TimingCalibrationUiState,
    onCheckAgain: () -> Unit,
    onConfirmUncertainRoute: (Boolean) -> Unit,
    onStart: () -> Unit,
    onCancelCalibration: () -> Unit,
    onAcceptCalibration: () -> Unit,
    onRejectCalibration: () -> Unit,
    onReset: () -> Unit,
    onPlayDebugRecording: () -> Unit,
    onStopDebugRecording: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    var showLeaveConfirmation by remember { mutableStateOf(false) }
    val view = LocalView.current
    DisposableEffect(state.isActive) {
        val previous = view.keepScreenOn
        if (state.isActive) view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
    BackHandler {
        if (state.isActive) showLeaveConfirmation = true else onNavigateBack()
    }
    if (showLeaveConfirmation) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirmation = false },
            title = { Text("Cancel calibration?") },
            text = { Text("The active calibration recording will be discarded.") },
            confirmButton = {
                Button(onClick = {
                    showLeaveConfirmation = false
                    onCancelCalibration()
                    onNavigateBack()
                }) { Text("Cancel and leave") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLeaveConfirmation = false }) { Text("Stay") }
            },
        )
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Timing calibration") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Timing calibration", fontWeight = FontWeight.Bold)
                    Text("Disconnect all headphones and audio devices.")
                    Text("Use the phone speaker and built-in microphone.")
                    Text("Place the phone on a stable surface in a quiet room.")
                    Text("Do not tap or play during calibration.")
                }
            }

            CalibrationStatus(state)
            state.routeDecision?.let { route ->
                Text(
                    route.message,
                    color = if (route.status == CalibrationRouteStatus.BLOCKED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (route.status == CalibrationRouteStatus.UNCERTAIN) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(
                            checked = state.uncertainRouteConfirmed,
                            onCheckedChange = onConfirmUncertainRoute,
                            enabled = !state.isActive,
                        )
                        Text("I confirm that all external audio devices are disconnected.")
                    }
                }
            }

            if (state.isActive) {
                Text("Progress: ${state.phase.progressLabel()}", fontWeight = FontWeight.Bold)
                Button(onClick = onCancelCalibration, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel calibration")
                }
            } else if (state.isAwaitingDecision) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onRejectCalibration,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Reject")
                    }
                    Button(
                        onClick = onAcceptCalibration,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Accept")
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onCheckAgain, modifier = Modifier.weight(1f)) {
                        Text("Check again")
                    }
                    Button(
                        onClick = onStart,
                        enabled = state.canStart,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (state.activeCalibration == null) "Start calibration" else "Calibrate again")
                    }
                }
                if (state.isFinished) {
                    Button(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
                        Text("Return Home")
                    }
                } else {
                    OutlinedButton(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel")
                    }
                }
                if (state.activeCalibration != null) {
                    OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                        Text("Reset calibration")
                    }
                }
            }

            if (BuildConfig.DEBUG) {
                state.diagnostics?.let { diagnostics ->
                    CalibrationDebugDiagnostics(
                        diagnostics,
                        state.failureReason,
                        onPlayDebugRecording,
                        onStopDebugRecording,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalibrationStatus(state: TimingCalibrationUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val pendingCalibration = state.pendingCalibration
            val calibration = state.activeCalibration
            if (pendingCalibration != null) {
                Text("Calibration complete", fontWeight = FontWeight.Bold)
                Text("Measured offset: ${pendingCalibration.offsetMillis.signedMilliseconds()}")
            } else {
                Text(
                    if (calibration == null) "Status: Not calibrated" else "Status: Calibrated",
                    fontWeight = FontWeight.Bold,
                )
                calibration?.let {
                    Text("Offset: ${it.offsetMillis.signedMilliseconds()}")
                    Text("Confidence: ${it.confidence.name.lowercase().replaceFirstChar(Char::uppercase)}")
                    Text("Calibrated: ${DateFormat.getDateInstance().format(Date(it.calibratedAtEpochMillis))}")
                } ?: Text("Calibration improves timing accuracy.")
                state.failureReason?.let {
                    Text(it.userMessage, color = MaterialTheme.colorScheme.error)
                    if (calibration != null) Text("The previous calibration is still active.")
                }
            }
        }
    }
}

@Composable
private fun CalibrationDebugDiagnostics(
    diagnostics: CalibrationDiagnostics,
    failureReason: CalibrationFailureReason?,
    onPlay: () -> Unit,
    onStop: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Debug: Calibration diagnostics", fontWeight = FontWeight.Bold)
            failureReason?.let { Text("Failure: ${it.name}") }
            val medianCorrelation = diagnostics.matches
                .map { (it.correlation * 1_000).toLong() }
                .takeIf(List<Long>::isNotEmpty)
                ?.let(TimingCalibrationMath::median)
                ?.div(1_000.0)
            Text(
                "Expected: ${diagnostics.expectedClickSamples.size} · " +
                    "Detected: ${diagnostics.matches.size}\n" +
                    "Median: ${diagnostics.medianOffsetSamples ?: "—"} samples · " +
                    "Spread: ${diagnostics.offsetSpreadSamples ?: "—"} samples\n" +
                    "Median correlation: ${medianCorrelation ?: "—"}",
            )
            CalibrationWaveformGraph(diagnostics)
            diagnostics.matches.forEachIndexed { index, match ->
                Text(
                    "Click ${index + 1}: ${match.offsetSamples} samples · " +
                        "correlation ${"%.3f".format(match.correlation)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPlay, modifier = Modifier.weight(1f)) {
                    Text("Play WAV")
                }
                OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
private fun CalibrationWaveformGraph(diagnostics: CalibrationDiagnostics) {
    val waveformColor = MaterialTheme.colorScheme.primary
    val expectedColor = MaterialTheme.colorScheme.tertiary
    val detectedColor = MaterialTheme.colorScheme.error
    Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        val centerY = size.height / 2f
        val path = Path()
        diagnostics.waveform.forEachIndexed { index, point ->
            val x = point.sampleFrame.toFloat() / diagnostics.totalSampleFrames * size.width
            val y = centerY - point.normalizedAmplitude * centerY
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, waveformColor, style = Stroke(1.dp.toPx()))
        diagnostics.expectedClickSamples.forEach { sample ->
            val x = sample.toFloat() / diagnostics.totalSampleFrames * size.width
            drawLine(expectedColor, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
        }
        diagnostics.matches.forEach { match ->
            val x = match.detectedSample.toFloat() / diagnostics.totalSampleFrames * size.width
            drawLine(detectedColor, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
        }
    }
    Text("Waveform · expected thin markers · detected thick markers", style = MaterialTheme.typography.bodySmall)
}

private fun CalibrationPhase.progressLabel(): String = when (this) {
    CalibrationPhase.CHECKING_ROUTE -> "Checking audio route"
    CalibrationPhase.PREPARING -> "Preparing"
    CalibrationPhase.RECORDING -> "Recording"
    CalibrationPhase.PLAYING_CLICKS -> "Playing clicks"
    CalibrationPhase.PROCESSING -> "Processing"
    else -> name.lowercase().replaceFirstChar(Char::uppercase)
}

private fun Double.signedMilliseconds(): String =
    "${if (this >= 0.0) "+" else ""}${roundToInt()} ms"
