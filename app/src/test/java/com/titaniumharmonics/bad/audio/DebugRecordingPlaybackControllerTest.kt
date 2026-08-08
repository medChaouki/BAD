package com.titaniumharmonics.bad.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DebugRecordingPlaybackControllerTest {
    @Test
    fun playbackState_coversReadyPlayingPausedCompletedAndStopped() {
        val player = FakeRecordedAudioPlayer(durationMillis = 1_250L)
        val controller = DebugRecordingPlaybackController(player)
        val recording = recordedSessionFile(
            sampleRateHz = 1_000,
            exerciseStartSampleFrame = 250L,
            totalSampleFrames = 1_250L,
        )

        assertEquals(DebugRecordingPlaybackPhase.UNAVAILABLE, controller.state.phase)
        controller.setRecording(recording)
        assertEquals(DebugRecordingPlaybackPhase.READY, controller.state.phase)
        assertTrue(controller.state.canPlay)

        controller.play()
        assertEquals(DebugRecordingPlaybackPhase.PLAYING, controller.state.phase)
        player.positionMillis = 500L
        controller.refreshPosition()
        assertEquals(500L, controller.state.positionMillis)

        controller.pause()
        assertEquals(DebugRecordingPlaybackPhase.PAUSED, controller.state.phase)
        controller.play()
        player.complete()
        assertEquals(DebugRecordingPlaybackPhase.COMPLETED, controller.state.phase)
        assertEquals(1_250L, controller.state.positionMillis)

        controller.stop()
        assertEquals(DebugRecordingPlaybackPhase.READY, controller.state.phase)
        assertEquals(0L, controller.state.positionMillis)
    }

    @Test
    fun missingFile_doesNotEnablePlaybackAndAttemptingPlayBecomesError() {
        val player = FakeRecordedAudioPlayer()
        val controller = DebugRecordingPlaybackController(player)
        val missing = File(Files.createTempDirectory("bad-missing").toFile(), "missing.wav")

        controller.setRecording(
            RecordedSession(
                wavFilePath = missing.absolutePath,
                audioFormat = PcmAudioFormat(
                    sampleRateHz = 1_000,
                    channelCount = 1,
                    encoding = PcmEncoding.SIGNED_16_BIT_LITTLE_ENDIAN,
                ),
                totalRecordedSampleFrames = 100L,
                exerciseStartSampleFrame = 50L,
                runtimeExercise = runtimeExerciseForAudioTest(),
            ),
        )

        assertEquals(DebugRecordingPlaybackPhase.ERROR, controller.state.phase)
        assertFalse(controller.state.canPlay)
        controller.play()
        assertEquals(DebugRecordingPlaybackPhase.ERROR, controller.state.phase)
    }

    @Test
    fun deleteRecording_removesFileAndDisablesControls() {
        val controller = DebugRecordingPlaybackController(FakeRecordedAudioPlayer())
        val recording = recordedSessionFile()
        controller.setRecording(recording)

        assertTrue(controller.deleteRecording())

        assertFalse(File(recording.wavFilePath).exists())
        assertEquals(DebugRecordingPlaybackPhase.UNAVAILABLE, controller.state.phase)
        assertFalse(controller.state.canPlay)
    }

    @Test
    fun cleanupFailureIsReportedWithoutDiscardingRecordingState() {
        val controller = DebugRecordingPlaybackController(FakeRecordedAudioPlayer())
        val recording = recordedSessionFile()
        controller.setRecording(recording)
        val path = File(recording.wavFilePath)
        assertTrue(path.delete())
        assertTrue(path.mkdir())
        File(path, "still-in-use").writeText("keep")

        assertFalse(controller.deleteRecording())
        assertEquals(recording.wavFilePath, controller.state.filePath)

        File(path, "still-in-use").delete()
        path.delete()
    }

    @Test
    fun playerFailure_exposesErrorAndReleaseCleansResources() {
        val player = FakeRecordedAudioPlayer(prepareFailure = true)
        val controller = DebugRecordingPlaybackController(player)

        controller.setRecording(recordedSessionFile())
        assertEquals(DebugRecordingPlaybackPhase.ERROR, controller.state.phase)

        controller.release()
        assertTrue(player.releaseCount > 0)
    }

    private class FakeRecordedAudioPlayer(
        private val durationMillis: Long = 1_000L,
        private val prepareFailure: Boolean = false,
    ) : RecordedAudioPlayer {
        var positionMillis = 0L
        var releaseCount = 0
        private var completion: (() -> Unit)? = null

        override fun prepare(
            filePath: String,
            onCompletion: () -> Unit,
            onError: (String) -> Unit,
        ): Long {
            if (prepareFailure) error("prepare failed")
            completion = onCompletion
            return durationMillis
        }

        override fun play() = Unit
        override fun pause() = Unit
        override fun stop() { positionMillis = 0L }
        override fun replay() { positionMillis = 0L }
        override fun currentPositionMillis(): Long = positionMillis
        override fun release() { releaseCount += 1 }
        fun complete() = completion?.invoke() ?: Unit
    }
}
