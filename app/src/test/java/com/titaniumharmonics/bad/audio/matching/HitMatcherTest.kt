package com.titaniumharmonics.bad.audio.matching

import com.titaniumharmonics.bad.audio.detection.DetectedHit
import com.titaniumharmonics.bad.exercise.ExercisePlaybackSettings
import com.titaniumharmonics.bad.exercise.EditableExercise
import com.titaniumharmonics.bad.exercise.ExerciseFormat
import com.titaniumharmonics.bad.exercise.ExpectedNote
import com.titaniumharmonics.bad.exercise.MeasureSubdivision
import com.titaniumharmonics.bad.exercise.RuntimeExercise
import com.titaniumharmonics.bad.exercise.RuntimeExpectedNote
import com.titaniumharmonics.bad.exercise.RuntimeMeasure
import com.titaniumharmonics.bad.exercise.TimeSignature
import com.titaniumharmonics.bad.exercise.compileForTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HitMatcherTest {
    @Test
    fun perfectOneToOneAndExactHitAreOnTime() {
        val exercise = exercise(listOf(listOf(0L, 480L, 960L)))
        val result = match(exercise, hitsAtExpected(exercise))

        assertEquals(3, result.matchedExpectedNotes.size)
        assertEquals(0, result.unmatchedExpectedNotes.size)
        assertEquals(0, result.extraDetectedHits.size)
        assertTrue(result.expectedNoteJudgements.all { it.judgement == HitJudgement.ON_TIME })
        assertTrue(result.expectedNoteJudgements.all { it.timingErrorSamples == 0L })
    }

    @Test
    fun earlyOnTimeLateAndInclusiveBoundariesAreClassified() {
        val exercise = exercise(List(7) { listOf(480L) })
        val expected = timeline(exercise)
        val errors = listOf(-120L, -40L, -39L, 0L, 40L, 41L, 120L)
        val result = match(
            exercise,
            expected.zip(errors).mapIndexed { index, (note, error) ->
                hit(note.exerciseSample + error, rawMarker = index.toLong())
            },
        )

        assertEquals(
            listOf(
                HitJudgement.EARLY,
                HitJudgement.ON_TIME,
                HitJudgement.ON_TIME,
                HitJudgement.ON_TIME,
                HitJudgement.ON_TIME,
                HitJudgement.LATE,
                HitJudgement.LATE,
            ),
            result.expectedNoteJudgements.map(ExpectedNoteJudgement::judgement),
        )
    }

    @Test
    fun hitsOutsideMaximumWindowBecomeMissedAndExtra() {
        val exercise = exercise(listOf(listOf(480L)))
        val expected = timeline(exercise).single().exerciseSample
        val result = match(exercise, listOf(hit(expected - 121L), hit(expected + 121L)))

        assertEquals(HitJudgement.MISSED, result.expectedNoteJudgements.single().judgement)
        assertNull(result.expectedNoteJudgements.single().detectedHit)
        assertEquals(2, result.extraDetectedHits.size)
    }

    @Test
    fun noHitsMissesEveryNoteAndNoNotesMakesEveryHitExtra() {
        val withNotes = match(exercise(listOf(listOf(0L, 480L))), emptyList())
        assertEquals(2, withNotes.unmatchedExpectedNotes.size)

        val empty = match(exercise(listOf(emptyList())), listOf(hit(100L), hit(200L)))
        assertEquals(0, empty.expectedNoteJudgements.size)
        assertEquals(2, empty.extraDetectedHits.size)
    }

    @Test
    fun dynamicProgrammingMakesOneToOneChoicesForAmbiguousHits() {
        val twoNotes = exercise(listOf(listOf(480L, 576L)))
        val between = match(twoNotes, listOf(hit(1_120L)))
        assertEquals(1, between.matchedExpectedNotes.size)
        assertEquals(1, between.unmatchedExpectedNotes.size)
        assertEquals(1, between.matchedExpectedNotes.single().expected.index)

        val oneNote = exercise(listOf(listOf(480L)))
        val twoHits = match(oneNote, listOf(hit(980L), hit(1_010L)))
        assertEquals(1_010L, twoHits.matchedExpectedNotes.single().detectedHit?.calibratedExerciseSample)
        assertEquals(listOf(980L), twoHits.extraDetectedHits.map { it.calibratedExerciseSample })
    }

    @Test
    fun stableInputOrderBreaksAnOtherwiseExactTie() {
        val exercise = exercise(listOf(listOf(480L)))
        val first = hit(990L, rawMarker = 1L)
        val second = hit(1_010L, rawMarker = 2L)
        val firstResult = match(exercise, listOf(first, second))
        val repeatedResult = match(exercise, listOf(first, second))

        assertSame(first, firstResult.matchedExpectedNotes.single().detectedHit)
        assertEquals(
            firstResult.expectedNoteJudgements,
            repeatedResult.expectedNoteJudgements,
        )
    }

    @Test
    fun confidenceGateIsInclusiveAndRejectedHitsNeverMatch() {
        val exercise = exercise(listOf(listOf(480L)))
        val expected = timeline(exercise).single().exerciseSample
        val low = hit(expected, confidence = 0.299)
        val boundary = hit(expected + 20L, confidence = 0.30)
        val result = match(exercise, listOf(low, boundary))

        assertEquals(listOf(low), result.rejectedLowConfidenceHits)
        assertEquals(listOf(boundary), result.acceptedHits)
        assertSame(boundary, result.matchedExpectedNotes.single().detectedHit)
    }

    @Test
    fun calibratedTimingIsUsedWhileRawTimingRemainsUntouched() {
        val exercise = exercise(listOf(listOf(480L)))
        val expected = timeline(exercise).single().exerciseSample
        val hit = hit(expected, rawMarker = expected + 500L)
        val result = match(exercise, listOf(hit))

        assertEquals(0L, result.matchedExpectedNotes.single().timingErrorSamples)
        assertEquals(expected + 500L, result.matchedExpectedNotes.single().detectedHit?.rawExerciseSample)
    }

    @Test
    fun tripletsAndFastSixteenthsRemainDistinct() {
        val exercise = exercise(
            listOf(
                listOf(0L, 160L, 320L),
                listOf(0L, 120L, 240L, 360L),
            ),
            tempoBpm = 240.0,
        )
        val expected = timeline(exercise)
        val result = match(exercise, expected.map { hit(it.exerciseSample) })

        assertEquals(expected.size, result.matchedExpectedNotes.size)
        assertEquals(expected.map { it.exerciseSample }, result.matchedExpectedNotes.map {
            it.detectedHit!!.calibratedExerciseSample
        })
    }

    @Test
    fun expandedPatternsRepeatedPlaybackAndTruncationUseRuntimeSnapshotExactly() {
        val expanded = exercise(
            listOf(
                listOf(0L),
                listOf(240L),
                listOf(240L),
            ),
        )
        val repeated = ExercisePlaybackSettings(tempoBpm = 60, measureCount = 5).applyTo(expanded)
        val truncated = ExercisePlaybackSettings(tempoBpm = 60, measureCount = 2).applyTo(expanded)

        assertEquals(5, match(repeated, hitsAtExpected(repeated)).matchedExpectedNotes.size)
        assertEquals(2, match(truncated, hitsAtExpected(truncated)).matchedExpectedNotes.size)
        assertEquals(listOf(0, 1), timeline(truncated).map { it.note.measureIndex })
    }

    @Test
    fun persistedPatternMultipliersCompileIntoEveryExpectedRuntimeNote() {
        val expanded = EditableExercise(
            formatVersion = ExerciseFormat.CURRENT_VERSION,
            id = "multiplier-match",
            name = "Multiplier match",
            description = "",
            tempoBpm = 60.0,
            timeSignature = TimeSignature(4, 4),
            measureCount = 2,
            ticksPerQuarterNote = 480,
            notes = listOf(ExpectedNote(0L), ExpectedNote(1_920L + 240L)),
            measureSubdivisions = listOf(
                MeasureSubdivision.QUARTER,
                MeasureSubdivision.EIGHTH,
            ),
            measureMultipliers = listOf(3, 2),
        ).compileForTest()

        val result = match(expanded, hitsAtExpected(expanded))

        assertEquals(5, expanded.measureCount)
        assertEquals(5, result.matchedExpectedNotes.size)
        assertEquals(listOf(0, 1, 2, 3, 4), result.matchedExpectedNotes.map {
            it.expected.note.measureIndex
        })
    }

    @Test
    fun emptyMeasuresProduceNoSyntheticExpectedNotes() {
        val exercise = exercise(listOf(emptyList(), listOf(0L), emptyList()))
        val expected = timeline(exercise)
        assertEquals(1, expected.size)
        assertEquals(1, expected.single().note.measureIndex)
    }

    @Test
    fun configurationValidationAndMillisecondRoundingAreExplicit() {
        assertThrows(IllegalArgumentException::class.java) {
            JudgementConfiguration(onTimeBeforeMillis = 121.0, maximumEarlyMillis = 120.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            JudgementConfiguration(minimumDetectedHitConfidence = Double.NaN)
        }
        assertEquals(
            44L,
            RuntimeExerciseSampleTimeline.millisecondsToSamples(1.0, 44_100),
        )
    }

    private fun match(
        exercise: RuntimeExercise,
        hits: List<DetectedHit>,
        configuration: JudgementConfiguration = JudgementConfiguration.DEFAULT,
    ): HitMatchingResult = HitMatcher.match(exercise, hits, SAMPLE_RATE, configuration)

    private fun timeline(exercise: RuntimeExercise): List<ExpectedNoteTiming> =
        RuntimeExerciseSampleTimeline.expectedNotes(exercise, SAMPLE_RATE)

    private fun hitsAtExpected(exercise: RuntimeExercise): List<DetectedHit> =
        timeline(exercise).map { hit(it.exerciseSample) }

    private fun exercise(
        measureNotes: List<List<Long>>,
        tempoBpm: Double = 60.0,
    ): RuntimeExercise {
        val duration = 1_920L
        return RuntimeExercise(
            id = "matching-test",
            name = "Matching test",
            description = "",
            tempoBpm = tempoBpm,
            timeSignature = TimeSignature(4, 4),
            ticksPerQuarterNote = 480,
            measures = measureNotes.mapIndexed { measureIndex, ticks ->
                val start = duration * measureIndex
                RuntimeMeasure(
                    index = measureIndex,
                    startTick = start,
                    durationTicks = duration,
                    notes = ticks.map { local ->
                        RuntimeExpectedNote(measureIndex, local, start + local)
                    },
                )
            },
        )
    }

    private fun hit(
        calibratedSample: Long,
        confidence: Double = 0.9,
        rawMarker: Long = calibratedSample,
    ) = DetectedHit(
        rawExerciseSample = rawMarker,
        rawExerciseTimeMillis = rawMarker.toDouble(),
        calibratedExerciseSample = calibratedSample,
        calibratedExerciseTimeMillis = calibratedSample.toDouble(),
        onsetFrame = 0,
        peakFrame = 1,
        peakExerciseSample = calibratedSample,
        peakTimeMillis = calibratedSample.toDouble(),
        peakAmplitude = 0.8f,
        frameLevel = 0.7f,
        envelope = 0.6f,
        noiseFloor = 0.01f,
        signalToNoiseRatio = 10.0,
        confidence = confidence,
        metronomeBandRatio = 0.0,
        broadbandResidualEnergy = 1.0,
        spectralBandwidthHz = 1_000.0,
        spectralCentroidHz = 1_000.0,
        calibrationApplied = true,
    )

    private companion object {
        const val SAMPLE_RATE = 1_000
    }
}
