package com.titaniumharmonics.bad.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ExercisePlaybackSettingsTest {
    private val editableExercise = EditableExercise(
        formatVersion = ExerciseFormat.CURRENT_VERSION,
        id = "playback-settings-test",
        name = "Playback settings test",
        description = "",
        tempoBpm = 100.0,
        timeSignature = TimeSignature(numerator = 4, denominator = 4),
        measureCount = 2,
        ticksPerQuarterNote = 480,
        notes = listOf(
            ExpectedNote(positionTicks = 0, accent = true),
            ExpectedNote(positionTicks = 1_920, accent = true),
        ),
        measureSubdivisions = listOf(
            MeasureSubdivision.QUARTER,
            MeasureSubdivision.EIGHTH,
        ),
    )
    private val exercise = editableExercise.compileForTest()

    @Test
    fun fromExercise_usesLoadedExerciseDefaults() {
        val settings = ExercisePlaybackSettings.fromExercise(exercise)

        assertEquals(100, settings.tempoBpm)
        assertEquals(2, settings.measureCount)
        assertFalse(settings.downbeatsOnly)
        assertEquals(16, settings.maximumMeasureCount)
    }

    @Test
    fun fromExercise_preservesExpandedRuntimeCountsAboveDefaultLimit() {
        val expandedExercise = editableExercise.copy(
            measureCount = 1,
            notes = listOf(ExpectedNote(positionTicks = 0, accent = true)),
            measureSubdivisions = listOf(MeasureSubdivision.QUARTER),
            measureMultipliers = listOf(20),
        ).compileForTest()

        val settings = ExercisePlaybackSettings.fromExercise(expandedExercise)
        val configuredExercise = settings.applyTo(expandedExercise)

        assertEquals(20, settings.measureCount)
        assertEquals(20, settings.maximumMeasureCount)
        assertEquals(20, configuredExercise.measureCount)
    }

    @Test
    fun applyTo_overridesTempoWithoutConfigurableCountIn() {
        val configuredExercise = ExercisePlaybackSettings(
            tempoBpm = 120,
            measureCount = 2,
        ).applyTo(exercise)

        assertEquals(120.0, configuredExercise.tempoBpm, 0.0)
        assertFalse(configuredExercise === exercise)
    }

    @Test
    fun applyTo_truncatesNotesWhenMeasureCountIsReduced() {
        val configuredExercise = ExercisePlaybackSettings(
            tempoBpm = 100,
            measureCount = 1,
        ).applyTo(exercise)

        assertEquals(1, configuredExercise.measureCount)
        assertEquals(listOf(0L), configuredExercise.notes.map { it.positionTicks })
    }

    @Test
    fun applyTo_repeatsPatternWhenMeasureCountIsIncreased() {
        val configuredExercise = ExercisePlaybackSettings(
            tempoBpm = 100,
            measureCount = 5,
        ).applyTo(exercise)

        assertEquals(5, configuredExercise.measureCount)
        assertEquals(
            listOf(0L, 1_920L, 3_840L, 5_760L, 7_680L),
            configuredExercise.notes.map { it.positionTicks },
        )
        assertEquals(listOf(0, 1, 2, 3, 4), configuredExercise.measures.map { it.index })
    }

    @Test
    fun applyTo_repeatsTheEntireExpandedRuntimeSequence() {
        val expandedExercise = editableExercise.copy(
            notes = listOf(
                ExpectedNote(positionTicks = 0),
                ExpectedNote(positionTicks = 1_920 + 240),
            ),
            measureMultipliers = listOf(2, 1),
        ).compileForTest()

        val configuredExercise = ExercisePlaybackSettings(
            tempoBpm = 100,
            measureCount = 5,
        ).applyTo(expandedExercise)

        assertEquals(
            listOf(0L, 1_920L, 4_080L, 5_760L, 7_680L),
            configuredExercise.notes.map(RuntimeExpectedNote::positionTicks),
        )
    }
}
