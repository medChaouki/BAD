package com.titaniumharmonics.bad.exercise

data class ExercisePlaybackSettings(
    val tempoBpm: Int,
    val measureCount: Int,
    val downbeatsOnly: Boolean = false,
    val maximumMeasureCount: Int = DEFAULT_MAX_MEASURE_COUNT,
) {
    init {
        require(tempoBpm in MIN_TEMPO_BPM..MAX_TEMPO_BPM) {
            "tempoBpm must be between $MIN_TEMPO_BPM and $MAX_TEMPO_BPM."
        }
        require(maximumMeasureCount >= MIN_MEASURE_COUNT) {
            "maximumMeasureCount must be at least $MIN_MEASURE_COUNT."
        }
        require(measureCount in MIN_MEASURE_COUNT..maximumMeasureCount) {
            "measureCount must be between $MIN_MEASURE_COUNT and " +
                "$maximumMeasureCount."
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
            ticksPerQuarterNote = exercise.ticksPerQuarterNote,
            measures = runtimeMeasures,
        )
    }

    companion object {
        const val MIN_TEMPO_BPM = 40
        const val MAX_TEMPO_BPM = 240
        const val TEMPO_STEP_BPM = 5
        const val MIN_MEASURE_COUNT = 1
        const val DEFAULT_MAX_MEASURE_COUNT = 16

        fun fromExercise(exercise: RuntimeExercise): ExercisePlaybackSettings =
            ExercisePlaybackSettings(
                tempoBpm = exercise.tempoBpm.toInt()
                    .coerceIn(MIN_TEMPO_BPM, MAX_TEMPO_BPM),
                measureCount = exercise.measureCount,
                downbeatsOnly = false,
                maximumMeasureCount = maxOf(
                    DEFAULT_MAX_MEASURE_COUNT,
                    exercise.measureCount,
                ),
            )
    }
}
