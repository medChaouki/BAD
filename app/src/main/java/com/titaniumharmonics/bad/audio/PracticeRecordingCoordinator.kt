package com.titaniumharmonics.bad.audio

class PracticeRecordingCoordinator(
    private val recorder: SessionAudioRecorder,
    private val playbackController: DebugRecordingPlaybackController,
) {
    fun startSession() {
        playbackController.deleteRecording()
        recorder.start()
    }

    fun pauseSession() = recorder.pause()
    fun resumeSession() = recorder.resume()

    fun completeSession(): DebugRecording {
        val recording = recorder.finish()
        playbackController.setRecording(recording)
        return recording
    }

    fun cancelSession() = recorder.cancel()

    fun release() {
        recorder.release()
        playbackController.release()
    }
}
