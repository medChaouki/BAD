package com.titaniumharmonics.bad.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.titaniumharmonics.bad.audio.calibration.TimingCalibration
import com.titaniumharmonics.bad.audio.detection.MetronomeRejectionConfiguration
import com.titaniumharmonics.bad.audio.detection.UncertainCandidateBehaviour
import com.titaniumharmonics.bad.audio.matching.JudgementConfiguration
import com.titaniumharmonics.bad.audio.metronome.MetronomeNotchConfiguration
import com.titaniumharmonics.bad.audio.metronome.MetronomeToneConfiguration
import kotlin.math.roundToInt

@Composable
fun SettingsRoute(
    activeTimingCalibration: TimingCalibration?,
    onOpenTimingCalibration: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: MetronomeSettingsViewModel = viewModel(),
    detectionViewModel: HitDetectionSettingsViewModel = viewModel(),
    judgementViewModel: JudgementSettingsViewModel = viewModel(),
) {
    val metronomeState by viewModel.uiState.collectAsStateWithLifecycle()
    val detectionState by detectionViewModel.uiState.collectAsStateWithLifecycle()
    val judgementState by judgementViewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        onDispose(viewModel::stopTestTone)
    }
    SettingsScreen(
        activeTimingCalibration = activeTimingCalibration,
        metronomeState = metronomeState,
        detectionState = detectionState,
        detectionActions = detectionViewModel,
        judgementState = judgementState,
        judgementActions = judgementViewModel,
        onOpenTimingCalibration = onOpenTimingCalibration,
        onNavigateBack = onNavigateBack,
        onToneFrequencyChange = viewModel::setToneFrequency,
        onToneDurationChange = viewModel::setToneDuration,
        onNormalVolumeChange = viewModel::setNormalVolume,
        onAccentVolumeChange = viewModel::setAccentVolume,
        onNotchEnabledChange = viewModel::setNotchEnabled,
        onNotchCenterChange = viewModel::setNotchCenter,
        onNotchQChange = viewModel::setNotchQ,
        onRelinkNotch = viewModel::relinkNotchCenter,
        onTestNormal = { viewModel.playTestTone(accent = false) },
        onTestAccent = { viewModel.playTestTone(accent = true) },
        onResetMetronome = viewModel::reset,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    activeTimingCalibration: TimingCalibration?,
    metronomeState: MetronomeSettingsUiState,
    detectionState: HitDetectionSettingsUiState,
    detectionActions: HitDetectionSettingsActions,
    judgementState: JudgementSettingsUiState,
    judgementActions: JudgementSettingsActions,
    onOpenTimingCalibration: () -> Unit,
    onNavigateBack: () -> Unit,
    onToneFrequencyChange: (Int) -> Unit,
    onToneDurationChange: (Int) -> Unit,
    onNormalVolumeChange: (Int) -> Unit,
    onAccentVolumeChange: (Int) -> Unit,
    onNotchEnabledChange: (Boolean) -> Unit,
    onNotchCenterChange: (Int) -> Unit,
    onNotchQChange: (Double) -> Unit,
    onRelinkNotch: () -> Unit,
    onTestNormal: () -> Unit,
    onTestAccent: () -> Unit,
    onResetMetronome: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("Back") }
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
            TimingCalibrationSettingsCard(activeTimingCalibration, onOpenTimingCalibration)
            MetronomeSettingsCard(
                state = metronomeState,
                onToneFrequencyChange = onToneFrequencyChange,
                onToneDurationChange = onToneDurationChange,
                onNormalVolumeChange = onNormalVolumeChange,
                onAccentVolumeChange = onAccentVolumeChange,
                onNotchEnabledChange = onNotchEnabledChange,
                onNotchCenterChange = onNotchCenterChange,
                onNotchQChange = onNotchQChange,
                onRelinkNotch = onRelinkNotch,
                onTestNormal = onTestNormal,
                onTestAccent = onTestAccent,
                onReset = onResetMetronome,
            )
            HitDetectionSettingsCard(detectionState, detectionActions)
            JudgementSettingsCard(judgementState, judgementActions)
        }
    }
}

