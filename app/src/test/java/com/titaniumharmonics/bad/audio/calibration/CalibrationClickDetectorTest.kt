package com.titaniumharmonics.bad.audio.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationClickDetectorTest {
    @Test
    fun detectsExactScaledPositiveNegativeAndZeroOffsets() {
        listOf(0, 73, -61).forEach { offset ->
            val config = detectorConfig()
            val (pcm, expected) = recordedClicks(48_000, config, offset, amplitude = 0.42)
            val matches = CalibrationClickDetector(config).detect(pcm, 48_000, expected)
            assertEquals(config.clickCount, matches.size)
            matches.forEach { assertEquals(offset.toLong(), it.offsetSamples) }
        }
    }

    @Test
    fun detectsMultipleClicksAt48And441KHzWithBackgroundNoise() {
        listOf(48_000, 44_100).forEach { rate ->
            val config = detectorConfig()
            val (pcm, expected) = recordedClicks(rate, config, 40, noiseAmplitude = 350)
            val matches = CalibrationClickDetector(config).detect(pcm, rate, expected)
            assertEquals(3, matches.size)
            assertTrue(matches.all { it.correlation >= config.minimumCorrelation })
        }
    }

    @Test
    fun handlesOneMissingClickAndClippedClicks() {
        val config = detectorConfig(clickCount = 4, minimumMatchedClicks = 3)
        val (missing, expected) = recordedClicks(48_000, config, 25, missingIndex = 2)
        assertEquals(3, CalibrationClickDetector(config).detect(missing, 48_000, expected).size)
        val (clipped, clippedExpected) = recordedClicks(48_000, config, 25, clipped = true)
        assertEquals(4, CalibrationClickDetector(config).detect(clipped, 48_000, clippedExpected).size)
    }

    @Test
    fun detectedSequenceRemainsStrictlyOrdered() {
        val config = detectorConfig()
        val (pcm, expected) = recordedClicks(48_000, config, 30)
        val matches = CalibrationClickDetector(config).detect(pcm, 48_000, expected)
        assertEquals(3, matches.size)
        assertTrue(matches.zipWithNext().all { it.second.detectedSample > it.first.detectedSample })
    }

    @Test
    fun noClicksAndLowConfidenceProduceNoMatches() {
        val config = detectorConfig()
        val expected = config.expectedClickSamples(48_000, 0L)
        assertTrue(CalibrationClickDetector(config).detect(ShortArray(config.sequenceSampleCount(48_000)), 48_000, expected).isEmpty())
    }
}
