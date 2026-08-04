package com.titaniumharmonics.bad.audio

import com.titaniumharmonics.bad.audio.metronome.MetronomeConfiguration
import com.titaniumharmonics.bad.audio.metronome.SessionMetronomeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class RecordedSessionTest {
    @Test
    fun sampleFrameConversions_supportActualSampleRateAndRoundDown() {
        assertEquals(
            1_000_000_000L,
            SampleFrameTiming.sampleFramesToDurationNanos(44_100L, 44_100),
        )
        assertEquals(
            22_675L,
            SampleFrameTiming.sampleFramesToDurationNanos(1L, 44_100),
        )
        assertEquals(
            1L,
            SampleFrameTiming.durationNanosToSampleFrames(22_676L, 44_100),
        )
    }

    @Test
    fun sampleFrameConversions_handleLongRecordingsWithoutIntermediateOverflow() {
        val thirtyDaysFrames = 48_000L * 60L * 60L * 24L * 30L

        assertEquals(
            30L * 24L * 60L * 60L * 1_000_000_000L,
            SampleFrameTiming.sampleFramesToDurationNanos(thirtyDaysFrames, 48_000),
        )
    }

    @Test
    fun recordingSamples_mapAroundExerciseStart() {
        val session = recordedSessionFile(
            exerciseStartSampleFrame = 48_000L,
            totalSampleFrames = 144_000L,
        )

        assertNull(session.recordingSampleToExerciseRelativeSample(47_999L))
        assertNull(session.recordingSampleToExerciseRelativeDurationNanos(0L))
        assertEquals(0L, session.recordingSampleToExerciseRelativeSample(48_000L))
        assertEquals(24_000L, session.recordingSampleToExerciseRelativeSample(72_000L))
        assertEquals(
            500_000_000L,
            session.recordingSampleToExerciseRelativeDurationNanos(72_000L),
        )
        assertEquals(72_000L, session.exerciseRelativeSampleToRecordingSample(24_000L))
        assertEquals(
            72_000L,
            session.exerciseRelativeDurationNanosToRecordingSample(500_000_000L),
        )
    }

    @Test
    fun completedSession_preservesSnapshotAndDerivesCountsAndDurations() {
        val exercise = runtimeExerciseForAudioTest()
        val metronomeSnapshot = SessionMetronomeSnapshot(
            configuration = MetronomeConfiguration.DEFAULT.withToneFrequency(5_000),
            downbeatsOnly = true,
        )
        val session = RecordedSession(
            wavFilePath = "recording.wav",
            audioFormat = PcmAudioFormat(
                sampleRateHz = 48_000,
                channelCount = 1,
                encoding = PcmEncoding.SIGNED_16_BIT_LITTLE_ENDIAN,
            ),
            totalRecordedSampleFrames = 240_000L,
            exerciseStartSampleFrame = 48_000L,
            runtimeExercise = exercise,
            metronomeSnapshot = metronomeSnapshot,
        )

        assertSame(exercise, session.runtimeExercise)
        assertEquals(metronomeSnapshot, session.metronomeSnapshot)
        assertEquals(48_000L, session.initialCountInSampleFrames)
        assertEquals(192_000L, session.gradedExerciseSampleFrames)
        assertEquals(5_000L, session.recordingDurationMillis)
        assertEquals(4_000L, session.gradedExerciseDurationMillis)
    }

    @Test
    fun missingSnapshotUsesDocumentedCompatibilityDefaults() {
        val session = recordedSessionFile(
            exerciseStartSampleFrame = 48_000L,
            totalSampleFrames = 144_000L,
        )

        assertEquals(
            SessionMetronomeSnapshot.COMPATIBILITY_FALLBACK,
            session.metronomeSnapshot,
        )
    }

    @Test
    fun invalidCompletedSessionCounts_areRejected() {
        val format = PcmAudioFormat(
            sampleRateHz = 48_000,
            channelCount = 1,
            encoding = PcmEncoding.SIGNED_16_BIT_LITTLE_ENDIAN,
        )
        assertThrows(IllegalArgumentException::class.java) {
            RecordedSession("recording.wav", format, 0L, 0L, runtimeExerciseForAudioTest())
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecordedSession("recording.wav", format, 100L, 100L, runtimeExerciseForAudioTest())
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecordedSession("recording.wav", format, 100L, 101L, runtimeExerciseForAudioTest())
        }
    }
}
