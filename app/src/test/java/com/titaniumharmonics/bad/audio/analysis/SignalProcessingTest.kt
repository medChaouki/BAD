package com.titaniumharmonics.bad.audio.analysis

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class SignalProcessingTest {
    @Test
    fun invalidConfigurationReturnsDomainFailure() {
        assertThrows(AudioAnalysisException.InvalidConfiguration::class.java) {
            AudioAnalysisConfig(frameDurationMillis = 0.0)
        }
        assertThrows(AudioAnalysisException.InvalidConfiguration::class.java) {
            AudioAnalysisConfig(sampleCountToleranceFrames = -1L)
        }
    }

    @Test
    fun normalizationHandlesEndpointsAndStaysFiniteAndBounded() {
        val normalized = Pcm16Normalizer.normalize(
            shortArrayOf(Short.MIN_VALUE, -1, 0, 1, Short.MAX_VALUE),
        )

        assertEquals(-1.0f, normalized[0], 0.0f)
        assertEquals(0.0f, normalized[2], 0.0f)
        assertEquals(1.0f, normalized[4], 0.0f)
        assertTrue(normalized.all { it.isFinite() && it in -1.0f..1.0f })
        assertTrue(abs(normalized[1]) < abs(normalized[0]))
    }

    @Test
    fun dcRemovalHandlesSilencePositiveNegativeOffsetsAndImpulse() {
        val silence = FloatArray(100)
        MeanDcOffsetRemover.removeInPlace(silence)
        assertTrue(silence.all { abs(it) < 1e-7f })

        listOf(0.4f, -0.4f).forEach { offset ->
            val constant = FloatArray(100) { offset }
            MeanDcOffsetRemover.removeInPlace(constant)
            assertTrue(constant.all { abs(it) < 1e-6f })
        }

        val transient = FloatArray(100) { 0.2f }.also { it[50] = 1.0f }
        MeanDcOffsetRemover.removeInPlace(transient)
        assertTrue(abs(transient.average()) < 1e-6)
        assertTrue(transient[50] > 0.7f)
        assertTrue(transient.all { it.isFinite() })
    }

    @Test
    fun highPassReducesDcAndLowFrequencyButPreservesImpulseAtBothRates() {
        listOf(48_000, 44_100).forEach { sampleRate ->
            val dc = FloatArray(sampleRate / 2) { 0.5f }
            FirstOrderHighPassFilter.filterInPlace(dc, sampleRate, 80.0)
            assertTrue(abs(dc.last()) < 0.001f)

            val lowFrequency = FloatArray(sampleRate) { index ->
                sin(2.0 * PI * 10.0 * index / sampleRate).toFloat()
            }
            val originalRms = rms(lowFrequency)
            FirstOrderHighPassFilter.filterInPlace(lowFrequency, sampleRate, 80.0)
            assertTrue(rms(lowFrequency) < originalRms * 0.25)

            val impulse = FloatArray(1_000).also { it[500] = 1.0f }
            FirstOrderHighPassFilter.filterInPlace(impulse, sampleRate, 80.0)
            assertTrue(abs(impulse[500]) > 0.9f)
            assertTrue(impulse.all(Float::isFinite))
        }
    }

    @Test
    fun disabledHighPassLeavesInputUnchanged() {
        val input = floatArrayOf(-0.2f, 0.0f, 0.4f)

        val output = FirstOrderHighPassFilter.filter(input, 48_000, 80.0, enabled = false)

        assertArrayEquals(input, output, 0.0f)
    }

    @Test
    fun biquadNotchAttenuatesCenterMoreThanOffFrequencyAtBothRates() {
        listOf(48_000, 44_100).forEach { sampleRate ->
            val center = sineWave(6_000.0, sampleRate, sampleRate)
            val offFrequency = sineWave(3_000.0, sampleRate, sampleRate)
            BiquadNotchFilter.filterInPlace(center, sampleRate, 6_000.0, 10.0)
            BiquadNotchFilter.filterInPlace(offFrequency, sampleRate, 6_000.0, 10.0)

            assertTrue(rms(center.copyOfRange(sampleRate / 4, sampleRate)) < 0.03)
            assertTrue(rms(offFrequency.copyOfRange(sampleRate / 4, sampleRate)) > 0.60)
            assertTrue(center.all(Float::isFinite))
            assertTrue(offFrequency.all(Float::isFinite))
        }
    }

    @Test
    fun notchQControlsWidthDisabledPathIsExactAndImpulseRemainsVisible() {
        val signal = sineWave(5_500.0, 48_000, 48_000)
        val wide = BiquadNotchFilter.filter(signal, 48_000, 6_000.0, 3.0, true)
        val narrow = BiquadNotchFilter.filter(signal, 48_000, 6_000.0, 25.0, true)
        assertTrue(rms(wide.copyOfRange(12_000, 48_000)) < rms(narrow.copyOfRange(12_000, 48_000)))
        assertArrayEquals(
            signal,
            BiquadNotchFilter.filter(signal, 48_000, 6_000.0, 10.0, false),
            0.0f,
        )

        val transientPlusTone = sineWave(6_000.0, 48_000, 2_000).also { it[1_000] += 1.0f }
        BiquadNotchFilter.filterInPlace(transientPlusTone, 48_000, 6_000.0, 10.0)
        assertTrue(abs(transientPlusTone[1_000]) > 0.75f)
    }

    @Test
    fun notchRejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException::class.java) {
            BiquadNotchFilter.filterInPlace(FloatArray(10), 48_000, 24_000.0, 10.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BiquadNotchFilter.filterInPlace(FloatArray(10), 48_000, 6_000.0, Double.NaN)
        }
    }

    @Test
    fun frameMetricsUseCenterAndIncludeUnpaddedPartialFinalFrame() {
        val raw = shortArrayOf(0, 1_000, 2_000, 3_000, 4_000, 5_000, 6_000)
        val filtered = floatArrayOf(0.0f, 0.5f, -1.0f, 0.25f, 0.0f, 0.1f, -0.3f)

        val metrics = AnalysisFrameCalculator.calculate(raw, filtered, 4, 3)

        assertArrayEquals(longArrayOf(1L, 4L, 6L), metrics.centerSamples)
        assertEquals(Pcm16Normalizer.normalize(raw[1]), metrics.representativeRaw[0], 0.0f)
        assertEquals(0.0f, metrics.representativeFiltered[1], 0.0f)
        assertEquals(1.0f, metrics.peaks[0], 0.0f)
        assertEquals(sqrt((0.25 + 1.0 + 0.0625) / 4.0).toFloat(), metrics.levels[0], 1e-6f)
        assertEquals(0.3f, metrics.peaks[2], 0.0f)
        assertEquals(0.3f, metrics.levels[2], 0.0f)
    }

    @Test
    fun envelopeIsZeroForSilenceAndRespondsToImpulseAmplitude() {
        val silence = TransientEnvelopeCalculator.calculate(
            FloatArray(20),
            FloatArray(20),
            2.0,
            2.0,
            12.0,
        )
        assertTrue(silence.all { abs(it) < 1e-7f })

        val levels = FloatArray(30)
        val peaks = FloatArray(30)
        peaks[5] = 0.25f
        peaks[20] = 1.0f
        val envelope = TransientEnvelopeCalculator.calculate(levels, peaks, 2.0, 2.0, 12.0)
        assertTrue(envelope[20] > envelope[5])
        assertTrue(envelope[5] > envelope[15])
        assertTrue(envelope.all { it.isFinite() && it >= 0.0f })
    }

    @Test
    fun noiseFloorIsStableAndDoesNotImmediatelyFollowTransient() {
        val constant = FloatArray(2_000) { 0.02f }
        val baseline = AdaptiveNoiseFloorEstimator.estimate(constant, 2.0, 1_000.0, 250.0)
        assertEquals(constant.size, baseline.size)
        assertTrue(baseline.last() in 0.019f..0.021f)

        val withTransient = FloatArray(2_000) { 0.02f }.also { it[1_000] = 1.0f }
        val transientBaseline = AdaptiveNoiseFloorEstimator.estimate(
            withTransient,
            2.0,
            1_000.0,
            250.0,
        )
        assertTrue(transientBaseline[1_000] < 0.03f)
        assertTrue(transientBaseline[1_000] < withTransient[1_000])
        assertTrue(transientBaseline.last() < 0.03f)

        val rising = FloatArray(2_000) { it / 2_000.0f * 0.1f }
        val risingBaseline = AdaptiveNoiseFloorEstimator.estimate(rising, 2.0, 1_000.0, 250.0)
        assertTrue(risingBaseline.last() > risingBaseline[500])
    }

    private fun rms(values: FloatArray): Double =
        sqrt(values.sumOf { it.toDouble() * it } / values.size)

    private fun sineWave(frequencyHz: Double, sampleRateHz: Int, sampleCount: Int) =
        FloatArray(sampleCount) { index ->
            sin(2.0 * PI * frequencyHz * index / sampleRateHz).toFloat()
        }
}
