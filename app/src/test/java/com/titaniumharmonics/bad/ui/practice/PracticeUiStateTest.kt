package com.titaniumharmonics.bad.ui.practice

import com.titaniumharmonics.bad.exercise.EditableExercise
import com.titaniumharmonics.bad.exercise.ExerciseFormat
import com.titaniumharmonics.bad.exercise.ExercisePlaybackSettings
import com.titaniumharmonics.bad.exercise.ExpectedNote
import com.titaniumharmonics.bad.exercise.TimeSignature
import com.titaniumharmonics.bad.exercise.compileForTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PracticeUiStateTest {
    @Test
    fun playbackExercise_retainsTheCompiledExpandedRuntimeSequence() {
        val editable = EditableExercise(
            formatVersion = ExerciseFormat.CURRENT_VERSION,
            id = "practice-expanded",
            name = "Practice expanded",
            description = "",
            tempoBpm = 120.0,
            timeSignature = TimeSignature(numerator = 4, denominator = 4),
            countInMeasures = 1,
            measureCount = 2,
            ticksPerQuarterNote = 480,
            notes = listOf(
                ExpectedNote(positionTicks = 0L),
                ExpectedNote(positionTicks = 1_920L + 240L),
            ),
            measureMultipliers = listOf(3, 2),
        )
        val runtime = editable.compileForTest()
        val state = PracticeUiState(
            exercise = runtime,
            playbackSettings = ExercisePlaybackSettings.fromExercise(runtime),
            phase = PracticePhase.READY,
        )

        val playbackExercise = checkNotNull(state.playbackExercise)

        assertEquals(5, playbackExercise.measureCount)
        assertEquals(9_600L, playbackExercise.totalTicks)
        assertEquals(
            listOf(0L, 1_920L, 3_840L, 6_000L, 7_920L),
            playbackExercise.notes.map { it.positionTicks },
        )
    }
}
