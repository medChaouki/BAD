package com.titaniumharmonics.bad.exercise

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseLibraryItemTest {
    @Test
    fun toLibraryItem_exposesCompactPatternsAndExpandedMeasures() {
        val exercise = EditableExercise(
            formatVersion = ExerciseFormat.CURRENT_VERSION,
            id = "library-counts",
            name = "Library counts",
            description = "",
            tempoBpm = 100.0,
            timeSignature = TimeSignature(numerator = 4, denominator = 4),
            measureCount = 3,
            ticksPerQuarterNote = 480,
            notes = emptyList(),
            measureMultipliers = listOf(3, 2, 3),
        )

        val item = exercise.toLibraryItem(
            documentUri = "content://exercise/library-counts",
            fileName = "library-counts.json",
        )

        assertEquals(exercise.id, item.exerciseId)
        assertEquals(3, item.patternCount)
        assertEquals(8, item.expandedMeasureCount)
    }

    @Test
    fun expandedMeasureCount_preservesImplicitMultiplierOneBehaviour() {
        val exercise = EditableExercise(
            formatVersion = ExerciseFormat.CURRENT_VERSION,
            id = "implicit-multipliers",
            name = "Implicit multipliers",
            description = "",
            tempoBpm = 100.0,
            timeSignature = TimeSignature(numerator = 4, denominator = 4),
            measureCount = 3,
            ticksPerQuarterNote = 480,
            notes = emptyList(),
        )

        assertEquals(3, exercise.expandedMeasureCount)
    }
}
