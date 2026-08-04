package com.titaniumharmonics.bad.audio.calibration

import com.titaniumharmonics.bad.audio.SyntheticClickSound
import com.titaniumharmonics.bad.audio.SyntheticClickWaveform

data class CalibrationClickSequence(
    val samples: ShortArray,
    val clickPositions: LongArray,
    val sampleRateHz: Int,
)

object CalibrationClickSequenceGenerator {
    fun generate(
        sampleRateHz: Int,
        configuration: TimingCalibrationConfig = TimingCalibrationConfig(),
    ): CalibrationClickSequence {
        val samples = ShortArray(configuration.sequenceSampleCount(sampleRateHz))
        val positions = configuration.expectedClickSamples(sampleRateHz, 0L)
        positions.forEach { position ->
            SyntheticClickWaveform.mixInto(
                destination = samples,
                startSample = position.toInt(),
                sound = SyntheticClickSound.COUNT_IN_ACCENT,
                sampleRateHz = sampleRateHz,
            )
        }
        return CalibrationClickSequence(samples, positions, sampleRateHz)
    }
}
