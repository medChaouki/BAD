package com.titaniumharmonics.bad.timing

import com.titaniumharmonics.bad.exercise.RuntimeExercise
import kotlin.math.roundToLong

class ExerciseTiming(
    private val exercise: RuntimeExercise,
) {

    val beatDurationNanos: Long =
        durationToNanos(quarterNotes = 4.0 / exercise.timeSignature.denominator)

    val quarterNoteDurationNanos: Long =
        durationToNanos(quarterNotes = 1.0)

    val measureDurationTicks: Long =
        Math.multiplyExact(
            Math.multiplyExact(
                exercise.ticksPerQuarterNote.toLong(),
                exercise.timeSignature.numerator.toLong(),
            ),
            4L,
        ) / exercise.timeSignature.denominator

    val beatHighlightDurationNanos: Long =
        (beatDurationNanos / BEAT_HIGHLIGHT_DURATION_DIVISOR).coerceAtLeast(1L)

    val countInDurationNanos: Long =
        durationToNanos(
            quarterNotes = exercise.timeSignature.numerator *
                4.0 / exercise.timeSignature.denominator,
        )

    val countInQuarterNoteCount: Int =
        (measureDurationTicks / exercise.ticksPerQuarterNote).toInt()

    val exerciseDurationNanos: Long =
        durationToNanos(
            quarterNotes = exercise.totalTicks.toDouble() /
                exercise.ticksPerQuarterNote,
        )

    val totalDurationNanos: Long =
        try {
            Math.addExact(countInDurationNanos, exerciseDurationNanos)
        } catch (exception: ArithmeticException) {
            throw IllegalArgumentException(
                "Total session duration is outside the supported nanosecond range.",
                exception,
            )
        }

    fun ticksToNanos(positionTicks: Long): Long {
        require(positionTicks >= 0) { "positionTicks must not be negative." }
        return durationToNanos(
            quarterNotes = positionTicks.toDouble() / exercise.ticksPerQuarterNote,
        )
    }

    fun beatTimeNanos(beatIndex: Long): Long {
        require(beatIndex >= 0) { "beatIndex must not be negative." }
        return durationToNanos(
            quarterNotes = beatIndex.toDouble() *
                4.0 / exercise.timeSignature.denominator,
        )
    }

    fun measureNumberAt(exerciseElapsedNanos: Long): Int {
        val measureDurationNanos =
            (exerciseDurationNanos / exercise.measureCount).coerceAtLeast(1L)
        return ((exerciseElapsedNanos.coerceAtLeast(0L) / measureDurationNanos) + 1L)
            .coerceAtMost(exercise.measureCount.toLong())
            .toInt()
    }

    fun highlightedBeatTimeNanos(exerciseElapsedNanos: Long): Long? {
        if (exerciseElapsedNanos !in 0 until exerciseDurationNanos) return null

        var beatIndex = exerciseElapsedNanos / beatDurationNanos
        var beatTimeNanos = beatTimeNanos(beatIndex)
        if (beatTimeNanos > exerciseElapsedNanos && beatIndex > 0L) {
            beatIndex -= 1
            beatTimeNanos = beatTimeNanos(beatIndex)
        }

        val elapsedSinceBeatNanos = exerciseElapsedNanos - beatTimeNanos
        return beatTimeNanos.takeIf {
            elapsedSinceBeatNanos in 0 until beatHighlightDurationNanos
        }
    }

    private fun durationToNanos(quarterNotes: Double): Long {
        val durationNanos =
            quarterNotes * NANOS_PER_MINUTE / exercise.tempoBpm
        require(durationNanos.isFinite() && durationNanos in 0.0..Long.MAX_VALUE.toDouble()) {
            "Exercise duration is outside the supported nanosecond range."
        }
        return durationNanos.roundToLong()
    }

    private companion object {
        const val NANOS_PER_MINUTE = 60_000_000_000.0
        const val BEAT_HIGHLIGHT_DURATION_DIVISOR = 4L
    }
}
