package com.titaniumharmonics.bad.timing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionElapsedClockTest {
    private var nowNanos = 0L
    private val sessionClock = SessionElapsedClock(
        clock = MonotonicClock { nowNanos },
    )

    @Test
    fun elapsedNanos_excludesTimeSpentPaused() {
        sessionClock.start(startedNanos = 100L)
        nowNanos = 600L

        assertEquals(500L, sessionClock.pause())
        assertTrue(sessionClock.isPaused)

        nowNanos = 1_600L
        assertEquals(500L, sessionClock.elapsedNanos())

        assertEquals(500L, sessionClock.resume())
        assertFalse(sessionClock.isPaused)

        nowNanos = 2_100L
        assertEquals(1_000L, sessionClock.elapsedNanos())
    }

    @Test
    fun reset_removesPreviousSessionTiming() {
        sessionClock.start(startedNanos = 100L)
        nowNanos = 200L
        sessionClock.pause()

        sessionClock.reset()

        assertEquals(null, sessionClock.elapsedNanos())
        assertFalse(sessionClock.isPaused)
    }
}
