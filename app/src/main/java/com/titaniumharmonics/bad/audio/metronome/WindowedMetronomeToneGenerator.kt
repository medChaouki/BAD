package com.titaniumharmonics.bad.audio.metronome

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object WindowedMetronomeToneGenerator {
    fun generate(
        configuration: MetronomeToneConfiguration,
        accent: Boolean,
        sampleRateHz: Int,
    ): ShortArray {
        require(sampleRateHz > 0)
        require(configuration.frequencyHz < sampleRateHz / 2.0) {
            "Tone frequency must be below Nyquist."
        }
        val sampleCount = (configuration.durationMillis.toLong() * sampleRateHz / 1_000L)
            .coerceAtLeast(2L)
        require(sampleCount <= Int.MAX_VALUE)
        val volume = (
            if (accent) configuration.accentVolumePercent else configuration.normalVolumePercent
            ) / 100.0
        return ShortArray(sampleCount.toInt()) { index ->
            val window = when (configuration.window) {
                MetronomeWindow.HANN_V1 -> if (sampleCount == 1L) {
                    1.0
                } else {
                    0.5 * (1.0 - cos(2.0 * PI * index / (sampleCount - 1L)))
                }
            }
            val phase = 2.0 * PI * configuration.frequencyHz * index / sampleRateHz
            (sin(phase) * window * volume * Short.MAX_VALUE)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    fun mixInto(
        destination: ShortArray,
        startSample: Int,
        configuration: MetronomeToneConfiguration,
        accent: Boolean,
        sampleRateHz: Int,
    ) {
        require(startSample >= 0)
        val tone = generate(configuration, accent, sampleRateHz)
        tone.forEachIndexed { offset, value ->
            val destinationIndex = startSample + offset
            if (destinationIndex >= destination.size) return
            destination[destinationIndex] = (destination[destinationIndex] + value)
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    fun generateSequence(
        configuration: MetronomeToneConfiguration,
        accent: Boolean,
        sampleRateHz: Int,
        beatCount: Int,
        beatIntervalMillis: Int,
        totalDurationMillis: Int,
    ): ShortArray {
        require(beatCount > 0)
        require(beatIntervalMillis > 0)
        require(totalDurationMillis > 0)
        val totalSamples = totalDurationMillis.toLong() * sampleRateHz / 1_000L
        require(totalSamples in 1..Int.MAX_VALUE.toLong())
        val tone = generate(configuration, accent, sampleRateHz)
        val intervalSamples = beatIntervalMillis.toLong() * sampleRateHz / 1_000L
        val finalBeatStart = (beatCount - 1L) * intervalSamples
        require(finalBeatStart + tone.size <= totalSamples) {
            "The tone sequence does not fit inside its requested duration."
        }
        return ShortArray(totalSamples.toInt()).also { destination ->
            repeat(beatCount) { beatIndex ->
                tone.copyInto(
                    destination = destination,
                    destinationOffset = (beatIndex * intervalSamples).toInt(),
                )
            }
        }
    }
}
