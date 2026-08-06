package com.titaniumharmonics.bad.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.titaniumharmonics.bad.ui.theme.BADTheme
import com.titaniumharmonics.bad.audio.detection.UncertainCandidateBehaviour
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun metronomeControlsAreDirectlyPresentAndResetIsActionable() {
        var resetClicked = false
        TestDetectionActions.resetClicked = false
        TestJudgementActions.resetClicked = false
        composeRule.setContent {
            BADTheme {
                SettingsScreen(
                    activeTimingCalibration = null,
                    metronomeState = MetronomeSettingsUiState(),
                    detectionState = HitDetectionSettingsUiState(),
                    detectionActions = TestDetectionActions,
                    judgementState = JudgementSettingsUiState(),
                    judgementActions = TestJudgementActions,
                    onOpenTimingCalibration = {},
                    onNavigateBack = {},
                    onToneFrequencyChange = {},
                    onToneDurationChange = {},
                    onNormalVolumeChange = {},
                    onAccentVolumeChange = {},
                    onNotchEnabledChange = {},
                    onNotchCenterChange = {},
                    onNotchQChange = {},
                    onRelinkNotch = {},
                    onTestNormal = {},
                    onTestAccent = {},
                    onResetMetronome = { resetClicked = true },
                )
            }
        }

        listOf(
            "Metronome",
            "Tone frequency",
            "Tone duration",
            "Normal volume",
            "Accent volume",
            "Test normal",
            "Test accent",
            "Notch filter enabled",
            "Notch center",
            "Notch Q",
            "Drum-hit detection",
            "Detection enabled",
            "Minimum absolute threshold",
            "Noise-floor multiplier",
            "Minimum signal-to-noise",
            "Minimum attack rise",
            "Onset look-back",
            "Peak search",
            "Release hysteresis",
            "Minimum hit spacing",
            "Minimum confidence",
            "Apply timing calibration",
            "FFT metronome rejection",
            "Metronome rejection enabled",
            "FFT size",
            "FFT analysis window",
            "Metronome-band width",
            "Minimum metronome-band ratio",
            "Minimum broadband residual",
            "Spectral confidence threshold",
            "Maximum scheduled distance",
            "Retain uncertain candidates as drum",
            "Timing judgement",
            "On-Time Before",
            "On-Time After",
            "Maximum Early",
            "Maximum Late",
        ).forEach { label ->
            composeRule.onNodeWithText(label).assertExists()
        }
        composeRule.onNodeWithText("Reset metronome settings")
            .performScrollTo()
            .performClick()
        assertTrue(resetClicked)
        composeRule.onNodeWithText("Reset hit-detection settings")
            .performScrollTo()
            .performClick()
        assertTrue(TestDetectionActions.resetClicked)
        composeRule.onNodeWithText("Reset judgement settings")
            .performScrollTo()
            .performClick()
        assertTrue(TestJudgementActions.resetClicked)
    }
}

private object TestJudgementActions : JudgementSettingsActions {
    var resetClicked = false
    override fun setOnTimeBefore(value: Double) = Unit
    override fun setOnTimeAfter(value: Double) = Unit
    override fun setMaximumEarly(value: Double) = Unit
    override fun setMaximumLate(value: Double) = Unit
    override fun reset() {
        resetClicked = true
    }
}

private object TestDetectionActions : HitDetectionSettingsActions {
    var resetClicked = false
    override fun setEnabled(value: Boolean) = Unit
    override fun setMinimumAbsoluteThreshold(value: Double) = Unit
    override fun setNoiseFloorMultiplier(value: Double) = Unit
    override fun setMinimumSignalToNoise(value: Double) = Unit
    override fun setMinimumAttackRise(value: Double) = Unit
    override fun setOnsetLookBack(value: Double) = Unit
    override fun setPeakSearch(value: Double) = Unit
    override fun setReleaseRatio(value: Double) = Unit
    override fun setMinimumHitSpacing(value: Double) = Unit
    override fun setMinimumConfidence(value: Double) = Unit
    override fun setApplyCalibration(value: Boolean) = Unit
    override fun setRejectionEnabled(value: Boolean) = Unit
    override fun setFftSize(value: Int) = Unit
    override fun setFftWindow(value: Double) = Unit
    override fun setBandWidth(value: Double) = Unit
    override fun setMinimumBandRatio(value: Double) = Unit
    override fun setMinimumBroadbandEnergy(value: Double) = Unit
    override fun setSpectralConfidence(value: Double) = Unit
    override fun setMaximumScheduledDistance(value: Double) = Unit
    override fun setUncertainBehaviour(value: UncertainCandidateBehaviour) = Unit
    override fun reset() {
        resetClicked = true
    }
}
