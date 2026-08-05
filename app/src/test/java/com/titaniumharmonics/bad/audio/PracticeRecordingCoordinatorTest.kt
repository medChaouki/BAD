package com.titaniumharmonics.bad.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import com.titaniumharmonics.bad.audio.metronome.MetronomeConfiguration
import com.titaniumharmonics.bad.audio.metronome.SessionMetronomeSnapshot
import com.titaniumharmonics.bad.audio.detection.HitDetectionConfiguration
import com.titaniumharmonics.bad.audio.detection.SessionDetectionSnapshot

class PracticeRecordingCoordinatorTest {
    @Test
    fun completedSessionPreservesFrozenMetronomeConfiguration() {
        val fixture = fixture()
        val snapshot = SessionMetronomeSnapshot(
            configuration = MetronomeConfiguration.DEFAULT.withToneFrequency(5_000),
            downbeatsOnly = true,
        )
        val detectionSnapshot = SessionDetectionSnapshot(
            configuration = HitDetectionConfiguration.DEFAULT.copy(
                minimumAbsoluteThreshold = 0.08,
            ),
        )
        fixture.coordinator.startSession(fixture.exercise, snapshot, detectionSnapshot)
        fixture.recorder.appendFrames(192_000L)
        fixture.coordinator.markExerciseStarted()
        fixture.recorder.appendFrames(48_000L)

        val session = fixture.coordinator.completeSession()
        val independentlyChangedGlobal = MetronomeConfiguration.DEFAULT.withToneFrequency(7_000)

        assertEquals(snapshot, session.metronomeSnapshot)
        assertEquals(5_000, session.metronomeSnapshot.configuration.tone.frequencyHz)
        assertEquals(7_000, independentlyChangedGlobal.tone.frequencyHz)
        assertEquals(detectionSnapshot, session.detectionSnapshot)
        assertEquals(0.08, session.detectionSnapshot.configuration.minimumAbsoluteThreshold, 0.0)
    }

    @Test
    fun noPauseSession_capturesStartAndCreatesOneCompletedSession() {
        val fixture = fixture()
        fixture.coordinator.startSession(fixture.exercise)
        fixture.recorder.appendFrames(192_000L)

        assertEquals(192_000L, fixture.coordinator.markExerciseStarted())
        fixture.recorder.appendFrames(192_000L)
        val session = fixture.coordinator.completeSession()

        assertEquals(192_000L, session.exerciseStartSampleFrame)
        assertEquals(384_000L, session.totalRecordedSampleFrames)
        assertEquals(192_000L, session.gradedExerciseSampleFrames)
        assertSame(fixture.exercise, session.runtimeExercise)
        assertSame(session, fixture.coordinator.completedSession)
        assertEquals(1, fixture.recorder.finishCount)
        assertEquals(DebugRecordingPlaybackPhase.READY, fixture.controller.state.phase)
        assertThrows(IllegalStateException::class.java) {
            fixture.coordinator.completeSession()
        }
        assertEquals(1, fixture.recorder.finishCount)
    }

    @Test
    fun onePauseResume_excludesPausedTimeAndResumeCountInFrames() {
        val fixture = fixture()
        fixture.coordinator.startSession(fixture.exercise)
        fixture.recorder.appendFrames(192_000L)
        fixture.coordinator.markExerciseStarted()
        fixture.recorder.appendFrames(48_000L)

        fixture.coordinator.pauseSession()
        val framesWhilePaused = fixture.recorder.writtenFrames
        fixture.coordinator.beginResumeCountIn()
        fixture.recorder.appendFrames(192_000L)
        assertEquals(framesWhilePaused, fixture.recorder.writtenFrames)
        assertEquals("pause", fixture.recorder.events.last())

        var playbackResumed = false
        fixture.coordinator.resumeSession {
            assertEquals("resume", fixture.recorder.events.last())
            playbackResumed = true
        }
        fixture.recorder.appendFrames(48_000L)
        val session = fixture.coordinator.completeSession()

        assertTrue(playbackResumed)
        assertEquals(96_000L, session.gradedExerciseSampleFrames)
        assertEquals(2_000L, session.gradedExerciseDurationMillis)
        assertEquals(listOf("start", "pause", "resume", "finish"), fixture.recorder.events)
    }

    @Test
    fun multiplePauseResumeCycles_keepExerciseRecordingContinuous() {
        val fixture = fixture()
        fixture.coordinator.startSession(fixture.exercise)
        fixture.recorder.appendFrames(192_000L)
        fixture.coordinator.markExerciseStarted()

        repeat(2) {
            fixture.recorder.appendFrames(24_000L)
            fixture.coordinator.pauseSession()
            fixture.coordinator.beginResumeCountIn()
            fixture.recorder.appendFrames(192_000L)
            fixture.coordinator.resumeSession {}
        }
        fixture.recorder.appendFrames(48_000L)

        val session = fixture.coordinator.completeSession()

        assertEquals(96_000L, session.gradedExerciseSampleFrames)
        assertEquals(
            listOf("start", "pause", "resume", "pause", "resume", "finish"),
            fixture.recorder.events,
        )
    }

