package com.titaniumharmonics.bad.ui.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.titaniumharmonics.bad.history.ExerciseRunSummary
import com.titaniumharmonics.bad.ui.theme.BADTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExerciseHistoryScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingEmptyAndErrorStatesAreSafeAndRetryable() {
        var state by mutableStateOf<ExerciseHistoryUiState>(ExerciseHistoryUiState.Loading)
        var retryRequested = false
        composeRule.setContent {
            BADTheme {
                ExerciseHistoryScreen(
                    uiState = state,
                    onOpenRun = {},
                    onSelectBpm = {},
                    onSelectSort = {},
                    onRequestDeletion = {},
                    onCancelDeletion = {},
                    onConfirmDeletion = {},
                    onRetryLoad = { retryRequested = true },
                    onNavigateBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Exercise history").assertExists()
        composeRule.runOnIdle {
            state = ExerciseHistoryUiState.Empty("exercise", "Rudiments")
        }
        composeRule.onNodeWithText("No runs yet.").assertExists()
        composeRule.onNodeWithText("Complete this exercise to start building history.")
            .assertExists()
        composeRule.runOnIdle {
            state = ExerciseHistoryUiState.Error("exercise", "Rudiments", "History unavailable")
        }
        composeRule.onNodeWithText("History unavailable").assertExists()
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.runOnIdle { assertTrue(retryRequested) }
    }

    @Test
    fun readyStateUsesLazyRowsAndEmitsOpenFilterSortAndDeleteActions() {
        val summaries = (0 until 120).map { index -> summary(index) }
        var state by mutableStateOf(ready(summaries))
        var openedRunId: String? = null
        var selectedBpm: Double? = null
        var selectedSort: ExerciseHistorySortMode? = null
        var deleteConfirmed = false
        composeRule.setContent {
            BADTheme {
                ExerciseHistoryScreen(
                    uiState = state,
                    onOpenRun = { openedRunId = it },
                    onSelectBpm = { selectedBpm = it },
                    onSelectSort = { selectedSort = it },
                    onRequestDeletion = { runId ->
                        val pending = summaries.first { it.runId == runId }
                        state = (state as ExerciseHistoryUiState.Ready).copy(
                            runPendingDeletion = pending,
                        )
                    },
                    onCancelDeletion = {
                        state = (state as ExerciseHistoryUiState.Ready).copy(
                            runPendingDeletion = null,
                        )
                    },
                    onConfirmDeletion = { deleteConfirmed = true },
                    onRetryLoad = {},
                    onNavigateBack = {},
                )
            }
        }

        composeRule.onNodeWithText("120 saved runs").assertExists()
        composeRule.onNodeWithTag("history-bpm-filter").performClick()
        composeRule.onNodeWithTag("history-bpm-${120.0.toBits()}").performClick()
        composeRule.runOnIdle { assertEquals(120.0, selectedBpm ?: 0.0, 0.0) }
        composeRule.onNodeWithTag("history-sort").performClick()
        composeRule.onNodeWithTag("history-sort-BEST_ACCURACY").performClick()
        composeRule.runOnIdle {
            assertEquals(ExerciseHistorySortMode.BEST_ACCURACY, selectedSort)
        }

        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(119)
        composeRule.onNodeWithTag("history-run-run-119").performClick()
        composeRule.runOnIdle { assertEquals("run-119", openedRunId) }

        composeRule.onNodeWithTag("history-delete-run-119").performClick()
        composeRule.onNodeWithText("Delete this run?").assertExists()
        composeRule.onNodeWithText("This removes the saved result permanently.").assertExists()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle { assertFalse(deleteConfirmed) }

        composeRule.onNodeWithTag("history-delete-run-119").performClick()
        composeRule.onNodeWithTag("history-delete-confirm").performClick()
        composeRule.runOnIdle { assertTrue(deleteConfirmed) }
    }

    private fun ready(summaries: List<ExerciseRunSummary>) = ExerciseHistoryUiState.Ready(
        exerciseId = "exercise",
        exerciseName = "Rudiments",
        totalRunCount = summaries.size,
        runs = summaries,
        availableBpms = listOf(80.0, 120.0),
        selectedBpm = null,
        sortMode = ExerciseHistorySortMode.NEWEST_FIRST,
    )

    private fun summary(index: Int) = ExerciseRunSummary(
        runId = "run-$index",
        exerciseId = "exercise",
        exerciseNameSnapshot = "Rudiments",
        startedAtEpochMillis = index.toLong() + 1L,
        completedAtEpochMillis = index.toLong() + 2L,
        bpm = if (index % 2 == 0) 120.0 else 80.0,
        exerciseDurationMillis = 4_000.0,
        expandedMeasureCount = 2,
        totalExpectedNotes = 8,
        accuracy = 0.87,
        hitRate = 0.94,
        meanAbsoluteTimingErrorMillis = 24.0,
        signedMeanTimingErrorMillis = -12.0,
        missedCount = 1,
        extraCount = 0,
        schemaVersion = 1,
        appVersion = "test",
    )
}
