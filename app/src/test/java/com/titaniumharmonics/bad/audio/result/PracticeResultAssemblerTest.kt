package com.titaniumharmonics.bad.audio.result

import com.titaniumharmonics.bad.audio.analysis.ImmutableFloatSeries
import com.titaniumharmonics.bad.audio.analysis.ImmutableLongSeries
import com.titaniumharmonics.bad.audio.calibration.CalibrationConfidence
import com.titaniumharmonics.bad.audio.calibration.TimingCalibration
import com.titaniumharmonics.bad.audio.detection.DetectedHit
import com.titaniumharmonics.bad.audio.detection.HitDetectionConfiguration
import com.titaniumharmonics.bad.audio.detection.HitDetectionResult
import com.titaniumharmonics.bad.audio.detection.SessionDetectionSnapshot
import com.titaniumharmonics.bad.audio.matching.HitJudgement
import com.titaniumharmonics.bad.audio.matching.HitMatcher
import com.titaniumharmonics.bad.audio.matching.JudgementConfiguration
import com.titaniumharmonics.bad.audio.matching.RuntimeExerciseSampleTimeline
import com.titaniumharmonics.bad.audio.matching.SessionJudgementSnapshot
import com.titaniumharmonics.bad.audio.metronome.MetronomeConfiguration
import com.titaniumharmonics.bad.audio.metronome.SessionMetronomeSnapshot
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeResultAssemblerTest {
    @Test
    fun perfectExerciseProducesCompleteImmutableResult() {
        val exercise = exercise(listOf(listOf(0L, 480L, 960L)))
        val hits = expectedSamples(exercise).mapIndexed { index, sample ->
            hit(sample, amplitude = 0.2f + index * 0.3f)
        }
        val result = assemble(exercise, hits)

        assertSame(exercise, result.runtimeExercise)
        assertEquals(PracticeResult.CURRENT_SCHEMA_VERSION, result.schemaVersion)
        assertEquals(exercise.id, result.exerciseId)
        assertEquals(exercise.name, result.exerciseName)
        assertEquals(3, result.totalExpectedNotes)
        assertEquals(3, result.onTimeCount)
        assertEquals(1.0, result.accuracy, 0.0)
        assertEquals(1.0, result.hitRate, 0.0)
        assertEquals(0.0, result.meanAbsoluteTimingErrorMillis!!, 0.0)
        assertTrue(result.judgedNotes.all { it.matchedHit != null })
    }

    @Test
    fun mixedJudgementsProduceDefinedCountsRatesAndTimingStatistics() {
        val exercise = exercise(listOf(listOf(480L, 960L, 1_440L), listOf(0L)))
        val expected = expectedSamples(exercise)
        val hits = listOf(
            hit(expected[0] - 60L, 0.2f, rawOffset = 15L),
            hit(expected[1], 0.4f),
            hit(expected[2] + 60L, 0.8f),
            hit(5_000L, 0.5f),
        )
        val result = assemble(exercise, hits)

        assertEquals(1, result.earlyCount)
        assertEquals(1, result.onTimeCount)
        assertEquals(1, result.lateCount)
        assertEquals(1, result.missedCount)
        assertEquals(1, result.extraCount)
        assertEquals(0.25, result.accuracy, 0.0)
        assertEquals(0.75, result.hitRate, 0.0)
        assertEquals(0.25, result.missedRate, 0.0)
        assertEquals(0.25, result.extraHitRate, 0.0)
        assertEquals(40.0, result.meanAbsoluteTimingErrorMillis!!, 0.0001)
        assertEquals(0.0, result.signedMeanTimingErrorMillis!!, 0.0001)
        assertEquals(60.0, result.medianAbsoluteTimingErrorMillis!!, 0.0001)
        assertEquals(48.9897948557, result.timingErrorStandardDeviationMillis!!, 0.0001)
        assertNull(result.judgedNotes.last().timingErrorMillis)
        assertNull(result.judgedNotes.last().relativeIntensity)
    }

    @Test
    fun rawCalibratedTimingMetadataAndAccentArePreserved() {
        val exercise = exercise(listOf(listOf(480L)), accent = true)
        val expected = expectedSamples(exercise).single()
        val hit = hit(expected + 20L, 0.7f, rawOffset = 70L)
        val calibration = TimingCalibration(
            offsetSamples = 50L,
            sampleRateHz = SAMPLE_RATE,
            confidence = CalibrationConfidence.HIGH,
            expectedClickCount = 8,
            matchedClickCount = 8,
            offsetSpreadSamples = 2L,
            calibratedAtEpochMillis = 123L,
            algorithmVersion = 1,
        )
        val result = assemble(exercise, listOf(hit), calibration = calibration)
        val judged = result.judgedNotes.single()

        assertEquals(expected + 90L, judged.rawDetectedSample)
        assertEquals(expected + 20L, judged.calibratedDetectedSample)
        assertEquals(20L, judged.timingErrorSamples)
        assertEquals(hit.confidence, judged.detectionConfidence!!, 0.0)
        assertTrue(judged.accent)
        assertEquals(calibration, result.timingCalibration)
        assertEquals(50L, result.calibrationOffsetSamples)
        assertTrue(result.calibrationApplied)
    }

    @Test
    fun allMissedAndZeroMatchedUseAbsentTimingAndIntensityStatistics() {
        val result = assemble(exercise(listOf(listOf(0L, 480L))), emptyList())

        assertEquals(2, result.missedCount)
        assertEquals(1.0, result.missedRate, 0.0)
        assertEquals(0.0, result.accuracy, 0.0)
        assertEquals(0.0, result.hitRate, 0.0)
        assertNull(result.meanAbsoluteTimingErrorMillis)
        assertNull(result.signedMeanTimingErrorMillis)
        assertNull(result.medianAbsoluteTimingErrorMillis)
        assertNull(result.timingErrorStandardDeviationMillis)
        assertNull(result.meanRelativeIntensity)
    }

    @Test
    fun zeroExpectedNotesAndAllExtrasAreSafeAndFinite() {
        val result = assemble(
            exercise(listOf(emptyList())),
            listOf(hit(100L, 0.2f), hit(200L, 0.8f)),
        )

        assertEquals(0, result.totalExpectedNotes)
        assertEquals(2, result.extraCount)
        assertEquals(0.0, result.accuracy, 0.0)
        assertEquals(0.0, result.hitRate, 0.0)
        assertEquals(0.0, result.missedRate, 0.0)
        assertEquals(1.0, result.extraHitRate, 0.0)
        assertTrue(listOf(result.accuracy, result.hitRate, result.extraHitRate).all(Double::isFinite))
    }

    @Test
    fun intensityUsesMatchedAndExtraAcceptedHitsTogether() {
        val exercise = exercise(listOf(listOf(480L)))
        val expected = expectedSamples(exercise).single()
        val result = assemble(
            exercise,
            listOf(hit(expected, 0.2f), hit(expected + 500L, 0.8f)),
        )

        assertEquals(0.0, result.judgedNotes.single().relativeIntensity!!, 0.0)
        assertEquals(1.0, result.extraHits.single().relativeIntensity, 0.0)
        assertEquals(0.5, result.meanRelativeIntensity!!, 0.0)
        assertEquals(0.0, result.minimumRelativeIntensity!!, 0.0)
        assertEquals(1.0, result.maximumRelativeIntensity!!, 0.0)
    }

    @Test
    fun oneHitAndEqualAmplitudeRunsNormalizeEveryAcceptedHitToOne() {
        val exercise = exercise(listOf(listOf(480L)))
        val expected = expectedSamples(exercise).single()
        assertEquals(
            1.0,
            assemble(exercise, listOf(hit(expected, 0.4f)))
                .judgedNotes.single().relativeIntensity!!,
            0.0,
        )
        val equal = assemble(
            exercise,
            listOf(hit(expected, 0.4f), hit(expected + 500L, 0.4f)),
        )
        assertTrue(
            (equal.judgedNotes.mapNotNull { it.relativeIntensity } +
                equal.extraHits.map { it.relativeIntensity }).all { it == 1.0 },
        )
    }

    @Test
    fun rejectedLowConfidenceHitsDoNotBecomeExtrasOrAffectIntensity() {
        val exercise = exercise(listOf(listOf(480L)))
        val expected = expectedSamples(exercise).single()
        val low = hit(expected + 500L, amplitude = 1.0f, confidence = 0.1)
        val accepted = hit(expected, amplitude = 0.2f)
        val result = assemble(exercise, listOf(low, accepted))

        assertEquals(0, result.extraCount)
        assertEquals(1.0, result.judgedNotes.single().relativeIntensity!!, 0.0)
    }

    @Test
    fun tripletsSixteenthsAndRepeatedTruncatedRuntimeSnapshotsRemainExact() {
        val base = exercise(
            listOf(
                listOf(0L, 160L, 320L),
                listOf(0L, 120L, 240L, 360L),
            ),
            tempoBpm = 120.0,
        )
        val repeated = ExercisePlaybackSettings(120, 5).applyTo(base)
        val truncated = ExercisePlaybackSettings(120, 1).applyTo(base)
        val repeatedResult = assemble(repeated, expectedSamples(repeated).map(::hit))
        val truncatedResult = assemble(truncated, expectedSamples(truncated).map(::hit))

        assertSame(repeated, repeatedResult.runtimeExercise)
        assertEquals(repeated.notes.size, repeatedResult.onTimeCount)
        assertEquals(truncated.notes.size, truncatedResult.onTimeCount)
        assertEquals(listOf(0L, 167L, 333L), expectedSamples(truncated))
    }

    @Test
    fun compiledPatternMultipliersRemainExpandedInTheResultSnapshot() {
        val exercise = EditableExercise(
            formatVersion = ExerciseFormat.CURRENT_VERSION,
            id = "result-multiplier",
            name = "Result multiplier",
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

        val result = assemble(exercise, expectedSamples(exercise).map(::hit))

        assertSame(exercise, result.runtimeExercise)
        assertEquals(5, result.runtimeExercise.measureCount)
        assertEquals(5, result.onTimeCount)
        assertEquals(listOf(0, 1, 2, 3, 4), result.judgedNotes.map(JudgedNote::measureIndex))
    }

    @Test
    fun completedResultRetainsItsJudgementSnapshotAfterLaterConfigurationChanges() {
        val exercise = exercise(listOf(listOf(480L)))
        val original = JudgementConfiguration.DEFAULT.copy(onTimeBeforeMillis = 25.0)
        val result = assemble(exercise, expectedSamples(exercise).map(::hit), configuration = original)
        val later = original.copy(onTimeBeforeMillis = 35.0)

        assertEquals(25.0, result.judgementSnapshot.configuration.onTimeBeforeMillis, 0.0)
        assertEquals(35.0, later.onTimeBeforeMillis, 0.0)
    }

    private fun assemble(
        exercise: RuntimeExercise,
        hits: List<DetectedHit>,
        calibration: TimingCalibration? = null,
        configuration: JudgementConfiguration = JudgementConfiguration.DEFAULT,
    ): PracticeResult {
        val matching = HitMatcher.match(exercise, hits, SAMPLE_RATE, configuration)
        val detectorConfiguration = HitDetectionConfiguration.DEFAULT
        val detectionSnapshot = SessionDetectionSnapshot(detectorConfiguration, calibration)
        return PracticeResultAssembler.assemble(
            runtimeExercise = exercise,
            matchingResult = matching,
            hitDetectionResult = detectionResult(
                hits = hits,
                configuration = detectorConfiguration,
                calibrationOffset = calibration?.offsetSamples ?: 0L,
                calibrationApplied = calibration != null,
            ),
            judgementSnapshot = SessionJudgementSnapshot(configuration),
            detectionSnapshot = detectionSnapshot,
            metronomeSnapshot = SessionMetronomeSnapshot(MetronomeConfiguration.DEFAULT),
        )
    }

    private fun detectionResult(
        hits: List<DetectedHit>,
        configuration: HitDetectionConfiguration,
        calibrationOffset: Long,
        calibrationApplied: Boolean,
    ) = HitDetectionResult(
        hits = hits,
        rejectedMetronomeCandidates = emptyList(),
        otherRejectedCandidates = emptyList(),
        candidates = emptyList(),
        adaptiveThreshold = ImmutableFloatSeries.copyOf(floatArrayOf()),
        expectedExerciseSamples = ImmutableLongSeries.copyOf(longArrayOf()),
        configuration = configuration,
        calibrationOffsetSamples = calibrationOffset,
        calibrationApplied = calibrationApplied,
    )

    private fun expectedSamples(exercise: RuntimeExercise): List<Long> =
        RuntimeExerciseSampleTimeline.expectedSamples(exercise, SAMPLE_RATE).toList()

    private fun exercise(
        measureNotes: List<List<Long>>,
        tempoBpm: Double = 60.0,
        accent: Boolean = false,
    ): RuntimeExercise {
        val duration = 1_920L
        return RuntimeExercise(
            id = "result-test",
            name = "Result test",
            description = "",
            tempoBpm = tempoBpm,
            timeSignature = TimeSignature(4, 4),
            ticksPerQuarterNote = 480,
            measures = measureNotes.mapIndexed { measureIndex, localTicks ->
                val start = duration * measureIndex
                RuntimeMeasure(
                    index = measureIndex,
                    startTick = start,
                    durationTicks = duration,
                    notes = localTicks.map { local ->
                        RuntimeExpectedNote(
                            measureIndex = measureIndex,
                            positionInMeasureTicks = local,
                            positionTicks = start + local,
                            accent = accent,
                        )
                    },
                )
            },
        )
    }

    private fun hit(
        calibratedSample: Long,
        amplitude: Float = 0.5f,
        confidence: Double = 0.9,
        rawOffset: Long = 0L,
    ) = DetectedHit(
        rawExerciseSample = calibratedSample + rawOffset,
        rawExerciseTimeMillis = calibratedSample + rawOffset.toDouble(),
        calibratedExerciseSample = calibratedSample,
        calibratedExerciseTimeMillis = calibratedSample.toDouble(),
        onsetFrame = 1,
        peakFrame = 2,
        peakExerciseSample = calibratedSample + 2L,
        peakTimeMillis = calibratedSample + 2.0,
        peakAmplitude = amplitude,
        frameLevel = amplitude,
        envelope = amplitude,
        noiseFloor = 0.01f,
        signalToNoiseRatio = 10.0,
        confidence = confidence,
        metronomeBandRatio = 0.0,
        broadbandResidualEnergy = 1.0,
        spectralBandwidthHz = 1_000.0,
        spectralCentroidHz = 1_000.0,
        calibrationApplied = rawOffset != 0L,
    )

    private companion object {
        const val SAMPLE_RATE = 1_000
    }
}
