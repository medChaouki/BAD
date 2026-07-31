package com.titaniumharmonics.bad.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PracticeRecordingCoordinatorTest {
    @Test
    fun lifecycle_followsPlayerAndPublishesOnlyCompletedRecording() {
        val recorder = FakeSessionAudioRecorder()
        val controller = DebugRecordingPlaybackController(FakeRecordedAudioPlayer())
        val coordinator = PracticeRecordingCoordinator(recorder, controller)

        coordinator.startSession()
        coordinator.pauseSession()
        coordinator.resumeSession()
        coordinator.completeSession()

        assertEquals(listOf("start", "pause", "resume", "finish"), recorder.events)
        assertEquals(DebugRecordingPlaybackPhase.READY, controller.state.phase)
        assertTrue(controller.state.filePath?.let { File(it).exists() } == true)
    }

    @Test
    fun newSessionDeletesOldRecordingAndCancelledSessionIsNotExposed() {
        val recorder = FakeSessionAudioRecorder()
        val controller = DebugRecordingPlaybackController(FakeRecordedAudioPlayer())
        val coordinator = PracticeRecordingCoordinator(recorder, controller)
        coordinator.startSession()
        val first = coordinator.completeSession()
        assertTrue(File(first.filePath).exists())

        coordinator.startSession()
        assertFalse(File(first.filePath).exists())
        assertEquals(DebugRecordingPlaybackPhase.UNAVAILABLE, controller.state.phase)
        coordinator.cancelSession()

        assertEquals("cancel", recorder.events.last())
        assertEquals(DebugRecordingPlaybackPhase.UNAVAILABLE, controller.state.phase)
    }

    @Test
    fun releaseCleansRecorderAndPlayerResources() {
        val recorder = FakeSessionAudioRecorder()
        val player = FakeRecordedAudioPlayer()
        val coordinator = PracticeRecordingCoordinator(
            recorder,
            DebugRecordingPlaybackController(player),
        )

        coordinator.release()

        assertTrue(recorder.released)
        assertTrue(player.released)
    }

    private class FakeSessionAudioRecorder : SessionAudioRecorder {
        val events = mutableListOf<String>()
        var released = false
        override fun start() { events += "start" }
        override fun pause() { events += "pause" }
        override fun resume() { events += "resume" }
        override fun finish(): DebugRecording {
            events += "finish"
            val file = File(Files.createTempDirectory("bad-session").toFile(), "recording.wav")
            file.writeBytes(byteArrayOf(1))
            return DebugRecording(file.absolutePath, 1L)
        }
        override fun cancel() { events += "cancel" }
        override fun release() { released = true }
    }

    private class FakeRecordedAudioPlayer : RecordedAudioPlayer {
        var released = false
        override fun prepare(filePath: String, onCompletion: () -> Unit, onError: (String) -> Unit) = 1L
        override fun play() = Unit
        override fun pause() = Unit
        override fun stop() = Unit
        override fun replay() = Unit
        override fun currentPositionMillis() = 0L
        override fun release() { released = true }
    }
}
