package com.titaniumharmonics.bad.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.titaniumharmonics.bad.ui.theme.BADTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun metronomeControlsAreDirectlyPresentAndResetIsActionable() {
        var resetClicked = false
        composeRule.setContent {
            BADTheme {
                SettingsScreen(
                    activeTimingCalibration = null,
                    metronomeState = MetronomeSettingsUiState(),
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
        ).forEach { label ->
            composeRule.onNodeWithText(label).assertExists()
        }
        composeRule.onNodeWithText("Reset metronome settings")
            .performScrollTo()
            .performClick()
        assertTrue(resetClicked)
    }
}
