package com.titaniumharmonics.bad.audio

import com.titaniumharmonics.bad.exercise.Exercise
import com.titaniumharmonics.bad.exercise.ExerciseFormat
import com.titaniumharmonics.bad.exercise.ExpectedNote
import com.titaniumharmonics.bad.exercise.MeasureSubdivision
import com.titaniumharmonics.bad.exercise.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ClickTrackGeneratorTest {
    private val exercise = Exercise(
        formatVersion = ExerciseFormat.CURRENT_VERSION,
        id = "click-test",
        name = "Click test",
        description = "",
        tempoBpm = 120.0,
        timeSignature = TimeSignature(numerator = 4, denominator = 4),
        countInMeasures = 0,
        measureCount = 1,
        ticksPerQuarterNote = 480,
        notes = listOf(ExpectedNote(positionTicks = 0)),
    )

    @Test
    fun generate_createsTwoSecondsOf48kHzMonoSamples() {
        val samples = ClickTrackGenerator.generate(exercise)

        assertEquals(96_000, samples.size)
    }

    @Test
    fun generate_placesClicksAtQuarterNoteBoundaries() {
        val samples = ClickTrackGenerator.generate(exercise)
        val samplesPerBeat = 24_000

        repeat(4) { beat ->
            val clickPeak = samples
                .sliceArray(beat * samplesPerBeat until beat * samplesPerBeat + 1_200)
                .maxOf { abs(it.toInt()) }
            assertTrue("No click detected on beat ${beat + 1}", clickPeak > 10_000)
        }

        val betweenBeatsPeak = samples
            .sliceArray(12_000 until 13_200)
            .maxOf { abs(it.toInt()) }
        assertEquals(0, betweenBeatsPeak)
    }

    @Test
    fun generate_downbeatsOnlyKeepsFullCountInAndMutesOtherExerciseBeats() {
        val exerciseWithCountIn = exercise.copy(countInMeasures = 1)
        val samples = ClickTrackGenerator.generate(
            exercise = exerciseWithCountIn,
            downbeatsOnly = true,
        )
        val samplesPerBeat = 24_000

        repeat(5) { beat ->
            assertTrue(
                "No click detected on beat ${beat + 1}",
                peakNearBeat(samples, beat, samplesPerBeat) > 10_000,
            )
        }
        repeat(3) { exerciseBeatOffset ->
            val beat = 5 + exerciseBeatOffset
            assertEquals(0, peakNearBeat(samples, beat, samplesPerBeat))
        }
    }

    @Test
    fun generate_mutesExerciseBeatsInsideEmptyMeasures() {
        val exerciseWithEmptySecondMeasure = exercise.copy(
            countInMeasures = 1,
            measureCount = 2,
            notes = listOf(ExpectedNote(positionTicks = 0)),
            measureSubdivisions = List(2) { MeasureSubdivision.QUARTER },
        )
        val samples = ClickTrackGenerator.generate(exerciseWithEmptySecondMeasure)
        val samplesPerBeat = 24_000

        repeat(8) { beat ->
            assertTrue(
                "No click detected on count-in or populated-measure beat ${beat + 1}",
                peakNearBeat(samples, beat, samplesPerBeat) > 10_000,
            )
        }
        repeat(4) { emptyMeasureBeatOffset ->
            assertEquals(
                0,
                peakNearBeat(
                    samples = samples,
                    beat = 8 + emptyMeasureBeatOffset,
                    samplesPerBeat = samplesPerBeat,
                ),
            )
        }
    }

    @Test
    fun generateCountIn_containsOnlyTheConfiguredAllBeatsCountIn() {
        val exerciseWithCountIn = exercise.copy(countInMeasures = 1)
        val samples = ClickTrackGenerator.generateCountIn(exerciseWithCountIn)
        val samplesPerBeat = 24_000

        assertEquals(96_000, samples.size)
        repeat(4) { beat ->
            assertTrue(
                "No count-in click detected on beat ${beat + 1}",
                peakNearBeat(samples, beat, samplesPerBeat) > 10_000,
            )
        }
    }

    private fun peakNearBeat(
        samples: ShortArray,
        beat: Int,
        samplesPerBeat: Int,
    ): Int {
        val beatStart = beat * samplesPerBeat
        return samples
            .sliceArray(beatStart until beatStart + 1_200)
            .maxOf { abs(it.toInt()) }
    }
}
