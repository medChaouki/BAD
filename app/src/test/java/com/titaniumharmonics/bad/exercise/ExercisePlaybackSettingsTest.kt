package com.titaniumharmonics.bad.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExercisePlaybackSettingsTest {
    private val exercise = Exercise(
        formatVersion = ExerciseFormat.CURRENT_VERSION,
        id = "playback-settings-test",
        name = "Playback settings test",
        description = "",
        tempoBpm = 100.0,
        timeSignature = TimeSignature(numerator = 4, denominator = 4),
        countInMeasures = 1,
        measureCount = 2,
        ticksPerQuarterNote = 480,
        notes = listOf(
            ExpectedNote(positionTicks = 0, accent = true),
            ExpectedNote(positionTicks = 1_920, accent = true),
        ),
    )

    @Test
    fun fromExercise_usesLoadedExerciseDefaults() {
        val settings = ExercisePlaybackSettings.fromExercise(exercise)

        assertEquals(100, settings.tempoBpm)
        assertTrue(settings.countInEnabled)
        assertEquals(2, settings.measureCount)
        assertFalse(settings.downbeatsOnly)
    }

    @Test
    fun applyTo_overridesTempoAndDisablesCountIn() {
        val configuredExercise = ExercisePlaybackSettings(
            tempoBpm = 120,
            countInEnabled = false,
            measureCount = 2,
        ).applyTo(exercise)

        assertEquals(120.0, configuredExercise.tempoBpm, 0.0)
        assertEquals(0, configuredExercise.countInMeasures)
        assertFalse(configuredExercise === exercise)
    }

    @Test
    fun applyTo_truncatesNotesWhenMeasureCountIsReduced() {
        val configuredExercise = ExercisePlaybackSettings(
            tempoBpm = 100,
            countInEnabled = true,
            measureCount = 1,
        ).applyTo(exercise)

        assertEquals(1, configuredExercise.measureCount)
        assertEquals(listOf(0L), configuredExercise.notes.map { it.positionTicks })
    }

    @Test
    fun applyTo_repeatsPatternWhenMeasureCountIsIncreased() {
        val configuredExercise = ExercisePlaybackSettings(
            tempoBpm = 100,
            countInEnabled = true,
            measureCount = 5,
        ).applyTo(exercise)

        assertEquals(5, configuredExercise.measureCount)
        assertEquals(
            listOf(0L, 1_920L, 3_840L, 5_760L, 7_680L),
            configuredExercise.notes.map { it.positionTicks },
        )
    }
}
