package com.titaniumharmonics.bad.ui.processing

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.titaniumharmonics.bad.audio.result.PracticeVerdict
import com.titaniumharmonics.bad.ui.theme.BADTheme
import org.junit.Rule
import org.junit.Test

class ProcessingScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingUsesAnimatedDotsWithoutGenericProgressOrStatistics() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            BADTheme {
                ProcessingScreen(
                    ProcessingPresentation(ProcessingStage.WEIGHING),
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("YOU HAVE BEEN WEIGHED").assertExists()
        composeRule.onNodeWithText(".").assertExists()
        composeRule.mainClock.advanceTimeBy(DOT_INTERVAL_MILLIS)
        composeRule.onNodeWithText("..").assertExists()
        listOf("Progress", "%", "Accuracy", "Hit rate").forEach {
            composeRule.onNodeWithText(it, substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun finalVerdictReplacesLoadingText() {
        composeRule.setContent {
            BADTheme {
                ProcessingScreen(
                    ProcessingPresentation(ProcessingStage.VERDICT, PracticeVerdict.ON_TIME),
                    onRetry = {},
                )
            }
        }
        composeRule.onNodeWithText("AND YOU HAVE BEEN FOUND...").assertExists()
        composeRule.onNodeWithText("ON TIME").assertExists()
        composeRule.onNodeWithText("YOU HAVE BEEN MEASURED", substring = true)
            .assertDoesNotExist()
    }
}
