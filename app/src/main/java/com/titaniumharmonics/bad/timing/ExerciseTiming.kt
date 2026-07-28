package com.titaniumharmonics.bad.timing

import com.titaniumharmonics.bad.exercise.Exercise
import com.titaniumharmonics.bad.exercise.ExerciseValidator
import com.titaniumharmonics.bad.exercise.InvalidExerciseException
import kotlin.math.roundToLong

class ExerciseTiming(
    private val exercise: Exercise,
) {
    init {
        val validationErrors = ExerciseValidator.validate(exercise)
        if (validationErrors.isNotEmpty()) {
            throw InvalidExerciseException(validationErrors)
        }
    }

    val beatDurationNanos: Long =
        durationToNanos(quarterNotes = 4.0 / exercise.timeSignature.denominator)

    val countInDurationNanos: Long =
        durationToNanos(
            quarterNotes = exercise.countInMeasures.toDouble() *
                exercise.timeSignature.numerator *
                4.0 / exercise.timeSignature.denominator,
        )

    val exerciseDurationNanos: Long =
        durationToNanos(
            quarterNotes = exercise.measureCount.toDouble() *
                exercise.timeSignature.numerator *
                4.0 / exercise.timeSignature.denominator,
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
    }
}
