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

    fun applyTo(exercise: RuntimeExercise): RuntimeExercise {
        val measureDurationTicks = exercise.measures.first().durationTicks
        val runtimeMeasures = List(measureCount) { measureIndex ->
            val sourceMeasure = exercise.measures[measureIndex % exercise.measureCount]
            val startTick = Math.multiplyExact(
                measureDurationTicks,
                measureIndex.toLong(),
            )
            RuntimeMeasure(
                index = measureIndex,
                startTick = startTick,
                durationTicks = measureDurationTicks,
                notes = sourceMeasure.notes.map { note ->
                    note.copy(
                        measureIndex = measureIndex,
                        positionTicks = Math.addExact(
                            startTick,
                            note.positionInMeasureTicks,
                        ),
                    )
                },
            )
        }

        return RuntimeExercise(
            id = exercise.id,
            name = exercise.name,
            description = exercise.description,
            tempoBpm = tempoBpm.toDouble(),
            timeSignature = exercise.timeSignature,
            countInMeasures = if (countInEnabled) exercise.countInMeasures else 0,
            ticksPerQuarterNote = exercise.ticksPerQuarterNote,
            measures = runtimeMeasures,
        )
    }

    companion object {
        const val MIN_TEMPO_BPM = 40
        const val MAX_TEMPO_BPM = 240
        const val TEMPO_STEP_BPM = 5
        const val MIN_MEASURE_COUNT = 1
        const val MAX_MEASURE_COUNT = 16

        fun fromExercise(exercise: RuntimeExercise): ExercisePlaybackSettings =
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
