package com.titaniumharmonics.bad.exercise

object ExerciseCompiler {
    fun compile(exercise: EditableExercise): ExerciseCompilationResult {
        val validationErrors = ExerciseValidator.validate(exercise)
        if (validationErrors.isNotEmpty()) {
            return ExerciseCompilationResult.Failure(validationErrors)
        }

        return try {
            val measureDurationTicks = calculateMeasureDurationTicks(exercise)
            val notesByMeasure = exercise.notes.groupBy { note ->
                (note.positionTicks / measureDurationTicks).toInt()
            }
            val runtimeMeasures = List(exercise.measureCount) { measureIndex ->
                val startTick = Math.multiplyExact(
                    measureDurationTicks,
                    measureIndex.toLong(),
                )
                RuntimeMeasure(
                    index = measureIndex,
                    startTick = startTick,
                    durationTicks = measureDurationTicks,
                    notes = notesByMeasure[measureIndex].orEmpty().map { note ->
                        RuntimeExpectedNote(
                            measureIndex = measureIndex,
                            positionInMeasureTicks = note.positionTicks - startTick,
                            positionTicks = note.positionTicks,
                            accent = note.accent,
                            targetIntensity = note.targetIntensity,
                        )
                    },
                )
            }
            ExerciseCompilationResult.Success(
                RuntimeExercise(
                    id = exercise.id,
                    name = exercise.name,
                    description = exercise.description,
                    tempoBpm = exercise.tempoBpm,
                    timeSignature = exercise.timeSignature,
                    countInMeasures = exercise.countInMeasures,
                    ticksPerQuarterNote = exercise.ticksPerQuarterNote,
                    measures = runtimeMeasures,
                ),
            )
        } catch (exception: ArithmeticException) {
            ExerciseCompilationResult.Failure(
                listOf("Exercise timing exceeds the supported tick range."),
            )
        }
    }

    private fun calculateMeasureDurationTicks(exercise: EditableExercise): Long {
        val scaledTicks = Math.multiplyExact(
            Math.multiplyExact(
                exercise.ticksPerQuarterNote.toLong(),
                exercise.timeSignature.numerator.toLong(),
            ),
            4L,
        )
        return scaledTicks / exercise.timeSignature.denominator
    }
}

sealed interface ExerciseCompilationResult {
    data class Success(
        val exercise: RuntimeExercise,
    ) : ExerciseCompilationResult

    data class Failure(
        val validationErrors: List<String>,
    ) : ExerciseCompilationResult {
        init {
            require(validationErrors.isNotEmpty())
        }
    }
}
