package com.titaniumharmonics.bad.audio.calibration

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationClickSequenceTest {
    @Test
    fun defaultSequenceIsVersionedEightClicksAtFixedSpacing() {
        val config = TimingCalibrationConfig()
        val sequence = CalibrationClickSequenceGenerator.generate(48_000, config)
        assertEquals(8, sequence.clickPositions.size)
        assertEquals(48_000L * 750 / 1_000, sequence.clickPositions.first())
        for (index in 1 until sequence.clickPositions.size) {
            assertEquals(
                24_000L,
                sequence.clickPositions[index] - sequence.clickPositions[index - 1],
            )
        }
        assertEquals(5 * 48_000, sequence.samples.size)
        assertEquals(TimingCalibrationConfig.CURRENT_ALGORITHM_VERSION, config.algorithmVersion)
        assertEquals(2, config.algorithmVersion)
    }

    @Test
    fun sequenceIsDeterministicAt48And441KHz() {
        listOf(48_000, 44_100).forEach { rate ->
            val first = CalibrationClickSequenceGenerator.generate(rate)
            val second = CalibrationClickSequenceGenerator.generate(rate)
            assertArrayEquals(first.samples, second.samples)
            assertArrayEquals(first.clickPositions, second.clickPositions)
        }
    }
}