@Composable
private fun JudgementSettingsCard(
    state: JudgementSettingsUiState,
    actions: JudgementSettingsActions,
) {
    val configuration = state.configuration
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Timing judgement",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Negative timing errors are early; positive timing errors are late.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DecimalStepper(
                "On-Time Before",
                configuration.onTimeBeforeMillis,
                5.0,
                0.0,
                configuration.maximumEarlyMillis,
                actions::setOnTimeBefore,
                "ms",
            )
            DecimalStepper(
                "On-Time After",
                configuration.onTimeAfterMillis,
                5.0,
                0.0,
                configuration.maximumLateMillis,
                actions::setOnTimeAfter,
                "ms",
            )
            DecimalStepper(
                "Maximum Early",
                configuration.maximumEarlyMillis,
                5.0,
                maxOf(
                    configuration.onTimeBeforeMillis,
                    JudgementConfiguration.MINIMUM_MAXIMUM_WINDOW_MILLIS,
                ),
                JudgementConfiguration.MAXIMUM_WINDOW_MILLIS,
                actions::setMaximumEarly,
                "ms",
            )
            DecimalStepper(
                "Maximum Late",
                configuration.maximumLateMillis,
                5.0,
                maxOf(
                    configuration.onTimeAfterMillis,
                    JudgementConfiguration.MINIMUM_MAXIMUM_WINDOW_MILLIS,
                ),
                JudgementConfiguration.MAXIMUM_WINDOW_MILLIS,
                actions::setMaximumLate,
                "ms",
            )
            DecimalStepper(
                "Minimum detected-hit confidence",
                configuration.minimumDetectedHitConfidence,
                0.05,
                0.0,
                1.0,
                actions::setMinimumConfidence,
            )
            LabelledSwitch(
                "Extra-hit handling enabled",
                configuration.extraHitHandlingEnabled,
                actions::setExtraHitHandlingEnabled,
            )
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = actions::reset, modifier = Modifier.fillMaxWidth()) {
                Text("Reset judgement settings")
            }
        }
    }
}

