package com.titaniumharmonics.bad.audio

import com.titaniumharmonics.bad.exercise.RuntimeExercise
import com.titaniumharmonics.bad.audio.metronome.MetronomeConfiguration
import com.titaniumharmonics.bad.audio.metronome.WindowedMetronomeToneGenerator
import com.titaniumharmonics.bad.timing.ExerciseTiming
import kotlin.math.ceil
import kotlin.math.roundToInt

object ClickTrackGenerator {
    const val DEFAULT_SAMPLE_RATE_HZ = 48_000
    private const val MAX_BUFFER_BYTES = 16 * 1024 * 1024
    private const val BYTES_PER_SAMPLE = 2

    fun generate(
        exercise: RuntimeExercise,
        sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
        downbeatsOnly: Boolean = false,
        configuration: MetronomeConfiguration = MetronomeConfiguration.DEFAULT,
    ): ShortArray {
        require(sampleRateHz > 0) { "sampleRateHz must be greater than zero." }
        configuration.requireValidForSampleRate(sampleRateHz)

        val timing = ExerciseTiming(exercise)
        val samples = createSampleBuffer(
            durationNanos = timing.totalDurationNanos,
            sampleRateHz = sampleRateHz,
        )
        mixCountIn(
            samples = samples,
            exercise = exercise,
            timing = timing,
            sampleRateHz = sampleRateHz,
            configuration = configuration,
        )

        exercise.notes.forEach { note ->
            val isMeasureStart =
                note.positionTicks % timing.measureDurationTicks == 0L
            if (downbeatsOnly && !isMeasureStart) return@forEach

            val noteTimeNanos = Math.addExact(
                timing.countInDurationNanos,
                timing.ticksToNanos(note.positionTicks),
            )
            mixClick(
                samples = samples,
                startSample = noteTimeNanos.toSampleIndex(sampleRateHz),
                sampleRateHz = sampleRateHz,
                isAccent = note.accent || isMeasureStart,
                configuration = configuration,
            )
        }
        return samples
    }

    fun generateCountIn(
        exercise: RuntimeExercise,
        sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
        configuration: MetronomeConfiguration = MetronomeConfiguration.DEFAULT,
    ): ShortArray {
        require(sampleRateHz > 0) { "sampleRateHz must be greater than zero." }
        configuration.requireValidForSampleRate(sampleRateHz)

        val timing = ExerciseTiming(exercise)
        val samples = createSampleBuffer(
            durationNanos = timing.countInDurationNanos,
            sampleRateHz = sampleRateHz,
        )
        mixCountIn(
            samples = samples,
            exercise = exercise,
            timing = timing,
            sampleRateHz = sampleRateHz,
            configuration = configuration,
        )
        return samples
    }

    private fun createSampleBuffer(
        durationNanos: Long,
        sampleRateHz: Int,
    ): ShortArray {
        val sampleCount = ceil(
            durationNanos.toDouble() * sampleRateHz / NANOS_PER_SECOND,
        ).toLong()
        val bufferByteCount = sampleCount * BYTES_PER_SAMPLE
        require(sampleCount <= Int.MAX_VALUE && bufferByteCount <= MAX_BUFFER_BYTES) {
            "Metronome audio is too long for the static sample buffer."
        }
        return ShortArray(sampleCount.toInt())
    }

    private fun mixCountIn(
        samples: ShortArray,
        exercise: RuntimeExercise,
        timing: ExerciseTiming,
        sampleRateHz: Int,
        configuration: MetronomeConfiguration,
    ) {
        val countInDurationTicks = timing.measureDurationTicks
        var countInPositionTicks = 0L
        while (countInPositionTicks < countInDurationTicks) {
            mixClick(
                samples = samples,
                startSample = timing.ticksToNanos(countInPositionTicks)
                    .toSampleIndex(sampleRateHz),
                sampleRateHz = sampleRateHz,
                isAccent =
                    countInPositionTicks % timing.measureDurationTicks == 0L,
                configuration = configuration,
            )
            countInPositionTicks = Math.addExact(
                countInPositionTicks,
                exercise.ticksPerQuarterNote.toLong(),
            )
        }
    }

    private fun mixClick(
        samples: ShortArray,
        startSample: Int,
        sampleRateHz: Int,
        isAccent: Boolean,
        configuration: MetronomeConfiguration,
    ) {
        WindowedMetronomeToneGenerator.mixInto(
            destination = samples,
            startSample = startSample,
            configuration = configuration.tone,
            accent = isAccent,
            sampleRateHz = sampleRateHz,
        )
    }

    private fun Long.toSampleIndex(sampleRateHz: Int): Int =
        (toDouble() * sampleRateHz / NANOS_PER_SECOND).roundToInt()

    private const val NANOS_PER_SECOND = 1_000_000_000.0
}
