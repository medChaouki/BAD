package com.titaniumharmonics.bad.ui.processing

import org.junit.Assert.assertEquals
import org.junit.Test

class ProcessingTimingTest {
    @Test
    fun fastProcessingKeepsEachLoadingStageVisibleBriefly() {
        assertEquals(
            MINIMUM_STAGE_VISIBILITY_MILLIS,
            remainingDelayMillis(1_000L, MINIMUM_STAGE_VISIBILITY_MILLIS, 1_000L),
        )
    }

    @Test
    fun slowProcessingIsNeverDelayedAfterTheRealStageCompletes() {
        assertEquals(
            0L,
            remainingDelayMillis(1_000L, MINIMUM_STAGE_VISIBILITY_MILLIS, 4_000L),
        )
    }

    @Test
    fun finalVerdictUsesNineHundredMillisecondWindow() {
        assertEquals(VERDICT_DURATION_MILLIS, remainingDelayMillis(5_000L, 900L, 5_000L))
        assertEquals(250L, remainingDelayMillis(5_000L, 900L, 5_650L))
    }

    @Test
    fun restoredTimestampContinuesInsteadOfRestartingRotationDelay() {
        val savedVerdictShownAt = 8_000L

        assertEquals(300L, remainingDelayMillis(savedVerdictShownAt, 900L, 8_600L))
    }
}
