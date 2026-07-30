package com.titaniumharmonics.bad.audio

import com.titaniumharmonics.bad.exercise.RuntimeExercise
import com.titaniumharmonics.bad.timing.ExerciseTiming
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

object ClickTrackGenerator {
    const val DEFAULT_SAMPLE_RATE_HZ = 48_000
    private const val MAX_BUFFER_BYTES = 16 * 1024 * 1024
    private const val BYTES_PER_SAMPLE = 2
    private const val CLICK_DURATION_MILLIS = 25

    fun generate(
        exercise: RuntimeExercise,
        sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
        downbeatsOnly: Boolean = false,
    ): ShortArray {
        require(sampleRateHz > 0) { "sampleRateHz must be greater than zero." }

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
                sound = ClickSound.EXERCISE,
            )
        }
        return samples
    }

    fun generateCountIn(
        exercise: RuntimeExercise,
        sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
    ): ShortArray {
        require(sampleRateHz > 0) { "sampleRateHz must be greater than zero." }
        require(exercise.countInMeasures > 0) {
            "Exercise must have at least one count-in measure."
        }

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
    ) {
        if (exercise.countInMeasures == 0) return

        val countInDurationTicks = Math.multiplyExact(
            timing.measureDurationTicks,
            exercise.countInMeasures.toLong(),
        )
        var countInPositionTicks = 0L
        while (countInPositionTicks < countInDurationTicks) {
            mixClick(
                samples = samples,
                startSample = timing.ticksToNanos(countInPositionTicks)
                    .toSampleIndex(sampleRateHz),
                sampleRateHz = sampleRateHz,
                isAccent =
                    countInPositionTicks % timing.measureDurationTicks == 0L,
                sound = ClickSound.COUNT_IN,
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
        sound: ClickSound,
    ) {
        val clickSampleCount = sampleRateHz * CLICK_DURATION_MILLIS / 1_000
        val profile = sound.profile(isAccent)

        repeat(clickSampleCount) { clickSampleOffset ->
            val destinationIndex = startSample + clickSampleOffset
            if (destinationIndex >= samples.size) return

            val timeSeconds = clickSampleOffset.toDouble() / sampleRateHz
            val envelope = exp(-timeSeconds / profile.decayTimeSeconds)
            val clickSample =
                sin(2.0 * PI * profile.frequencyHz * timeSeconds) *
                    envelope *
                    profile.peakAmplitude *
                    Short.MAX_VALUE
            val mixedSample = samples[destinationIndex].toInt() + clickSample.roundToInt()
            samples[destinationIndex] = mixedSample
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    private fun Long.toSampleIndex(sampleRateHz: Int): Int =
        (toDouble() * sampleRateHz / NANOS_PER_SECOND).roundToInt()

    private enum class ClickSound {
        COUNT_IN,
        EXERCISE,
        ;

        fun profile(isAccent: Boolean): ClickProfile = when (this) {
            COUNT_IN -> if (isAccent) {
                ClickProfile(
                    frequencyHz = 2_400.0,
                    peakAmplitude = 0.90,
                    decayTimeSeconds = 0.008,
                )
            } else {
                ClickProfile(
                    frequencyHz = 1_900.0,
                    peakAmplitude = 0.68,
                    decayTimeSeconds = 0.006,
                )
            }
            EXERCISE -> if (isAccent) {
                ClickProfile(
                    frequencyHz = 1_600.0,
                    peakAmplitude = 0.85,
                    decayTimeSeconds = 0.009,
                )
            } else {
                ClickProfile(
                    frequencyHz = 1_050.0,
                    peakAmplitude = 0.58,
                    decayTimeSeconds = 0.007,
                )
            }
        }
    }

    private data class ClickProfile(
        val frequencyHz: Double,
        val peakAmplitude: Double,
        val decayTimeSeconds: Double,
    )

    private const val NANOS_PER_SECOND = 1_000_000_000.0
}
