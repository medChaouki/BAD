package com.titaniumharmonics.bad.audio.metronome

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowedMetronomeToneGeneratorTest {
    @Test
    fun defaultsProduceCorrectDurationFrequencyAndSmoothHannEdgesAtBothRates() {
        listOf(48_000, 44_100).forEach { sampleRate ->
            val tone = WindowedMetronomeToneGenerator.generate(
                MetronomeConfiguration.DEFAULT.tone,
                accent = false,
                sampleRateHz = sampleRate,
            )

            assertEquals(sampleRate / 100, tone.size)
            assertEquals(0, tone.first().toInt())
            assertEquals(0, tone.last().toInt())
            assertTrue(abs(estimatedFrequencyHz(tone, sampleRate) - 6_000.0) < 120.0)
            assertTrue(tone.all { it.toInt() in Short.MIN_VALUE..Short.MAX_VALUE })
        }
    }

    @Test
    fun accentChangesAmplitudeOnlyAndMaximumVolumeDoesNotClip() {
        val normal = WindowedMetronomeToneGenerator.generate(
            MetronomeConfiguration.DEFAULT.tone,
            accent = false,
            sampleRateHz = 48_000,
        )
        val accent = WindowedMetronomeToneGenerator.generate(
            MetronomeConfiguration.DEFAULT.tone,
            accent = true,
            sampleRateHz = 48_000,
        )
        normal.indices.filter { abs(normal[it].toInt()) > 100 }.forEach { index ->
            assertTrue(abs(accent[index] * 55 - normal[index] * 85) < 100)
        }
        val maximum = WindowedMetronomeToneGenerator.generate(
            MetronomeConfiguration.DEFAULT.tone.copy(
                normalVolumePercent = 100,
                accentVolumePercent = 100,
            ),
            accent = true,
            sampleRateHz = 48_000,
        )
        assertTrue(maximum.maxOf { abs(it.toInt()) } <= Short.MAX_VALUE)
    }

    @Test
    fun configuredDurationIsUsedAndInvalidNyquistFrequencyIsRejected() {
        val tone = MetronomeConfiguration.DEFAULT.tone.copy(
            frequencyHz = 5_000,
            durationMillis = 17,
        )
        val generated = WindowedMetronomeToneGenerator.generate(tone, false, 48_000)
        assertEquals(
            816,
            generated.size,
        )
        assertTrue(abs(estimatedFrequencyHz(generated, 48_000) - 5_000.0) < 120.0)
        assertThrows(IllegalArgumentException::class.java) {
            WindowedMetronomeToneGenerator.generate(
                tone.copy(frequencyHz = 9_000),
                false,
                16_000,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MetronomeToneConfiguration(durationMillis = 0)
        }
    }

    @Test
    fun testSequenceContainsFourEvenlySpacedBeatsAcrossTwoSeconds() {
        listOf(48_000, 44_100).forEach { sampleRate ->
            val sequence = WindowedMetronomeToneGenerator.generateSequence(
                configuration = MetronomeConfiguration.DEFAULT.tone,
                accent = false,
                sampleRateHz = sampleRate,
                beatCount = 4,
                beatIntervalMillis = 500,
                totalDurationMillis = 2_000,
            )
            val toneLength = sampleRate / 100
            val interval = sampleRate / 2

            assertEquals(sampleRate * 2, sequence.size)
            repeat(4) { beatIndex ->
                val beatStart = beatIndex * interval
                assertTrue(
                    sequence.copyOfRange(beatStart, beatStart + toneLength)
                        .any { it != 0.toShort() },
                )
                assertTrue(
                    sequence.copyOfRange(beatStart + toneLength, beatStart + interval)
                        .all { it == 0.toShort() },
                )
            }
        }
    }

    private fun estimatedFrequencyHz(samples: ShortArray, sampleRateHz: Int): Double {
        var positiveCrossings = 0
        for (index in 1 until samples.size) {
            if (samples[index - 1] <= 0 && samples[index] > 0) positiveCrossings += 1
        }
        return positiveCrossings * sampleRateHz.toDouble() / samples.size
    }
}
