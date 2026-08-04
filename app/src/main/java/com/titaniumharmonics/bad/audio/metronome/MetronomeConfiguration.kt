package com.titaniumharmonics.bad.audio.metronome

enum class MetronomeWindow {
    HANN_V1,
}

data class MetronomeToneConfiguration(
    val frequencyHz: Int = DEFAULT_FREQUENCY_HZ,
    val durationMillis: Int = DEFAULT_DURATION_MILLIS,
    val normalVolumePercent: Int = DEFAULT_NORMAL_VOLUME_PERCENT,
    val accentVolumePercent: Int = DEFAULT_ACCENT_VOLUME_PERCENT,
    val window: MetronomeWindow = MetronomeWindow.HANN_V1,
) {
    init {
        require(frequencyHz in MIN_FREQUENCY_HZ..MAX_FREQUENCY_HZ)
        require(durationMillis in MIN_DURATION_MILLIS..MAX_DURATION_MILLIS)
        require(normalVolumePercent in MIN_VOLUME_PERCENT..MAX_VOLUME_PERCENT)
        require(accentVolumePercent in MIN_VOLUME_PERCENT..MAX_VOLUME_PERCENT)
    }

    companion object {
        const val DEFAULT_FREQUENCY_HZ = 6_000
        const val DEFAULT_DURATION_MILLIS = 10
        const val DEFAULT_NORMAL_VOLUME_PERCENT = 55
        const val DEFAULT_ACCENT_VOLUME_PERCENT = 85
        const val MIN_FREQUENCY_HZ = 3_000
        const val MAX_FREQUENCY_HZ = 9_000
        const val MIN_DURATION_MILLIS = 5
        const val MAX_DURATION_MILLIS = 30
        const val MIN_VOLUME_PERCENT = 0
        const val MAX_VOLUME_PERCENT = 100
    }
}

data class MetronomeNotchConfiguration(
    val enabled: Boolean = true,
    val centerFrequencyHz: Int = MetronomeToneConfiguration.DEFAULT_FREQUENCY_HZ,
    val qFactor: Double = DEFAULT_Q_FACTOR,
    val centerLinkedToTone: Boolean = true,
) {
    init {
        require(centerFrequencyHz in MIN_CENTER_FREQUENCY_HZ..MAX_CENTER_FREQUENCY_HZ)
        require(qFactor.isFinite() && qFactor in MIN_Q_FACTOR..MAX_Q_FACTOR)
    }

    companion object {
        const val DEFAULT_Q_FACTOR = 10.0
        const val MIN_CENTER_FREQUENCY_HZ = 3_000
        const val MAX_CENTER_FREQUENCY_HZ = 9_000
        const val MIN_Q_FACTOR = 2.0
        const val MAX_Q_FACTOR = 30.0
    }
}

/** Public, immutable configuration shared by playback, recording, and offline analysis. */
data class MetronomeConfiguration(
    val tone: MetronomeToneConfiguration = MetronomeToneConfiguration(),
    val notch: MetronomeNotchConfiguration = MetronomeNotchConfiguration(),
    val version: Int = CURRENT_VERSION,
) {
    init {
        require(version > 0)
        require(!notch.centerLinkedToTone || notch.centerFrequencyHz == tone.frequencyHz) {
            "A linked notch center must match the tone frequency."
        }
    }

    fun withToneFrequency(frequencyHz: Int): MetronomeConfiguration {
        val updatedTone = tone.copy(frequencyHz = frequencyHz)
        return copy(
            tone = updatedTone,
            notch = if (notch.centerLinkedToTone) {
                notch.copy(centerFrequencyHz = frequencyHz)
            } else {
                notch
            },
        )
    }

    fun withCustomNotchCenter(centerFrequencyHz: Int): MetronomeConfiguration = copy(
        notch = notch.copy(
            centerFrequencyHz = centerFrequencyHz,
            centerLinkedToTone = false,
        ),
    )

    fun relinkNotchCenter(): MetronomeConfiguration = copy(
        notch = notch.copy(
            centerFrequencyHz = tone.frequencyHz,
            centerLinkedToTone = true,
        ),
    )

    fun validationErrors(sampleRateHz: Int): List<String> {
        if (sampleRateHz <= 0) return listOf("Sample rate must be positive.")
        val nyquistHz = sampleRateHz / 2.0
        return buildList {
            if (tone.frequencyHz >= nyquistHz) {
                add("Tone frequency must be below the $nyquistHz Hz Nyquist limit.")
            }
            if (notch.centerFrequencyHz >= nyquistHz) {
                add("Notch center must be below the $nyquistHz Hz Nyquist limit.")
            }
        }
    }

    fun requireValidForSampleRate(sampleRateHz: Int) {
        val errors = validationErrors(sampleRateHz)
        require(errors.isEmpty()) { errors.joinToString(" ") }
    }

    companion object {
        const val CURRENT_VERSION = 1
        val DEFAULT = MetronomeConfiguration()
    }
}

/** Frozen configuration used by one completed or active practice session. */
data class SessionMetronomeSnapshot(
    val configuration: MetronomeConfiguration = MetronomeConfiguration.DEFAULT,
    val downbeatsOnly: Boolean = false,
) {
    companion object {
        val COMPATIBILITY_FALLBACK = SessionMetronomeSnapshot()
    }
}
