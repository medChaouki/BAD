package com.titaniumharmonics.bad.audio

import com.titaniumharmonics.bad.exercise.EditableExercise
import com.titaniumharmonics.bad.exercise.ExerciseFormat
import com.titaniumharmonics.bad.exercise.ExpectedNote
import com.titaniumharmonics.bad.exercise.MeasureSubdivision
import com.titaniumharmonics.bad.exercise.TimeSignature
import com.titaniumharmonics.bad.exercise.compileForTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ClickTrackGeneratorTest {
    private val editableExercise = EditableExercise(
        formatVersion = ExerciseFormat.CURRENT_VERSION,
        id = "click-test",
        name = "Click test",
        description = "",
        tempoBpm = 120.0,
        timeSignature = TimeSignature(numerator = 4, denominator = 4),
        countInMeasures = 0,
        measureCount = 1,
        ticksPerQuarterNote = 480,
        notes = listOf(
            ExpectedNote(positionTicks = 0),
            ExpectedNote(positionTicks = 480),
            ExpectedNote(positionTicks = 960),
            ExpectedNote(positionTicks = 1_440),
        ),
    )
    private val exercise = editableExercise.compileForTest()

    @Test
    fun generate_createsTwoSecondsOf48kHzMonoSamples() {
        val samples = ClickTrackGenerator.generate(exercise)

        assertEquals(96_000, samples.size)
    }

    @Test
    fun generate_placesClicksAtSelectedQuarterNotePositions() {
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
    fun generate_placesClicksAtEighthTripletAndSixteenthPositions() {
        val selectedNotes = editableExercise.copy(
            notes = listOf(
                ExpectedNote(positionTicks = 120),
                ExpectedNote(positionTicks = 160),
                ExpectedNote(positionTicks = 240),
            ),
        ).compileForTest()
        val samples = ClickTrackGenerator.generate(selectedNotes)

        assertTrue(peakNearSample(samples, 6_000) > 10_000)
        assertTrue(peakNearSample(samples, 8_000) > 10_000)
        assertTrue(peakNearSample(samples, 12_000) > 10_000)
        assertEquals(0, peakNearSample(samples, 24_000))
    }

    @Test
    fun generate_doesNotClickAtDisabledGridPositions() {
        val exerciseWithDisabledBeat = editableExercise.copy(
            notes = listOf(
                ExpectedNote(positionTicks = 0),
                ExpectedNote(positionTicks = 960),
            ),
        ).compileForTest()
        val samples = ClickTrackGenerator.generate(exerciseWithDisabledBeat)

        assertTrue(peakNearSample(samples, 0) > 10_000)
        assertEquals(0, peakNearSample(samples, 24_000))
        assertTrue(peakNearSample(samples, 48_000) > 10_000)
    }

    @Test
    fun generate_downbeatsOnlyKeepsFullCountInAndMutesOtherExerciseBeats() {
        val exerciseWithCountIn = editableExercise.copy(
            countInMeasures = 1,
        ).compileForTest()
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
        val exerciseWithEmptySecondMeasure = editableExercise.copy(
            countInMeasures = 1,
            measureCount = 2,
            notes = listOf(ExpectedNote(positionTicks = 0)),
            measureSubdivisions = List(2) { MeasureSubdivision.QUARTER },
        ).compileForTest()
        val samples = ClickTrackGenerator.generate(exerciseWithEmptySecondMeasure)
        val samplesPerBeat = 24_000

        repeat(4) { beat ->
            assertTrue(
                "No click detected on count-in beat ${beat + 1}",
                peakNearBeat(samples, beat, samplesPerBeat) > 10_000,
            )
        }
        assertTrue(peakNearBeat(samples, beat = 4, samplesPerBeat) > 10_000)
        repeat(7) { silentExerciseBeatOffset ->
            assertEquals(
                0,
                peakNearBeat(
                    samples = samples,
                    beat = 5 + silentExerciseBeatOffset,
                    samplesPerBeat = samplesPerBeat,
                ),
            )
        }
    }

    @Test
    fun generateCountIn_containsOnlyTheConfiguredAllBeatsCountIn() {
        val exerciseWithCountIn = editableExercise.copy(
            countInMeasures = 1,
        ).compileForTest()
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

    @Test
    fun generate_downbeatsOnlyKeepsMeasureSilentWhenItsStartNoteIsDisabled() {
        val exerciseWithoutMeasureStart = editableExercise.copy(
            notes = listOf(ExpectedNote(positionTicks = 480)),
        ).compileForTest()
        val samples = ClickTrackGenerator.generate(
            exercise = exerciseWithoutMeasureStart,
            downbeatsOnly = true,
        )

        assertEquals(0, samples.maxOf { abs(it.toInt()) })
    }

    @Test
    fun generateCountIn_alwaysUsesQuarterNotesForCompoundTimeSignatures() {
        val sixEightExercise = editableExercise.copy(
            timeSignature = TimeSignature(numerator = 6, denominator = 8),
            countInMeasures = 1,
            notes = listOf(ExpectedNote(positionTicks = 0)),
        ).compileForTest()
        val samples = ClickTrackGenerator.generateCountIn(sixEightExercise)

        assertEquals(72_000, samples.size)
        assertTrue(peakNearSample(samples, 0) > 10_000)
        assertTrue(peakNearSample(samples, 24_000) > 10_000)
        assertTrue(peakNearSample(samples, 48_000) > 10_000)
        assertEquals(0, peakNearSample(samples, 12_000))
        assertEquals(0, peakNearSample(samples, 36_000))
    }

    @Test
    fun countInAndExerciseClicks_useDifferentSoundProfiles() {
        val countInSamples = ClickTrackGenerator.generateCountIn(
            editableExercise.copy(countInMeasures = 1).compileForTest(),
        )
        val exerciseSamples = ClickTrackGenerator.generate(exercise)

        assertFalse(
            countInSamples.copyOfRange(0, 1_200).contentEquals(
                exerciseSamples.copyOfRange(0, 1_200),
            ),
        )
    }

    @Test
    fun generate_accentsMeasureStartNotes() {
        val samples = ClickTrackGenerator.generate(exercise)

        assertTrue(
            peakNearSample(samples, 0) >
                peakNearSample(samples, 24_000),
        )
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

    private fun peakNearSample(
        samples: ShortArray,
        startSample: Int,
    ): Int = samples
        .sliceArray(startSample until startSample + 1_200)
        .maxOf { abs(it.toInt()) }
}
