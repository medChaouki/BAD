package com.titaniumharmonics.bad.exercise

object ExerciseCompiler {
    fun compile(exercise: EditableExercise): ExerciseCompilationResult {
        val validationErrors = ExerciseValidator.validate(exercise)
        if (validationErrors.isNotEmpty()) {
            return ExerciseCompilationResult.Failure(validationErrors)
        }

        return try {
            val measureDurationTicks = calculateMeasureDurationTicks(exercise)
            val notesByPattern = exercise.notes.groupBy { note ->
                (note.positionTicks / measureDurationTicks).toInt()
            }
            val expandedMeasureCount = exercise.expandedMeasureCount
            val totalTicks = Math.multiplyExact(
                measureDurationTicks,
                expandedMeasureCount.toLong(),
            )
            var runtimeMeasureIndex = 0
            val runtimeMeasures = buildList(expandedMeasureCount) {
                repeat(exercise.measureCount) { patternIndex ->
                    val patternStartTick = Math.multiplyExact(
                        measureDurationTicks,
                        patternIndex.toLong(),
                    )
                    val patternNotes = notesByPattern[patternIndex].orEmpty().map { note ->
                        PatternNote(
                            positionInMeasureTicks = note.positionTicks - patternStartTick,
                            accent = note.accent,
                            targetIntensity = note.targetIntensity,
                        )
                    }
                    repeat(exercise.measureMultipliers[patternIndex]) {
                        val runtimeStartTick = Math.multiplyExact(
                            measureDurationTicks,
                            runtimeMeasureIndex.toLong(),
                        )
                        add(
                            RuntimeMeasure(
                                index = runtimeMeasureIndex,
                                startTick = runtimeStartTick,
                                durationTicks = measureDurationTicks,
                                notes = patternNotes.map { note ->
                                    val absolutePositionTicks = Math.addExact(
                                        runtimeStartTick,
                                        note.positionInMeasureTicks,
                                    )
                                    check(absolutePositionTicks in 0 until totalTicks) {
                                        "Generated note position is outside runtime bounds."
                                    }
                                    RuntimeExpectedNote(
                                        measureIndex = runtimeMeasureIndex,
                                        positionInMeasureTicks =
                                            note.positionInMeasureTicks,
                                        positionTicks = absolutePositionTicks,
                                        accent = note.accent,
                                        targetIntensity = note.targetIntensity,
                                    )
                                },
                            ),
                        )
                        runtimeMeasureIndex = Math.addExact(runtimeMeasureIndex, 1)
                    }
                }
            }
            ExerciseCompilationResult.Success(
                RuntimeExercise(
                    id = exercise.id,
                    name = exercise.name,
                    description = exercise.description,
                    tempoBpm = exercise.tempoBpm,
                    timeSignature = exercise.timeSignature,
                    ticksPerQuarterNote = exercise.ticksPerQuarterNote,
                    measures = runtimeMeasures,
                ),
            )
        } catch (exception: ArithmeticException) {
            ExerciseCompilationResult.Failure(
                listOf("Expanded exercise timing exceeds the supported runtime range."),
            )
        } catch (exception: IllegalStateException) {
            ExerciseCompilationResult.Failure(
                listOf(
                    exception.message
                        ?: "Exercise cannot produce a valid runtime representation.",
                ),
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

    private data class PatternNote(
        val positionInMeasureTicks: Long,
        val accent: Boolean,
        val targetIntensity: Double?,
    )
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
