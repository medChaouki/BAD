package com.titaniumharmonics.bad.ui.results

import org.junit.Assert.assertEquals
import org.junit.Test

class ResultFormattingTest {
    @Test
    fun percentagesAndNullableTimingAreSafe() {
        assertEquals("82%", formatPercent(0.82))
        assertEquals("0%", formatPercent(-1.0))
        assertEquals("100%", formatPercent(2.0))
        assertEquals("—", formatOptionalMillis(null))
        assertEquals("31.0 ms", formatOptionalMillis(31.0))
    }

    @Test
    fun signedBiasDistinguishesEarlyLateAndNeutral() {
        assertEquals("12.0 ms early", formatBias(-12.0))
        assertEquals("12.0 ms late", formatBias(12.0))
        assertEquals("Neutral", formatBias(0.0))
        assertEquals("—", formatBias(null))
    }
}
