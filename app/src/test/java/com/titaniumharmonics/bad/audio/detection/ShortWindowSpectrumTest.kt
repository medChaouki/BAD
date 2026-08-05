package com.titaniumharmonics.bad.audio.detection

import com.titaniumharmonics.bad.audio.analysis.ImmutableFloatSeries
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortWindowSpectrumTest {
    @Test
    fun configuredToneIsNarrowWhileImpulseAndWrongFrequencyAreBroadOrOffBand() {
        listOf(48_000, 44_100).forEach { sampleRate ->
            listOf(512, 1_024, 2_048).forEach { fftSize ->
                val config = MetronomeRejectionConfiguration(fftSize = fftSize)
                val tone = signal(sampleRate) { index ->
                    (0.6 * sin(2.0 * PI * 6_000.0 * index / sampleRate)).toFloat()
                }
                val wrongTone = signal(sampleRate) { index ->
                    (0.6 * sin(2.0 * PI * 3_500.0 * index / sampleRate)).toFloat()
                }
                val impulse = FloatArray(sampleRate).also {
                    val center = sampleRate / 2
                    repeat(40) { offset ->
                        it[center + offset] = if (offset % 2 == 0) 0.8f else -0.55f
                    }
                }
                val toneSpectrum = spectrum(tone, sampleRate, config)
                val wrongSpectrum = spectrum(wrongTone, sampleRate, config)
                val impulseSpectrum = spectrum(impulse, sampleRate, config)

                assertTrue(toneSpectrum.metronomeBandRatio > wrongSpectrum.metronomeBandRatio)
                assertTrue(toneSpectrum.metronomeBandRatio > impulseSpectrum.metronomeBandRatio)
                assertTrue(impulseSpectrum.broadbandResidualEnergy > 0.0)
            }
        }
    }

    @Test
    fun drumPlusToneRetainsBroadbandResidualAndClippedInputStaysFinite() {
        val sampleRate = 48_000
        val combined = signal(sampleRate) { index ->
            (0.5 * sin(2.0 * PI * 6_000.0 * index / sampleRate)).toFloat()
        }
        val center = sampleRate / 2
        repeat(64) { offset -> combined[center + offset] = if (offset % 3 == 0) 1f else -1f }
        val result = spectrum(combined, sampleRate, MetronomeRejectionConfiguration())
        assertTrue(result.broadbandResidualEnergy.isFinite())
        assertTrue(result.spectralBandwidthHz.isFinite())
        assertTrue(result.spectralCentroidHz.isFinite())
        assertTrue(result.broadbandResidualEnergy > 0.0)
    }

    private fun signal(sampleRate: Int, value: (Int) -> Float): FloatArray =
        FloatArray(sampleRate) { index -> value(index) }

    private fun spectrum(
        values: FloatArray,
        sampleRate: Int,
        configuration: MetronomeRejectionConfiguration,
    ) = ShortWindowSpectrum.analyze(
        pcm = ImmutableFloatSeries.copyOf(values),
        centerSample = (sampleRate / 2).toLong(),
        sampleRateHz = sampleRate,
        metronomeFrequencyHz = 6_000,
        configuration = configuration,
    )
}
