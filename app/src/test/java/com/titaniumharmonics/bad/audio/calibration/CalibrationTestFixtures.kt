package com.titaniumharmonics.bad.audio.calibration

import com.titaniumharmonics.bad.audio.SyntheticClickSound
import com.titaniumharmonics.bad.audio.SyntheticClickWaveform

internal fun detectorConfig(
    clickCount: Int = 3,
    minimumMatchedClicks: Int = 2,
) = TimingCalibrationConfig(
    clickCount = clickCount,
    leadingSilenceMillis = 100,
    clickIntervalMillis = 150,
    trailingSilenceMillis = 100,
    searchRadiusMillis = 40,
    minimumCorrelation = 0.30,
    minimumMatchedClicks = minimumMatchedClicks,
    maximumSpacingErrorMillis = 4.0,
    maximumOffsetSpreadMillis = 4.0,
    highConfidenceSpreadMillis = 2.0,
)

internal fun recordedClicks(
    sampleRateHz: Int,
    configuration: TimingCalibrationConfig,
    offsetSamples: Int,
    amplitude: Double = 1.0,
    missingIndex: Int? = null,
    clipped: Boolean = false,
    noiseAmplitude: Int = 0,
): Pair<ShortArray, LongArray> {
    val expected = configuration.expectedClickSamples(sampleRateHz, 0L)
    val samples = ShortArray(configuration.sequenceSampleCount(sampleRateHz)) { index ->
        if (noiseAmplitude == 0) 0 else (((index * 1_103 + 17) % (noiseAmplitude * 2 + 1)) - noiseAmplitude).toShort()
    }
    val template = SyntheticClickWaveform.generate(SyntheticClickSound.COUNT_IN_ACCENT, sampleRateHz)
    expected.forEachIndexed { clickIndex, expectedSample ->
        if (clickIndex == missingIndex) return@forEachIndexed
        val start = expectedSample.toInt() + offsetSamples
        template.forEachIndexed { index, value ->
            val destination = start + index
            if (destination in samples.indices) {
                val scaled = if (clipped) {
                    if (value >= 0) Short.MAX_VALUE.toInt() else Short.MIN_VALUE.toInt()
                } else {
                    (value * amplitude).toInt()
                }
                samples[destination] = (samples[destination] + scaled)
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
        }
    }
    return samples to expected
}

internal fun recordedClicksWithOffsets(
    sampleRateHz: Int,
    configuration: TimingCalibrationConfig,
    offsets: IntArray,
): ShortArray {
    val expected = configuration.expectedClickSamples(sampleRateHz, 0L)
    require(offsets.size == expected.size)
    val samples = ShortArray(configuration.sequenceSampleCount(sampleRateHz))
    val template = SyntheticClickWaveform.generate(SyntheticClickSound.COUNT_IN_ACCENT, sampleRateHz)
    expected.forEachIndexed { clickIndex, expectedSample ->
        val start = expectedSample.toInt() + offsets[clickIndex]
        template.forEachIndexed { index, value ->
            val destination = start + index
            if (destination in samples.indices) samples[destination] = value
        }
    }
    return samples
}

internal fun match(expected: Long, offset: Long, correlation: Double = 0.8) =
    CalibrationClickMatch(expected, expected + offset, correlation)
