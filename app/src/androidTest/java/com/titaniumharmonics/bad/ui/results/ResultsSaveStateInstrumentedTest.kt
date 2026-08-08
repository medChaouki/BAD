package com.titaniumharmonics.bad.ui.results

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.titaniumharmonics.bad.history.ExerciseRunPersistenceError
import com.titaniumharmonics.bad.history.ExerciseRunSaveState
import com.titaniumharmonics.bad.history.persistence.androidExerciseRunFixture
import com.titaniumharmonics.bad.ui.theme.BADTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ResultsSaveStateInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun failedSaveKeepsResultsVisibleAndOffersRetry() {
        val run = androidExerciseRunFixture("ui-save", "exercise", 2_000L)
        var retried = false

        composeRule.setContent {
            BADTheme {
                ResultsScreen(
                    result = run.practiceResult,
                    graphModel = run.productionGraph,
                    showDetails = false,
                    onOpenDetails = {},
                    onBack = {},
                    onRetry = {},
                    onReturnToPractice = {},
                    onReturnToLibrary = {},
                    saveState = ExerciseRunSaveState.SaveFailed(
                        run.runId,
                        ExerciseRunPersistenceError.SaveFailure(run.runId),
                    ),
                    onRetrySave = { retried = true },
                )
            }
        }

        composeRule.onNodeWithText(run.exerciseNameSnapshot).assertExists()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        composeRule.onNodeWithText("Save failed").assertExists()
        composeRule.onNodeWithText("Retry save").performClick()
        composeRule.runOnIdle { assertTrue(retried) }
    }

    @Test
    fun deletedSourceExerciseDisablesRetryButLeavesHistoricalResultVisible() {
        val run = androidExerciseRunFixture("ui-history", "deleted", 3_000L)

        composeRule.setContent {
            BADTheme {
                ResultsScreen(
                    result = run.practiceResult,
                    graphModel = run.productionGraph,
                    showDetails = false,
                    onOpenDetails = {},
                    onBack = {},
                    onRetry = {},
                    retryEnabled = false,
                    onReturnToPractice = {},
                    onReturnToLibrary = {},
                    saveState = ExerciseRunSaveState.Saved(run.runId),
                )
            }
        }

        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(5)
        composeRule.onNodeWithText("Retry").assertIsNotEnabled()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(7)
        composeRule.onNodeWithText("Source exercise is no longer available.")
            .assertExists()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        composeRule.onNodeWithText("Saved").assertExists()
    }
}
