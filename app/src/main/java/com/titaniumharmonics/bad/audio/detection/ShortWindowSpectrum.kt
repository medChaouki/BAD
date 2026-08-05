package com.titaniumharmonics.bad.audio.detection

import com.titaniumharmonics.bad.audio.analysis.ImmutableFloatSeries
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class CandidateSpectrum(
    val metronomeBandRatio: Double,
    val broadbandResidualEnergy: Double,
    val spectralBandwidthHz: Double,
    val spectralCentroidHz: Double,
)

object ShortWindowSpectrum {
    fun analyze(
        pcm: ImmutableFloatSeries,
        centerSample: Long,
        sampleRateHz: Int,
        metronomeFrequencyHz: Int,
        configuration: MetronomeRejectionConfiguration,
    ): CandidateSpectrum {
        require(sampleRateHz > 0)
        require(metronomeFrequencyHz in 1 until sampleRateHz / 2)
        val fftSize = configuration.fftSize
        val requestedWindow = (
            configuration.analysisWindowMillis * sampleRateHz / 1_000.0
            ).toInt().coerceIn(2, fftSize)
        val real = DoubleArray(fftSize)
        val imaginary = DoubleArray(fftSize)
        val start = centerSample - requestedWindow / 2L
        repeat(requestedWindow) { index ->
            val sourceIndex = start + index
            val sample = if (sourceIndex in 0 until pcm.size.toLong()) {
                pcm[sourceIndex.toInt()].toDouble()
            } else {
                0.0
            }
            val window = 0.5 * (1.0 - cos(2.0 * PI * index / (requestedWindow - 1)))
            real[index] = sample * window
        }
        fftInPlace(real, imaginary)

        val halfBand = configuration.metronomeBandWidthHz / 2.0
        var totalEnergy = 0.0
        var bandEnergy = 0.0
        var weightedFrequency = 0.0
        val energies = DoubleArray(fftSize / 2 + 1)
        for (bin in energies.indices) {
            val energy = real[bin] * real[bin] + imaginary[bin] * imaginary[bin]
            val frequency = bin.toDouble() * sampleRateHz / fftSize
            energies[bin] = energy
            totalEnergy += energy
            weightedFrequency += frequency * energy
            if (frequency in
                (metronomeFrequencyHz - halfBand)..(metronomeFrequencyHz + halfBand)
            ) {
                bandEnergy += energy
            }
        }
        if (totalEnergy <= MINIMUM_ENERGY || !totalEnergy.isFinite()) {
            return CandidateSpectrum(0.0, 0.0, 0.0, 0.0)
        }
        val centroid = weightedFrequency / totalEnergy
        var variance = 0.0
        energies.forEachIndexed { bin, energy ->
            val frequency = bin.toDouble() * sampleRateHz / fftSize
            val delta = frequency - centroid
            variance += delta * delta * energy
        }
        val outsideEnergy = (totalEnergy - bandEnergy).coerceAtLeast(0.0)
        return CandidateSpectrum(
            metronomeBandRatio = (bandEnergy / totalEnergy).coerceIn(0.0, 1.0),
            broadbandResidualEnergy = (sqrt(outsideEnergy) / fftSize).coerceIn(0.0, 1.0),
            spectralBandwidthHz = sqrt(variance / totalEnergy).coerceAtLeast(0.0),
            spectralCentroidHz = centroid.coerceAtLeast(0.0),
        )
    }

    private fun fftInPlace(real: DoubleArray, imaginary: DoubleArray) {
        val size = real.size
        require(size > 1 && size and (size - 1) == 0)
        var target = 0
        for (source in 1 until size) {
            var bit = size shr 1
            while (target and bit != 0) {
                target = target xor bit
                bit = bit shr 1
            }
            target = target xor bit
            if (source < target) {
                val realValue = real[source]
                real[source] = real[target]
                real[target] = realValue
                val imaginaryValue = imaginary[source]
                imaginary[source] = imaginary[target]
                imaginary[target] = imaginaryValue
            }
        }
        var length = 2
        while (length <= size) {
            val angle = -2.0 * PI / length
            val baseReal = cos(angle)
            val baseImaginary = sin(angle)
            var start = 0
            while (start < size) {
                var twiddleReal = 1.0
                var twiddleImaginary = 0.0
                repeat(length / 2) { offset ->
                    val even = start + offset
                    val odd = even + length / 2
                    val oddReal = real[odd] * twiddleReal - imaginary[odd] * twiddleImaginary
                    val oddImaginary = real[odd] * twiddleImaginary + imaginary[odd] * twiddleReal
                    real[odd] = real[even] - oddReal
                    imaginary[odd] = imaginary[even] - oddImaginary
                    real[even] += oddReal
                    imaginary[even] += oddImaginary
                    val nextReal = twiddleReal * baseReal - twiddleImaginary * baseImaginary
                    twiddleImaginary = twiddleReal * baseImaginary + twiddleImaginary * baseReal
                    twiddleReal = nextReal
                }
                start += length
            }
            length = length shl 1
        }
    }

    private const val MINIMUM_ENERGY = 1e-20
}
