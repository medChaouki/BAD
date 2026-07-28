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
    ): ShortArray {
        require(sampleRateHz > 0) { "sampleRateHz must be greater than zero." }

        val timing = ExerciseTiming(exercise)
        val sampleCount = ceil(
            timing.totalDurationNanos.toDouble() *
                sampleRateHz / NANOS_PER_SECOND,
        ).toLong()
        val bufferByteCount = sampleCount * BYTES_PER_SAMPLE
        require(sampleCount <= Int.MAX_VALUE && bufferByteCount <= MAX_BUFFER_BYTES) {
            "Exercise is too long for the static metronome buffer."
        }

        val samples = ShortArray(sampleCount.toInt())
        val totalMeasureCount =
            exercise.countInMeasures.toLong() + exercise.measureCount
        val totalBeatCount = Math.multiplyExact(
            totalMeasureCount,
            exercise.timeSignature.numerator.toLong(),
        )
        for (beatIndex in 0 until totalBeatCount) {
            val startSample = (
                timing.beatTimeNanos(beatIndex).toDouble() *
                    sampleRateHz / NANOS_PER_SECOND
                ).roundToInt()
            val isAccent = beatIndex % exercise.timeSignature.numerator == 0L
            mixClick(
                samples = samples,
                startSample = startSample,
                sampleRateHz = sampleRateHz,
                isAccent = isAccent,
            )
        }
        return samples
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