@Composable
private fun HitDetectionSettingsCard(
    state: HitDetectionSettingsUiState,
    actions: HitDetectionSettingsActions,
) {
    val configuration = state.configuration
    val rejection = configuration.metronomeRejection
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Drum-hit detection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            LabelledSwitch("Detection enabled", configuration.enabled, actions::setEnabled)
            DecimalStepper(
                "Minimum absolute threshold",
                configuration.minimumAbsoluteThreshold,
                0.005,
                0.0,
                1.0,
                actions::setMinimumAbsoluteThreshold,
            )
            DecimalStepper(
                "Noise-floor multiplier",
                configuration.noiseFloorMultiplier,
                0.25,
                1.0,
                12.0,
                actions::setNoiseFloorMultiplier,
                "×",
            )
            DecimalStepper(
                "Minimum signal-to-noise",
                configuration.minimumSignalToNoiseRatio,
                0.25,
                1.0,
                20.0,
                actions::setMinimumSignalToNoise,
                "×",
            )
            DecimalStepper(
                "Minimum attack rise",
                configuration.minimumAttackRise,
                0.005,
                0.0,
                1.0,
                actions::setMinimumAttackRise,
            )
            DecimalStepper(
                "Onset look-back",
                configuration.onsetLookBackMillis,
                1.0,
                0.0,
                50.0,
                actions::setOnsetLookBack,
                "ms",
            )
            DecimalStepper(
                "Peak search",
                configuration.peakSearchMillis,
                1.0,
                1.0,
                100.0,
                actions::setPeakSearch,
                "ms",
            )
            DecimalStepper(
                "Release hysteresis",
                configuration.releaseHysteresisRatio,
                0.05,
                0.1,
                0.95,
                actions::setReleaseRatio,
            )
            DecimalStepper(
                "Minimum hit spacing",
                configuration.minimumHitSpacingMillis,
                5.0,
                5.0,
                200.0,
                actions::setMinimumHitSpacing,
                "ms",
            )
            DecimalStepper(
                "Minimum confidence",
                configuration.minimumConfidence,
                0.05,
                0.0,
                1.0,
                actions::setMinimumConfidence,
            )
            LabelledSwitch(
                "Apply timing calibration",
                configuration.applyTimingCalibration,
                actions::setApplyCalibration,
            )

            Text("FFT metronome rejection", fontWeight = FontWeight.Bold)
            LabelledSwitch("Metronome rejection enabled", rejection.enabled, actions::setRejectionEnabled)
            val fftSizes = MetronomeRejectionConfiguration.SUPPORTED_FFT_SIZES.sorted()
            val fftIndex = fftSizes.indexOf(rejection.fftSize)
            IntegerStepper(
                label = "FFT size",
                value = rejection.fftSize,
                unit = "samples",
                step = 1,
                range = 0..(fftSizes.lastIndex),
                displayValue = rejection.fftSize.toString(),
                decreaseValue = fftSizes[(fftIndex - 1).coerceAtLeast(0)],
                increaseValue = fftSizes[(fftIndex + 1).coerceAtMost(fftSizes.lastIndex)],
                decreaseEnabled = fftIndex > 0,
                increaseEnabled = fftIndex < fftSizes.lastIndex,
                onChange = actions::setFftSize,
            )
            DecimalStepper(
                "FFT analysis window",
                rejection.analysisWindowMillis,
                1.0,
                5.0,
                40.0,
                actions::setFftWindow,
                "ms",
            )
            DecimalStepper(
                "Metronome-band width",
                rejection.metronomeBandWidthHz,
                100.0,
                100.0,
                3_000.0,
                actions::setBandWidth,
                "Hz",
            )
            DecimalStepper(
                "Minimum metronome-band ratio",
                rejection.minimumMetronomeBandEnergyRatio,
                0.05,
                0.0,
                1.0,
                actions::setMinimumBandRatio,
            )
            DecimalStepper(
                "Minimum broadband residual",
                rejection.minimumBroadbandResidualEnergy,
                0.005,
                0.0,
                1.0,
                actions::setMinimumBroadbandEnergy,
            )
            DecimalStepper(
                "Spectral confidence threshold",
                rejection.spectralConfidenceThreshold,
                0.05,
                0.0,
                1.0,
                actions::setSpectralConfidence,
            )
            DecimalStepper(
                "Maximum scheduled distance",
                rejection.maximumScheduledDistanceMillis,
                5.0,
                0.0,
                100.0,
                actions::setMaximumScheduledDistance,
                "ms",
            )
            LabelledSwitch(
                "Retain uncertain candidates as drum",
                rejection.uncertainCandidateBehaviour ==
                    UncertainCandidateBehaviour.RETAIN_AS_DRUM,
            ) { retain ->
                actions.setUncertainBehaviour(
                    if (retain) UncertainCandidateBehaviour.RETAIN_AS_DRUM
                    else UncertainCandidateBehaviour.REJECT_AS_METRONOME,
                )
            }
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = actions::reset, modifier = Modifier.fillMaxWidth()) {
                Text("Reset hit-detection settings")
            }
        }
    }
}

@Composable
private fun LabelledSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TimingCalibrationSettingsCard(
    calibration: TimingCalibration?,
    onOpen: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Timing calibration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(calibration.statusText())
            Text("Calibrate the phone speaker and built-in microphone timing.")
            OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                Text(if (calibration == null) "Calibrate" else "Calibrate again")
            }
        }
    }
}

