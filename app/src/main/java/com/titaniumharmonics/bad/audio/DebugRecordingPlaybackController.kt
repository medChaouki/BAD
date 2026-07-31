package com.titaniumharmonics.bad.audio

import java.io.File

class DebugRecordingPlaybackController(
    private val player: RecordedAudioPlayer,
    private val onStateChanged: (DebugRecordingPlaybackState) -> Unit = {},
) {
    var state: DebugRecordingPlaybackState = DebugRecordingPlaybackState()
        private set

    fun setRecording(recording: DebugRecording?) {
        player.release()
        if (recording == null) {
            update(DebugRecordingPlaybackState())
            return
        }
        val file = File(recording.filePath)
        if (!file.isFile) {
            update(errorState("Recorded WAV file is missing."))
            return
        }
        try {
            val playerDurationMillis = player.prepare(
                filePath = recording.filePath,
                onCompletion = {
                    update(
                        state.copy(
                            phase = DebugRecordingPlaybackPhase.COMPLETED,
                            positionMillis = state.durationMillis,
                            errorMessage = null,
                        ),
                    )
                },
                onError = { message -> update(errorState(message)) },
            )
            update(
                DebugRecordingPlaybackState(
                    phase = DebugRecordingPlaybackPhase.READY,
                    filePath = recording.filePath,
                    durationMillis = recording.durationMillis.takeIf { it > 0L }
                        ?: playerDurationMillis,
                ),
            )
        } catch (exception: Exception) {
            update(errorState(exception.message ?: "Unable to prepare recorded audio."))
        }
    }

    fun play() = runPlayerAction {
        require(state.canPlay) { "Recorded audio is not ready to play." }
        require(state.filePath?.let { File(it).isFile } == true) {
            "Recorded WAV file is missing."
        }
        if (state.phase == DebugRecordingPlaybackPhase.COMPLETED) player.replay() else player.play()
        state.copy(phase = DebugRecordingPlaybackPhase.PLAYING, errorMessage = null)
    }

    fun pause() = runPlayerAction {
        require(state.phase == DebugRecordingPlaybackPhase.PLAYING) {
            "Recorded audio is not playing."
        }
        player.pause()
        state.copy(
            phase = DebugRecordingPlaybackPhase.PAUSED,
            positionMillis = player.currentPositionMillis(),
        )
    }

    fun stop() = runPlayerAction {
        player.stop()
        state.copy(
            phase = DebugRecordingPlaybackPhase.READY,
            positionMillis = 0L,
            errorMessage = null,
        )
    }

    fun replay() = runPlayerAction {
        require(state.filePath != null) { "Recorded audio is unavailable." }
        player.replay()
        state.copy(
            phase = DebugRecordingPlaybackPhase.PLAYING,
            positionMillis = 0L,
            errorMessage = null,
        )
    }

    fun refreshPosition() {
        if (state.phase != DebugRecordingPlaybackPhase.PLAYING) return
        runPlayerAction {
            state.copy(positionMillis = player.currentPositionMillis())
        }
    }

    fun deleteRecording() {
        val filePath = state.filePath
        player.release()
        if (filePath != null) File(filePath).delete()
        update(DebugRecordingPlaybackState())
    }

    fun release() {
        player.release()
        val filePath = state.filePath
        update(
            if (filePath != null && File(filePath).isFile) {
                state.copy(
                    phase = DebugRecordingPlaybackPhase.READY,
                    positionMillis = 0L,
                )
            } else {
                DebugRecordingPlaybackState()
            },
        )
    }

    private inline fun runPlayerAction(action: () -> DebugRecordingPlaybackState) {
        try {
            update(action())
        } catch (exception: Exception) {
            update(errorState(exception.message ?: "Recorded audio playback failed."))
        }
    }

    private fun errorState(message: String): DebugRecordingPlaybackState = state.copy(
        phase = DebugRecordingPlaybackPhase.ERROR,
        errorMessage = message,
    )

    private fun update(updatedState: DebugRecordingPlaybackState) {
        state = updatedState
        onStateChanged(updatedState)
    }
}
