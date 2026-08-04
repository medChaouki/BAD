package com.titaniumharmonics.bad.audio

import com.titaniumharmonics.bad.audio.metronome.MetronomeConfiguration
import com.titaniumharmonics.bad.audio.metronome.WindowedMetronomeToneGenerator

enum class SyntheticClickSound {
    COUNT_IN_ACCENT,
    COUNT_IN,
    EXERCISE_ACCENT,
    EXERCISE,
}

/** Compatibility facade for calibration's fixed default metronome waveform. */
object SyntheticClickWaveform {
    const val DURATION_MILLIS = 10

    fun generate(sound: SyntheticClickSound, sampleRateHz: Int): ShortArray {
        return WindowedMetronomeToneGenerator.generate(
            configuration = MetronomeConfiguration.DEFAULT.tone,
            accent = sound.isAccent,
            sampleRateHz = sampleRateHz,
        )
    }

    fun mixInto(
        destination: ShortArray,
        startSample: Int,
        sound: SyntheticClickSound,
        sampleRateHz: Int,
    ) {
        WindowedMetronomeToneGenerator.mixInto(
            destination = destination,
            startSample = startSample,
            configuration = MetronomeConfiguration.DEFAULT.tone,
            accent = sound.isAccent,
            sampleRateHz = sampleRateHz,
        )
    }

    private val SyntheticClickSound.isAccent: Boolean
        get() = this == SyntheticClickSound.COUNT_IN_ACCENT ||
            this == SyntheticClickSound.EXERCISE_ACCENT
}
