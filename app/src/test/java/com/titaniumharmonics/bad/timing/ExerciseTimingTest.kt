package com.titaniumharmonics.bad.timing

import com.titaniumharmonics.bad.exercise.Exercise
import com.titaniumharmonics.bad.exercise.ExerciseFormat
import com.titaniumharmonics.bad.exercise.ExpectedNote
import com.titaniumharmonics.bad.exercise.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseTimingTest {
    private val exercise = Exercise(
        formatVersion = ExerciseFormat.CURRENT_VERSION,
        id = "timing-test",
        name = "Timing test",
        description = "",
        tempoBpm = 100.0,
        timeSignature = TimeSignature(numerator = 4, denominator = 4),
        countInMeasures = 1,
        measureCount = 1,
        ticksPerQuarterNote = 480,
        notes = listOf(ExpectedNote(positionTicks = 0)),
    )

    @Test
    fun ticksToNanos_convertsOneQuarterNoteAt100Bpm() {
        val timing = ExerciseTiming(exercise)

        assertEquals(600_000_000L, timing.ticksToNanos(480))
    }

    @Test
    fun durations_includeCountInAndExerciseMeasures() {
        val timing = ExerciseTiming(exercise)

        assertEquals(600_000_000L, timing.beatDurationNanos)
        assertEquals(600_000_000L, timing.quarterNoteDurationNanos)
        assertEquals(1_920L, timing.measureDurationTicks)
        assertEquals(150_000_000L, timing.beatHighlightDurationNanos)
        assertEquals(2_400_000_000L, timing.countInDurationNanos)
        assertEquals(2_400_000_000L, timing.exerciseDurationNanos)
        assertEquals(4_800_000_000L, timing.totalDurationNanos)
    }

    @Test
    fun beatHighlight_returnsCurrentBeatTimeForFirstQuarterOfEveryBeat() {
        val timing = ExerciseTiming(exercise)

        assertEquals(null, timing.highlightedBeatTimeNanos(-1L))
        assertEquals(0L, timing.highlightedBeatTimeNanos(0L))
        assertEquals(0L, timing.highlightedBeatTimeNanos(149_999_999L))
        assertEquals(null, timing.highlightedBeatTimeNanos(150_000_000L))
        assertEquals(null, timing.highlightedBeatTimeNanos(599_999_999L))
        assertEquals(600_000_000L, timing.highlightedBeatTimeNanos(600_000_000L))
        assertEquals(
            null,
            timing.highlightedBeatTimeNanos(timing.exerciseDurationNanos),
        )
    }

    @Test
    fun progress_transitionsAtCountInAndExerciseBoundaries() {
        val calculator = SessionProgressCalculator(ExerciseTiming(exercise))

        assertEquals(
            PlaybackPhase.COUNTING_IN,
            calculator.calculate(2_399_999_999L).phase,
        )
        assertEquals(
            PlaybackPhase.RUNNING,
            calculator.calculate(2_400_000_000L).phase,
        )
        assertEquals(
            PlaybackPhase.COMPLETED,
            calculator.calculate(4_800_000_000L).phase,
        )
    }
}
