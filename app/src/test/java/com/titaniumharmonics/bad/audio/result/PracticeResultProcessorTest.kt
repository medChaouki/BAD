package com.titaniumharmonics.bad.audio.result

import com.titaniumharmonics.bad.audio.PcmAudioFormat
import com.titaniumharmonics.bad.audio.PcmEncoding
import com.titaniumharmonics.bad.audio.RecordedSession
import com.titaniumharmonics.bad.audio.analysis.AudioAnalysis
import com.titaniumharmonics.bad.audio.analysis.AudioAnalysisConfig
import com.titaniumharmonics.bad.audio.analysis.ImmutableFloatSeries
import com.titaniumharmonics.bad.audio.analysis.ImmutableLongSeries
import com.titaniumharmonics.bad.audio.detection.HitDetectionResult
import com.titaniumharmonics.bad.audio.detection.SessionDetectionSnapshot
import com.titaniumharmonics.bad.audio.matching.RuntimeExerciseSampleTimeline
import com.titaniumharmonics.bad.audio.matching.SessionJudgementSnapshot
import com.titaniumharmonics.bad.audio.metronome.SessionMetronomeSnapshot
import com.titaniumharmonics.bad.exercise.RuntimeExercise
import com.titaniumharmonics.bad.exercise.RuntimeExpectedNote
import com.titaniumharmonics.bad.exercise.RuntimeMeasure
import com.titaniumharmonics.bad.exercise.TimeSignature
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeResultProcessorTest {
    @Test
    fun completeEmptyDetectionProducesAllMissedResultWithExactSnapshots() {
        val fixture = fixture()

        val processed = PracticeResultProcessor().process(
            fixture.session,
            fixture.analysis,
            fixture.detection,
        ) as PracticeResultProcessingResult.Success

        assertEquals(2, processed.result.totalExpectedNotes)
        assertEquals(2, processed.result.missedCount)
        assertEquals(0, processed.result.extraCount)
        assertSame(fixture.session.runtimeExercise, processed.result.runtimeExercise)
        assertEquals(fixture.session.judgementSnapshot, processed.result.judgementSnapshot)
        assertEquals(fixture.session.detectionSnapshot, processed.result.detectionSnapshot)
        assertEquals(fixture.session.metronomeSnapshot, processed.result.metronomeSnapshot)
    }

    @Test
    fun mismatchedSampleRateAndTimelineAreRejected() {
        val fixture = fixture()
        val wrongRate = fixture.analysis.copyForTest(sampleRateHz = 44_100)
        assertEquals(
            PracticeResultFailure.SessionMismatch,
            (PracticeResultProcessor().process(
                fixture.session,
                wrongRate,
                fixture.detection,
            ) as PracticeResultProcessingResult.Failure).reason,
        )

        val wrongTimeline = fixture.detection.copyForTest(longArrayOf(123L))
        assertEquals(
            PracticeResultFailure.InvalidTimeline,
            (PracticeResultProcessor().process(
                fixture.session,
                fixture.analysis,
                wrongTimeline,
            ) as PracticeResultProcessingResult.Failure).reason,
        )
    }

    @Test
    fun processingIsDeterministicAndCancellationPropagates() {
        val fixture = fixture()
        val processor = PracticeResultProcessor()
        val first = processor.process(fixture.session, fixture.analysis, fixture.detection)
        val second = processor.process(fixture.session, fixture.analysis, fixture.detection)
        assertEquals(
            (first as PracticeResultProcessingResult.Success).result.judgedNotes,
            (second as PracticeResultProcessingResult.Success).result.judgedNotes,
        )

        val failure = runCatching {
            processor.process(fixture.session, fixture.analysis, fixture.detection) {
                throw CancellationException("cancelled")
            }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
    }

    private fun fixture(): Fixture {
        val exercise = RuntimeExercise(
            id = "processor-test",
            name = "Processor test",
            description = "",
            tempoBpm = 120.0,
            timeSignature = TimeSignature(4, 4),
            ticksPerQuarterNote = 480,
            measures = listOf(
                RuntimeMeasure(
                    index = 0,
                    startTick = 0L,
                    durationTicks = 1_920L,
                    notes = listOf(
                        RuntimeExpectedNote(0, 0L, 0L),
                        RuntimeExpectedNote(0, 480L, 480L),
                    ),
                ),
            ),
        )
        val metronome = SessionMetronomeSnapshot.COMPATIBILITY_FALLBACK
        val detectionSnapshot = SessionDetectionSnapshot.COMPATIBILITY_FALLBACK
        val session = RecordedSession(
            wavFilePath = "processor.wav",
            audioFormat = PcmAudioFormat(48_000, 1, PcmEncoding.SIGNED_16_BIT_LITTLE_ENDIAN),
            totalRecordedSampleFrames = 48_100L,
            exerciseStartSampleFrame = 100L,
            runtimeExercise = exercise,
            metronomeSnapshot = metronome,
            detectionSnapshot = detectionSnapshot,
            judgementSnapshot = SessionJudgementSnapshot.COMPATIBILITY_FALLBACK,
        )
        val analysis = analysis(48_000, metronome)
        val expected = RuntimeExerciseSampleTimeline.expectedNotes(exercise, 48_000)
            .map { it.exerciseSample }.toLongArray()
        val detection = HitDetectionResult(
            hits = emptyList(),
            rejectedMetronomeCandidates = emptyList(),
            otherRejectedCandidates = emptyList(),
            candidates = emptyList(),
            adaptiveThreshold = ImmutableFloatSeries.copyOf(floatArrayOf(0f)),
            expectedExerciseSamples = ImmutableLongSeries.copyOf(expected),
            configuration = detectionSnapshot.configuration,
            calibrationOffsetSamples = 0L,
            calibrationApplied = false,
        )
        return Fixture(session, analysis, detection)
    }

    private fun analysis(rate: Int, metronome: SessionMetronomeSnapshot) = AudioAnalysis(
        sampleRateHz = rate,
        gradedSampleFrameCount = 48_000L,
        frameSizeSamples = 240,
        hopSizeSamples = 96,
        frameCenterExerciseSamples = ImmutableLongSeries.copyOf(longArrayOf(0L)),
        representativeRawSamples = ImmutableFloatSeries.copyOf(floatArrayOf(0f)),
        representativeFilteredSamples = ImmutableFloatSeries.copyOf(floatArrayOf(0f)),
        preNotchFrameLevels = ImmutableFloatSeries.copyOf(floatArrayOf(0f)),
        preNotchEnvelope = ImmutableFloatSeries.copyOf(floatArrayOf(0f)),
        framePeaks = ImmutableFloatSeries.copyOf(floatArrayOf(0f)),
        frameLevels = ImmutableFloatSeries.copyOf(floatArrayOf(0f)),
        envelope = ImmutableFloatSeries.copyOf(floatArrayOf(0f)),
        noiseFloor = ImmutableFloatSeries.copyOf(floatArrayOf(0f)),
        maximumNormalizedInputAmplitude = 0f,
        maximumFramePeak = 0f,
        maximumEnvelope = 0f,
        meanNoiseFloor = 0f,
        configuration = AudioAnalysisConfig(),
        metronomeConfiguration = metronome.configuration,
        expectedMetronomeExerciseSamples = ImmutableLongSeries.copyOf(longArrayOf()),
        maximumMetronomeSuppression = 0f,
        postNotchPcm = ImmutableFloatSeries.copyOf(FloatArray(48_000)),
    )

    private fun AudioAnalysis.copyForTest(sampleRateHz: Int) = analysis(
        sampleRateHz,
        SessionMetronomeSnapshot.COMPATIBILITY_FALLBACK,
    )

    private fun HitDetectionResult.copyForTest(expected: LongArray) = HitDetectionResult(
        hits, rejectedMetronomeCandidates, otherRejectedCandidates, candidates,
        adaptiveThreshold, ImmutableLongSeries.copyOf(expected), configuration,
        calibrationOffsetSamples, calibrationApplied,
    )

    private data class Fixture(
        val session: RecordedSession,
        val analysis: AudioAnalysis,
        val detection: HitDetectionResult,
    )
}

