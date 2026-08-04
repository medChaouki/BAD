package com.titaniumharmonics.bad.audio.analysis

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecordedSessionAudioValidatorTest {
    private val reader = WavPcm16Reader()

    @Test
    fun matchingSessionExcludesInitialCountInAndMapsFirstGradedSampleToZero() {
        val samples = shortArrayOf(10, 20, 30, 40, 50)
        val file = writeTestWav(samples)
        val session = recordedSessionForWav(file, 48_000, 5L, 2L)

        val graded = RecordedSessionAudioValidator().validateAndExtract(
            session,
            reader.read(file),
        )

        assertArrayEquals(shortArrayOf(30, 40, 50), graded.gradedSamples)
        assertEquals(0L, session.recordingSampleToExerciseRelativeSample(2L))
        assertEquals(3L, session.gradedExerciseSampleFrames)
    }

    @Test
    fun exerciseStartAtZeroIncludesCompleteWav() {
        val samples = shortArrayOf(1, 2, 3)
        val file = writeTestWav(samples, sampleRateHz = 44_100)
        val session = recordedSessionForWav(file, 44_100, 3L, 0L)

        val graded = RecordedSessionAudioValidator().validateAndExtract(
            session,
            reader.read(file),
        )

        assertArrayEquals(samples, graded.gradedSamples)
    }

    @Test
    fun rejectsSampleRateChannelAndSampleCountMismatch() {
        val file = writeTestWav(shortArrayOf(1, 2, 3))
        val decoded = reader.read(file)
        assertThrows(AudioAnalysisException.SessionMismatch::class.java) {
            RecordedSessionAudioValidator().validateAndExtract(
                recordedSessionForWav(file, 44_100, 3L, 1L),
                decoded,
            )
        }
        assertThrows(AudioAnalysisException.SessionMismatch::class.java) {
            RecordedSessionAudioValidator().validateAndExtract(
                recordedSessionForWav(file, 48_000, 3L, 1L, channelCount = 2),
                decoded,
            )
        }
        assertThrows(AudioAnalysisException.SessionMismatch::class.java) {
            RecordedSessionAudioValidator().validateAndExtract(
                recordedSessionForWav(file, 48_000, 4L, 1L),
                decoded,
            )
        }
    }

    @Test
    fun documentedSampleCountToleranceIsApplied() {
        val file = writeTestWav(shortArrayOf(1, 2, 3))
        val session = recordedSessionForWav(file, 48_000, 4L, 1L)

        val graded = RecordedSessionAudioValidator(sampleCountToleranceFrames = 1L)
            .validateAndExtract(session, reader.read(file))

        assertArrayEquals(shortArrayOf(2, 3), graded.gradedSamples)
    }

    @Test
    fun exerciseStartAtOrAfterEndIsRejected() {
        val decoded = reader.read(writeTestWav(shortArrayOf(1, 2, 3)))
        assertThrows(AudioAnalysisException.SessionMismatch::class.java) {
            GradedRangeExtractor.extract(decoded, 3L)
        }
        assertThrows(AudioAnalysisException.SessionMismatch::class.java) {
            GradedRangeExtractor.extract(decoded, 4L)
        }
    }

    @Test
    fun continuousGradedRangeNeedsNoPauseResumeSegments() {
        val decoded = reader.read(writeTestWav(shortArrayOf(9, 8, 1, 2, 3, 4)))

        val graded = GradedRangeExtractor.extract(decoded, 2L)

        assertArrayEquals(shortArrayOf(1, 2, 3, 4), graded)
    }
}