    @Test
    fun pauseImmediatelyAfterStart_preservesExerciseStartBoundary() {
        val fixture = fixture()
        fixture.coordinator.startSession(fixture.exercise)
        fixture.recorder.appendFrames(192_000L)
        fixture.coordinator.markExerciseStarted()
        fixture.coordinator.pauseSession()
        fixture.coordinator.beginResumeCountIn()
        fixture.coordinator.resumeSession {}
        fixture.recorder.appendFrames(1L)

        val session = fixture.coordinator.completeSession()

        assertEquals(192_000L, session.exerciseStartSampleFrame)
        assertEquals(1L, session.gradedExerciseSampleFrames)
    }

    @Test
    fun pauseDuringInitialCountIn_excludesResumeCountInButKeepsInitialCountIn() {
        val fixture = fixture()
        fixture.coordinator.startSession(fixture.exercise)
        fixture.recorder.appendFrames(48_000L)
        fixture.coordinator.pauseSession()
        fixture.coordinator.beginResumeCountIn()
        fixture.recorder.appendFrames(192_000L)
        fixture.coordinator.resumeSession {}
        fixture.recorder.appendFrames(144_000L)

        fixture.coordinator.markExerciseStarted()
        fixture.recorder.appendFrames(1L)
        val session = fixture.coordinator.completeSession()

        assertEquals(192_000L, session.exerciseStartSampleFrame)
        assertEquals(192_001L, session.totalRecordedSampleFrames)
    }

    @Test
    fun pauseNearCompletion_preservesAllActiveExerciseFrames() {
        val fixture = fixture()
        fixture.coordinator.startSession(fixture.exercise)
        fixture.recorder.appendFrames(192_000L)
        fixture.coordinator.markExerciseStarted()
        fixture.recorder.appendFrames(191_999L)
        fixture.coordinator.pauseSession()
        fixture.coordinator.beginResumeCountIn()
        fixture.coordinator.resumeSession {}
        fixture.recorder.appendFrames(1L)

        val session = fixture.coordinator.completeSession()

        assertEquals(192_000L, session.gradedExerciseSampleFrames)
    }

    @Test
    fun stopDuringResumeCountIn_exposesNoCompletedSession() {
        val fixture = fixture()
        fixture.coordinator.startSession(fixture.exercise)
        fixture.recorder.appendFrames(192_000L)
        fixture.coordinator.markExerciseStarted()
        fixture.coordinator.pauseSession()
        fixture.coordinator.beginResumeCountIn()

        fixture.coordinator.cancelSession()

        assertEquals(PracticeRecordingPhase.CANCELLED, fixture.coordinator.phase)
        assertNull(fixture.coordinator.completedSession)
        assertEquals("cancel", fixture.recorder.events.last())
        assertEquals(0, fixture.recorder.finishCount)
    }

    @Test
    fun recorderResumeFailure_preventsExerciseResumeAndCompletion() {
        val fixture = fixture(recorderResumeFailure = true)
        fixture.coordinator.startSession(fixture.exercise)
        fixture.recorder.appendFrames(192_000L)
        fixture.coordinator.markExerciseStarted()
        fixture.coordinator.pauseSession()
        fixture.coordinator.beginResumeCountIn()
        var playbackResumed = false

        assertThrows(IllegalStateException::class.java) {
            fixture.coordinator.resumeSession { playbackResumed = true }
        }

        assertFalse(playbackResumed)
        assertEquals(PracticeRecordingPhase.ERROR, fixture.coordinator.phase)
        assertNull(fixture.coordinator.completedSession)
        assertEquals(0, fixture.recorder.finishCount)
    }

    @Test
    fun playbackResumeFailure_cancelsResumedRecorderAndCompletion() {
        val fixture = fixture()
        fixture.coordinator.startSession(fixture.exercise)
        fixture.recorder.appendFrames(192_000L)
        fixture.coordinator.markExerciseStarted()
        fixture.coordinator.pauseSession()
        fixture.coordinator.beginResumeCountIn()

        assertThrows(IllegalStateException::class.java) {
            fixture.coordinator.resumeSession { error("playback resume failed") }
        }

        assertEquals(listOf("start", "pause", "resume", "cancel"), fixture.recorder.events)
        assertEquals(PracticeRecordingPhase.ERROR, fixture.coordinator.phase)
        assertNull(fixture.coordinator.completedSession)
    }

