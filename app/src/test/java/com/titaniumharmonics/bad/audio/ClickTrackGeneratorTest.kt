package com.titaniumharmonics.bad.audio

import com.titaniumharmonics.bad.exercise.Exercise
import com.titaniumharmonics.bad.exercise.ExerciseFormat
import com.titaniumharmonics.bad.exercise.ExpectedNote
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
}
