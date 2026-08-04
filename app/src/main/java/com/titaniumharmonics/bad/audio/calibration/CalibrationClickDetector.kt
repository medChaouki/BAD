package com.titaniumharmonics.bad.audio.calibration

import com.titaniumharmonics.bad.audio.SyntheticClickSound
import com.titaniumharmonics.bad.audio.SyntheticClickWaveform
import com.titaniumharmonics.bad.audio.analysis.FirstOrderHighPassFilter
import com.titaniumharmonics.bad.audio.analysis.MeanDcOffsetRemover
import com.titaniumharmonics.bad.audio.analysis.Pcm16Normalizer
import kotlin.math.abs
import kotlin.math.sqrt

class CalibrationClickDetector(
    private val configuration: TimingCalibrationConfig = TimingCalibrationConfig(),
) {
    fun detect(
        recordedPcm: ShortArray,
        sampleRateHz: Int,
        expectedClickSamples: LongArray,
        cancellationCheck: () -> Unit = {},
    ): List<CalibrationClickMatch> {
        require(sampleRateHz > 0)
        val template = Pcm16Normalizer.normalize(
            SyntheticClickWaveform.generate(
                SyntheticClickSound.COUNT_IN_ACCENT,
                sampleRateHz,
            ),
        )
        MeanDcOffsetRemover.removeInPlace(template)
        FirstOrderHighPassFilter.filterInPlace(template, sampleRateHz, 80.0)
        val signal = Pcm16Normalizer.normalize(recordedPcm)
        MeanDcOffsetRemover.removeInPlace(signal)
        FirstOrderHighPassFilter.filterInPlace(signal, sampleRateHz, 80.0)

        val searchRadius = millisecondsToSamples(
            configuration.searchRadiusMillis.toLong(),
            sampleRateHz,
        ).toInt()
        val matches = mutableListOf<CalibrationClickMatch>()
        expectedClickSamples.forEachIndexed { index, expected ->
            cancellationCheck()
            val start = (expected - searchRadius).coerceAtLeast(0L).toInt()
            val endExclusive = (expected + searchRadius + 1L)
                .coerceAtMost((signal.size - template.size + 1).toLong())
                .toInt()
            if (start >= endExclusive) return@forEachIndexed

            var bestIndex = start
            var bestCorrelation = 0.0
            var candidate = start
            while (candidate < endExclusive) {
                val correlation = normalizedCorrelation(
                    signal,
                    template,
                    candidate,
                    TEMPLATE_COARSE_STRIDE,
                )
                if (correlation > bestCorrelation) {
                    bestCorrelation = correlation
                    bestIndex = candidate
                }
                candidate += SEARCH_COARSE_STRIDE
            }
            val refineStart = (bestIndex - SEARCH_COARSE_STRIDE).coerceAtLeast(start)
            val refineEnd = (bestIndex + SEARCH_COARSE_STRIDE + 1).coerceAtMost(endExclusive)
            for (refined in refineStart until refineEnd) {
                val correlation = normalizedCorrelation(signal, template, refined, 1)
                if (correlation > bestCorrelation) {
                    bestCorrelation = correlation
                    bestIndex = refined
                }
            }
            if (bestCorrelation >= configuration.minimumCorrelation) {
                matches += CalibrationClickMatch(
                    expectedSample = expected,
                    detectedSample = bestIndex.toLong(),
                    correlation = bestCorrelation,
                )
            }
            if (index % 2 == 1) cancellationCheck()
        }
        return immutableList(matches)
    }

    private fun normalizedCorrelation(
        signal: FloatArray,
        template: FloatArray,
        signalStart: Int,
        stride: Int,
    ): Double {
        var dot = 0.0
        var signalEnergy = 0.0
        var templateEnergy = 0.0
        var index = 0
        while (index < template.size) {
            val signalValue = signal[signalStart + index].toDouble()
            val templateValue = template[index].toDouble()
            dot += signalValue * templateValue
            signalEnergy += signalValue * signalValue
            templateEnergy += templateValue * templateValue
            index += stride
        }
        if (signalEnergy <= MINIMUM_ENERGY || templateEnergy <= MINIMUM_ENERGY) return 0.0
        return (abs(dot) / sqrt(signalEnergy * templateEnergy)).coerceIn(0.0, 1.0)
    }

    private companion object {
        const val SEARCH_COARSE_STRIDE = 4
        const val TEMPLATE_COARSE_STRIDE = 2
        const val MINIMUM_ENERGY = 1e-12
    }
}
