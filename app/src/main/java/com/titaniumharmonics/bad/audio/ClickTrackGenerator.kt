package com.titaniumharmonics.bad.audio

import com.titaniumharmonics.bad.exercise.Exercise
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
        exercise: Exercise,
        sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
        downbeatsOnly: Boolean = false,
    ): ShortArray {
        require(sampleRateHz > 0) { "sampleRateHz must be greater than zero." }

        val timing = ExerciseTiming(exercise)
        val totalMeasureCount =
            exercise.countInMeasures.toLong() + exercise.measureCount
        val totalBeatCount = Math.multiplyExact(
            totalMeasureCount,
            exercise.timeSignature.numerator.toLong(),
        )
        val countInBeatCount = Math.multiplyExact(
            exercise.countInMeasures.toLong(),
            exercise.timeSignature.numerator.toLong(),
        )
        val measuresWithExpectedNotes = exercise.measuresWithExpectedNotes()
        return generateBeatTrack(
            durationNanos = timing.totalDurationNanos,
            beatCount = totalBeatCount,
            countInBeatCount = countInBeatCount,
            beatsPerMeasure = exercise.timeSignature.numerator,
            beatTimeNanos = timing::beatTimeNanos,
            sampleRateHz = sampleRateHz,
            downbeatsOnly = downbeatsOnly,
            measuresWithExpectedNotes = measuresWithExpectedNotes,
        )
    }

    fun generateCountIn(
        exercise: Exercise,
        sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
    ): ShortArray {
        require(sampleRateHz > 0) { "sampleRateHz must be greater than zero." }
        require(exercise.countInMeasures > 0) {
            "Exercise must have at least one count-in measure."
        }

        val timing = ExerciseTiming(exercise)
        val countInBeatCount = Math.multiplyExact(
            exercise.countInMeasures.toLong(),
            exercise.timeSignature.numerator.toLong(),
        )
        return generateBeatTrack(
            durationNanos = timing.countInDurationNanos,
            beatCount = countInBeatCount,
            countInBeatCount = countInBeatCount,
            beatsPerMeasure = exercise.timeSignature.numerator,
            beatTimeNanos = timing::beatTimeNanos,
            sampleRateHz = sampleRateHz,
            downbeatsOnly = false,
            measuresWithExpectedNotes = null,
        )
    }

    private fun generateBeatTrack(
        durationNanos: Long,
        beatCount: Long,
        countInBeatCount: Long,
        beatsPerMeasure: Int,
        beatTimeNanos: (Long) -> Long,
        sampleRateHz: Int,
        downbeatsOnly: Boolean,
        measuresWithExpectedNotes: BooleanArray?,
    ): ShortArray {
        val sampleCount = ceil(
            durationNanos.toDouble() * sampleRateHz / NANOS_PER_SECOND,
        ).toLong()
        val bufferByteCount = sampleCount * BYTES_PER_SAMPLE
        require(sampleCount <= Int.MAX_VALUE && bufferByteCount <= MAX_BUFFER_BYTES) {
            "Metronome audio is too long for the static sample buffer."
        }

        val samples = ShortArray(sampleCount.toInt())
        for (beatIndex in 0 until beatCount) {
            val isCountInBeat = beatIndex < countInBeatCount
            val exerciseBeatIndex = beatIndex - countInBeatCount
            val isExerciseDownbeat =
                exerciseBeatIndex % beatsPerMeasure == 0L
            if (!isCountInBeat && measuresWithExpectedNotes != null) {
                val exerciseMeasureIndex =
                    (exerciseBeatIndex / beatsPerMeasure).toInt()
                if (!measuresWithExpectedNotes[exerciseMeasureIndex]) continue
            }
            if (downbeatsOnly && !isCountInBeat && !isExerciseDownbeat) continue

            val startSample = (
                beatTimeNanos(beatIndex).toDouble() *
                    sampleRateHz / NANOS_PER_SECOND
                ).roundToInt()
            val isAccent = beatIndex % beatsPerMeasure == 0L
            mixClick(
                samples = samples,
                startSample = startSample,
                sampleRateHz = sampleRateHz,
                isAccent = isAccent,
            )
        }
        return samples
    }

    private fun Exercise.measuresWithExpectedNotes(): BooleanArray {
        val numeratorTicks = Math.multiplyExact(
            Math.multiplyExact(
                ticksPerQuarterNote.toLong(),
                timeSignature.numerator.toLong(),
            ),
            4L,
        )
        require(numeratorTicks % timeSignature.denominator == 0L) {
            "ticksPerQuarterNote cannot represent this time signature exactly."
        }
        val ticksPerMeasure = numeratorTicks / timeSignature.denominator
        return BooleanArray(measureCount).also { measuresWithNotes ->
            notes.forEach { note ->
                val measureIndex = (note.positionTicks / ticksPerMeasure).toInt()
                if (measureIndex in measuresWithNotes.indices) {
                    measuresWithNotes[measureIndex] = true
                }
            }
        }
    }

    private fun mixClick(
        samples: ShortArray,
        startSample: Int,
        sampleRateHz: Int,
        isAccent: Boolean,
    ) {
        val clickSampleCount = sampleRateHz * CLICK_DURATION_MILLIS / 1_000
        val frequencyHz = if (isAccent) 1_600.0 else 1_050.0
        val peakAmplitude = if (isAccent) 0.85 else 0.58
        val decayTimeSeconds = if (isAccent) 0.009 else 0.007

        repeat(clickSampleCount) { clickSampleOffset ->
            val destinationIndex = startSample + clickSampleOffset
            if (destinationIndex >= samples.size) return

            val timeSeconds = clickSampleOffset.toDouble() / sampleRateHz
            val envelope = exp(-timeSeconds / decayTimeSeconds)
            val clickSample =
                sin(2.0 * PI * frequencyHz * timeSeconds) *
                    envelope *
                    peakAmplitude *
                    Short.MAX_VALUE
            val mixedSample = samples[destinationIndex].toInt() + clickSample.roundToInt()
            samples[destinationIndex] = mixedSample
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }

    private const val NANOS_PER_SECOND = 1_000_000_000.0
}
