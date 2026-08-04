package com.titaniumharmonics.bad.exercise

object ExerciseValidator {
    fun validate(exercise: EditableExercise): List<String> = buildList {
        if (exercise.formatVersion != ExerciseFormat.CURRENT_VERSION) {
            add(
                "Unsupported formatVersion ${exercise.formatVersion}; " +
                    "expected ${ExerciseFormat.CURRENT_VERSION}.",
            )
        }
        if (exercise.id.isBlank()) add("Exercise id must not be blank.")
        if (exercise.name.isBlank()) add("Exercise name must not be blank.")
        if (!exercise.tempoBpm.isFinite() || exercise.tempoBpm <= 0.0) {
            add("tempoBpm must be a finite value greater than zero.")
        }
        if (exercise.timeSignature.numerator <= 0) {
            add("Time-signature numerator must be greater than zero.")
        }
        if (!exercise.timeSignature.denominator.isPositivePowerOfTwo()) {
            add("Time-signature denominator must be a positive power of two.")
        }
        if (exercise.measureCount <= 0) {
            add("measureCount must be greater than zero.")
        }
        if (exercise.measureSubdivisions.size != exercise.measureCount) {
            add(
                "measureSubdivisions must contain exactly one entry per measure " +
                    "(${exercise.measureCount} expected).",
            )
        }
        if (exercise.measureMultipliers.size != exercise.measureCount) {
            add(
                "measureMultipliers must contain exactly one entry per measure pattern " +
                    "(${exercise.measureCount} expected).",
            )
        }
        exercise.measureMultipliers.forEachIndexed { index, multiplier ->
            if (
                multiplier !in
                MeasurePatternConstraints.MIN_MULTIPLIER..
                    MeasurePatternConstraints.MAX_MULTIPLIER
            ) {
                add(
                    "measureMultipliers[$index] must be between " +
                        "${MeasurePatternConstraints.MIN_MULTIPLIER} and " +
                        "${MeasurePatternConstraints.MAX_MULTIPLIER}.",
                )
            }
        }
        if (exercise.ticksPerQuarterNote <= 0) {
            add("ticksPerQuarterNote must be greater than zero.")
        }
        val durationCalculation = exercise.calculateDurationTicks()
        durationCalculation.validationError?.let(::add)
        val durationTicks = durationCalculation.durationTicks
        exercise.notes.forEachIndexed { index, note ->
            if (note.positionTicks < 0) {
                add("notes[$index].positionTicks must not be negative.")
            } else if (durationTicks != null && note.positionTicks >= durationTicks) {
                add(
                    "notes[$index].positionTicks must be before exercise duration " +
                        "($durationTicks ticks).",
                )
            }
            note.targetIntensity?.let { intensity ->
                if (!intensity.isFinite() || intensity !in 0.0..1.0) {
                    add("notes[$index].targetIntensity must be between 0.0 and 1.0.")
                }
            }
        }

        exercise.notes.zipWithNext().forEachIndexed { index, (current, next) ->
            if (current.positionTicks >= next.positionTicks) {
                add(
                    "notes must be ordered by positionTicks without duplicates; " +
                        "notes[$index] is not before notes[${index + 1}].",
                )
            }
        }
    }

    private fun EditableExercise.calculateDurationTicks(): DurationCalculation {
        if (
            ticksPerQuarterNote <= 0 ||
            measureCount <= 0 ||
            timeSignature.numerator <= 0 ||
            !timeSignature.denominator.isPositivePowerOfTwo()
        ) {
            return DurationCalculation()
        }

        return try {
            val quarterNoteTicksPerMeasure = Math.multiplyExact(
                ticksPerQuarterNote.toLong(),
                timeSignature.numerator.toLong(),
            )
            val scaledTicksPerMeasure = Math.multiplyExact(quarterNoteTicksPerMeasure, 4L)
            if (scaledTicksPerMeasure % timeSignature.denominator != 0L) {
                return DurationCalculation(
                    validationError =
                        "ticksPerQuarterNote cannot represent this time signature exactly.",
                )
            }
            val ticksPerMeasure = scaledTicksPerMeasure / timeSignature.denominator
            DurationCalculation(
                durationTicks = Math.multiplyExact(ticksPerMeasure, measureCount.toLong()),
            )
        } catch (exception: ArithmeticException) {
            DurationCalculation(
                validationError = "Exercise duration exceeds the supported tick range.",
            )
        }
    }

    private fun Int.isPositivePowerOfTwo(): Boolean =
        this > 0 && (this and (this - 1)) == 0

    private data class DurationCalculation(
        val durationTicks: Long? = null,
        val validationError: String? = null,
    )
}

class InvalidExerciseException(
    val validationErrors: List<String>,
) : IllegalArgumentException(validationErrors.joinToString(separator = "\n"))
