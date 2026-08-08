package com.titaniumharmonics.bad.ui.history

import com.titaniumharmonics.bad.history.ExerciseRunSummary
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseHistoryPresentationTest {
    @Test
    fun bpmOptionsAreGeneratedFromDataAndFilteringRestoresTheFullList() {
        val runs = listOf(
            summary("a", completed = 1L, bpm = 100.0),
            summary("b", completed = 2L, bpm = 80.0),
            summary("c", completed = 3L, bpm = 100.0),
        )

        assertEquals(listOf(80.0, 100.0), availableHistoryBpms(runs))
        assertEquals(
            listOf("c", "a"),
            presentHistoryRuns(runs, 100.0, ExerciseHistorySortMode.NEWEST_FIRST)
                .map(ExerciseRunSummary::runId),
        )
        assertTrue(
            presentHistoryRuns(runs, 120.0, ExerciseHistorySortMode.NEWEST_FIRST).isEmpty(),
        )
        assertEquals(
            listOf("c", "b", "a"),
            presentHistoryRuns(runs, null, ExerciseHistorySortMode.NEWEST_FIRST)
                .map(ExerciseRunSummary::runId),
        )
    }

    @Test
    fun everySortModeUsesDeterministicTieBreaking() {
        val runs = listOf(
            summary("a", completed = 10L, accuracy = 0.9, timingError = 12.0),
            summary("b", completed = 20L, accuracy = 0.9, timingError = 12.0),
            summary("c", completed = 30L, accuracy = 0.5, timingError = null),
        )

        assertOrder(runs, ExerciseHistorySortMode.NEWEST_FIRST, "c", "b", "a")
        assertOrder(runs, ExerciseHistorySortMode.OLDEST_FIRST, "a", "b", "c")
        assertOrder(runs, ExerciseHistorySortMode.BEST_ACCURACY, "b", "a", "c")
        assertOrder(runs, ExerciseHistorySortMode.LOWEST_TIMING_ERROR, "b", "a", "c")
    }

    @Test
    fun formattingHandlesDateMetricsBiasAndUnavailableTimingSafely() {
        assertEquals(
            "8 Aug 2026 · 18:42",
            formatHistoryTimestamp(
                epochMillis = 1_786_214_520_000L,
                zoneId = ZoneId.of("UTC"),
                locale = Locale.ENGLISH,
            ),
        )
        assertEquals("80 BPM", formatHistoryBpm(80.0))
        assertEquals("87%", formatHistoryPercentage(0.87))
        assertEquals("24 ms", formatHistoryTimingError(24.2))
        assertEquals("—", formatHistoryTimingError(null))
        assertEquals("12 ms early", formatHistoryBias(-12.0))
        assertEquals("8 ms late", formatHistoryBias(8.0))
        assertEquals("Balanced", formatHistoryBias(0.2))
    }

    private fun assertOrder(
        runs: List<ExerciseRunSummary>,
        mode: ExerciseHistorySortMode,
        vararg expected: String,
    ) {
        assertEquals(
            expected.toList(),
            presentHistoryRuns(runs, null, mode).map(ExerciseRunSummary::runId),
        )
    }

    private fun summary(
        runId: String,
        completed: Long,
        bpm: Double = 80.0,
        accuracy: Double = 0.8,
        timingError: Double? = 20.0,
    ) = ExerciseRunSummary(
        runId = runId,
        exerciseId = "exercise",
        exerciseNameSnapshot = "Exercise",
        startedAtEpochMillis = completed.coerceAtLeast(1L) - 1L,
        completedAtEpochMillis = completed,
        bpm = bpm,
        exerciseDurationMillis = 1_000.0,
        expandedMeasureCount = 1,
        totalExpectedNotes = 4,
        accuracy = accuracy,
        hitRate = 0.9,
        meanAbsoluteTimingErrorMillis = timingError,
        signedMeanTimingErrorMillis = -4.0,
        missedCount = 1,
        extraCount = 0,
        schemaVersion = 1,
        appVersion = "test",
    )
}
