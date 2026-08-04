package com.titaniumharmonics.bad.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

enum class SyntheticClickSound {
    COUNT_IN_ACCENT,
    COUNT_IN,
    EXERCISE_ACCENT,
    EXERCISE,
}

/** One authoritative synthetic click implementation shared by practice and calibration. */
object SyntheticClickWaveform {
    const val DURATION_MILLIS = 25

    fun generate(sound: SyntheticClickSound, sampleRateHz: Int): ShortArray {
        require(sampleRateHz > 0)
        return ShortArray(sampleRateHz * DURATION_MILLIS / 1_000).also {
            mixInto(it, 0, sound, sampleRateHz)
        }
    }

    fun mixInto(
        destination: ShortArray,
        startSample: Int,
        sound: SyntheticClickSound,
        sampleRateHz: Int,
    ) {
        require(startSample >= 0)
        val profile = sound.profile()
        val clickSampleCount = sampleRateHz * DURATION_MILLIS / 1_000
        repeat(clickSampleCount) { offset ->
            val destinationIndex = startSample + offset
            if (destinationIndex >= destination.size) return
            val timeSeconds = offset.toDouble() / sampleRateHz
            val envelope = exp(-timeSeconds / profile.decayTimeSeconds)
            val sample = sin(2.0 * PI * profile.frequencyHz * timeSeconds) *
                envelope * profile.peakAmplitude * Short.MAX_VALUE
            destination[destinationIndex] = (destination[destinationIndex] + sample.roundToInt())
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    private fun SyntheticClickSound.profile(): ClickProfile = when (this) {
        SyntheticClickSound.COUNT_IN_ACCENT -> ClickProfile(2_400.0, 0.90, 0.008)
        SyntheticClickSound.COUNT_IN -> ClickProfile(1_900.0, 0.68, 0.006)
        SyntheticClickSound.EXERCISE_ACCENT -> ClickProfile(1_600.0, 0.85, 0.009)
        SyntheticClickSound.EXERCISE -> ClickProfile(1_050.0, 0.58, 0.007)
    }

    private data class ClickProfile(
        val frequencyHz: Double,
        val peakAmplitude: Double,
        val decayTimeSeconds: Double,
    )
}
