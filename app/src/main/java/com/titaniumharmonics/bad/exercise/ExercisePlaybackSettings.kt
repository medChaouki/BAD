package com.titaniumharmonics.bad.exercise

data class ExercisePlaybackSettings(
    val tempoBpm: Int,
    val countInEnabled: Boolean,
    val measureCount: Int,
    val downbeatsOnly: Boolean = false,
) {
    init {
        require(tempoBpm in MIN_TEMPO_BPM..MAX_TEMPO_BPM) {
            "tempoBpm must be between $MIN_TEMPO_BPM and $MAX_TEMPO_BPM."
        }
        require(measureCount in MIN_MEASURE_COUNT..MAX_MEASURE_COUNT) {
            "measureCount must be between $MIN_MEASURE_COUNT and $MAX_MEASURE_COUNT."
        }
    }

    fun applyTo(exercise: Exercise): Exercise {
        val sourceDurationTicks = exercise.durationTicks()
        val targetDurationTicks = exercise.durationTicks(measureCount)
        val repeatedNotes = buildList {
            var cycleOffsetTicks = 0L
            while (cycleOffsetTicks < targetDurationTicks) {
                exercise.notes.forEach { note ->
                    val repeatedPositionTicks = Math.addExact(
                        note.positionTicks,
                        cycleOffsetTicks,
                    )
                    if (repeatedPositionTicks < targetDurationTicks) {
                        add(note.copy(positionTicks = repeatedPositionTicks))
                    }
                }
                cycleOffsetTicks = Math.addExact(cycleOffsetTicks, sourceDurationTicks)
            }
        }

        return exercise.copy(
            tempoBpm = tempoBpm.toDouble(),
            countInMeasures = if (countInEnabled) exercise.countInMeasures else 0,
            measureCount = measureCount,
            notes = repeatedNotes,
        )
    }

    private fun Exercise.durationTicks(
        measures: Int = measureCount,
    ): Long {
        val numeratorTicks = Math.multiplyExact(
            Math.multiplyExact(ticksPerQuarterNote.toLong(), timeSignature.numerator.toLong()),
            4L,
        )
        require(numeratorTicks % timeSignature.denominator == 0L) {
            "ticksPerQuarterNote cannot represent this time signature exactly."
        }
        return Math.multiplyExact(
            numeratorTicks / timeSignature.denominator,
            measures.toLong(),
        )
    }

    companion object {
        const val MIN_TEMPO_BPM = 40
        const val MAX_TEMPO_BPM = 240
        const val TEMPO_STEP_BPM = 5
        const val MIN_MEASURE_COUNT = 1
        const val MAX_MEASURE_COUNT = 16

        fun fromExercise(exercise: Exercise): ExercisePlaybackSettings =
            ExercisePlaybackSettings(
                tempoBpm = exercise.tempoBpm.toInt()
                    .coerceIn(MIN_TEMPO_BPM, MAX_TEMPO_BPM),
                countInEnabled = exercise.countInMeasures > 0,
                measureCount = exercise.measureCount
                    .coerceIn(MIN_MEASURE_COUNT, MAX_MEASURE_COUNT),
                downbeatsOnly = false,
            )
    }
}