@Composable
private fun MetronomeSettingsCard(
    state: MetronomeSettingsUiState,
    onToneFrequencyChange: (Int) -> Unit,
    onToneDurationChange: (Int) -> Unit,
    onNormalVolumeChange: (Int) -> Unit,
    onAccentVolumeChange: (Int) -> Unit,
    onNotchEnabledChange: (Boolean) -> Unit,
    onNotchCenterChange: (Int) -> Unit,
    onNotchQChange: (Double) -> Unit,
    onRelinkNotch: () -> Unit,
    onTestNormal: () -> Unit,
    onTestAccent: () -> Unit,
    onReset: () -> Unit,
) {
    val configuration = state.configuration
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Metronome",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text("Windowed tone")
            IntegerStepper(
                label = "Tone frequency",
                value = configuration.tone.frequencyHz,
                unit = "Hz",
                step = 100,
                range = MetronomeToneConfiguration.MIN_FREQUENCY_HZ..
                    MetronomeToneConfiguration.MAX_FREQUENCY_HZ,
                onChange = onToneFrequencyChange,
            )
            IntegerStepper(
                label = "Tone duration",
                value = configuration.tone.durationMillis,
                unit = "ms",
                step = 1,
                range = MetronomeToneConfiguration.MIN_DURATION_MILLIS..
                    MetronomeToneConfiguration.MAX_DURATION_MILLIS,
                onChange = onToneDurationChange,
            )
            IntegerStepper(
                label = "Normal volume",
                value = configuration.tone.normalVolumePercent,
                unit = "%",
                step = 5,
                range = MetronomeToneConfiguration.MIN_VOLUME_PERCENT..
                    MetronomeToneConfiguration.MAX_VOLUME_PERCENT,
                onChange = onNormalVolumeChange,
            )
            IntegerStepper(
                label = "Accent volume",
                value = configuration.tone.accentVolumePercent,
                unit = "%",
                step = 5,
                range = MetronomeToneConfiguration.MIN_VOLUME_PERCENT..
                    MetronomeToneConfiguration.MAX_VOLUME_PERCENT,
                onChange = onAccentVolumeChange,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onTestNormal,
                    enabled = state.isValid && !state.testTonePlaying,
                    modifier = Modifier.weight(1f),
                ) { Text("Test normal") }
                OutlinedButton(
                    onClick = onTestAccent,
                    enabled = state.isValid && !state.testTonePlaying,
                    modifier = Modifier.weight(1f),
                ) { Text("Test accent") }
            }

            Text("Rejection filter", fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Notch filter enabled", modifier = Modifier.weight(1f))
                Switch(
                    checked = configuration.notch.enabled,
                    onCheckedChange = onNotchEnabledChange,
                )
            }
            IntegerStepper(
                label = "Notch center",
                value = configuration.notch.centerFrequencyHz,
                unit = "Hz",
                step = 100,
                range = MetronomeNotchConfiguration.MIN_CENTER_FREQUENCY_HZ..
                    MetronomeNotchConfiguration.MAX_CENTER_FREQUENCY_HZ,
                onChange = onNotchCenterChange,
            )
            Text(
                if (configuration.notch.centerLinkedToTone) {
                    "Linked to tone frequency"
                } else {
                    "Custom override"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!configuration.notch.centerLinkedToTone) {
                OutlinedButton(onClick = onRelinkNotch, modifier = Modifier.fillMaxWidth()) {
                    Text("Relink to tone frequency")
                }
            }
            DecimalStepper(
                label = "Notch Q",
                value = configuration.notch.qFactor,
                step = 1.0,
                minimum = MetronomeNotchConfiguration.MIN_Q_FACTOR,
                maximum = MetronomeNotchConfiguration.MAX_Q_FACTOR,
                onChange = onNotchQChange,
            )
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                Text("Reset metronome settings")
            }
        }
    }
}

@Composable
private fun IntegerStepper(
    label: String,
    value: Int,
    unit: String,
    step: Int,
    range: IntRange,
    displayValue: String = value.toString(),
    decreaseValue: Int = value - step,
    increaseValue: Int = value + step,
    decreaseEnabled: Boolean = value > range.first,
    increaseEnabled: Boolean = value < range.last,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange(decreaseValue) }, enabled = decreaseEnabled) {
            Text("−")
        }
        Text("$displayValue $unit")
        OutlinedButton(onClick = { onChange(increaseValue) }, enabled = increaseEnabled) {
            Text("+")
        }
    }
}

@Composable
private fun DecimalStepper(
    label: String,
    value: Double,
    step: Double,
    minimum: Double,
    maximum: Double,
    onChange: (Double) -> Unit,
    unit: String = "",
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange(value - step) }, enabled = value > minimum) {
            Text("−")
        }
        Text("$value $unit".trim())
        OutlinedButton(onClick = { onChange(value + step) }, enabled = value < maximum) {
            Text("+")
        }
    }
}

private fun TimingCalibration?.statusText(): String = if (this == null) {
    "Status: Not calibrated"
} else {
    val offset = offsetMillis.roundToInt()
    val formattedOffset = if (offset >= 0) "+$offset" else offset.toString()
    val formattedConfidence = confidence.name.lowercase().replaceFirstChar(Char::uppercase)
    "Status: Calibrated · $formattedOffset ms · $formattedConfidence confidence"
}