    @Test
    fun newSessionReplacesOldDebugRecordingAndDeleteInvalidatesSession() {
        val fixture = fixture()
        val first = completeBasicSession(fixture)
        assertTrue(File(first.wavFilePath).isFile)

        fixture.coordinator.startSession(fixture.exercise)

        assertFalse(File(first.wavFilePath).exists())
        assertNull(fixture.coordinator.completedSession)
        assertEquals(DebugRecordingPlaybackPhase.UNAVAILABLE, fixture.controller.state.phase)

        fixture.recorder.appendFrames(192_000L)
        fixture.coordinator.markExerciseStarted()
        fixture.recorder.appendFrames(1L)
        val second = fixture.coordinator.completeSession()
        fixture.coordinator.deleteCompletedSession()

        assertFalse(File(second.wavFilePath).exists())
        assertNull(fixture.coordinator.completedSession)
    }

    @Test
    fun failedOrCancelledSession_neverPublishesAnalyzableSession() {
        val cancelled = fixture()
        cancelled.coordinator.startSession(cancelled.exercise)
        cancelled.coordinator.cancelSession()
        assertNull(cancelled.coordinator.completedSession)

        val invalid = fixture()
        invalid.coordinator.startSession(invalid.exercise)
        invalid.recorder.appendFrames(100L)
        invalid.coordinator.markExerciseStarted()
        assertThrows(IllegalArgumentException::class.java) {
            invalid.coordinator.completeSession()
        }
        assertEquals(PracticeRecordingPhase.ERROR, invalid.coordinator.phase)
        assertNull(invalid.coordinator.completedSession)
    }

    @Test
    fun releaseCleansRecorderAndPlayerResources() {
        val fixture = fixture()

        fixture.coordinator.release()

        assertTrue(fixture.recorder.released)
        assertTrue(fixture.player.released)
    }

    private fun completeBasicSession(fixture: Fixture): RecordedSession {
        fixture.coordinator.startSession(fixture.exercise)
        fixture.recorder.appendFrames(192_000L)
        fixture.coordinator.markExerciseStarted()
        fixture.recorder.appendFrames(192_000L)
        return fixture.coordinator.completeSession()
    }

    private fun fixture(recorderResumeFailure: Boolean = false): Fixture {
        val recorder = FakeSessionAudioRecorder(resumeFailure = recorderResumeFailure)
        val player = FakeRecordedAudioPlayer()
        val controller = DebugRecordingPlaybackController(player)
        val exercise = runtimeExerciseForAudioTest()
        return Fixture(
            recorder = recorder,
            player = player,
            controller = controller,
            coordinator = PracticeRecordingCoordinator(recorder, controller),
            exercise = exercise,
        )
    }

    private data class Fixture(
        val recorder: FakeSessionAudioRecorder,
        val player: FakeRecordedAudioPlayer,
        val controller: DebugRecordingPlaybackController,
        val coordinator: PracticeRecordingCoordinator,
        val exercise: com.titaniumharmonics.bad.exercise.RuntimeExercise,
    )

    private class FakeSessionAudioRecorder(
        private val resumeFailure: Boolean = false,
    ) : SessionAudioRecorder {
        override val format = PcmAudioFormat(
            sampleRateHz = 48_000,
            channelCount = 1,
            encoding = PcmEncoding.SIGNED_16_BIT_LITTLE_ENDIAN,
        )
        override val totalWrittenSampleFrames: Long
            get() = writtenFrames

        val events = mutableListOf<String>()
        var writtenFrames = 0L
            private set
        var finishCount = 0
        var released = false
        private var capturing = false

        fun appendFrames(sampleFrames: Long) {
            if (capturing) writtenFrames += sampleFrames
        }

        override fun start() {
            events += "start"
            writtenFrames = 0L
            capturing = true
        }

        override fun pause() {
            events += "pause"
            capturing = false
        }

        override fun resume() {
            events += "resume"
            if (resumeFailure) error("recorder resume failed")
            capturing = true
        }

        override fun finish(): FinalizedRecording {
            events += "finish"
            finishCount += 1
            capturing = false
            val file = File(Files.createTempDirectory("bad-session").toFile(), "recording.wav")
            file.writeBytes(byteArrayOf(1))
            return FinalizedRecording(
                filePath = file.absolutePath,
                format = format,
                totalSampleFrames = writtenFrames,
            )
        }

        override fun cancel() {
            events += "cancel"
            capturing = false
        }

        override fun release() {
            released = true
        }
    }

    private class FakeRecordedAudioPlayer : RecordedAudioPlayer {
        var released = false
        override fun prepare(
            filePath: String,
            onCompletion: () -> Unit,
            onError: (String) -> Unit,
        ) = 1L
        override fun play() = Unit
        override fun pause() = Unit
        override fun stop() = Unit
        override fun replay() = Unit
        override fun currentPositionMillis() = 0L
        override fun release() { released = true }
    }
}
