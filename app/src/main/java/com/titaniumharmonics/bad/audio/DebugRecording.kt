package com.titaniumharmonics.bad.audio

enum class DebugRecordingPlaybackPhase {
    UNAVAILABLE,
    READY,
    PLAYING,
    PAUSED,
    COMPLETED,
    ERROR,
}

data class DebugRecordingPlaybackState(
    val phase: DebugRecordingPlaybackPhase = DebugRecordingPlaybackPhase.UNAVAILABLE,
    val filePath: String? = null,
    val positionMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val errorMessage: String? = null,
) {
    val canPlay: Boolean
        get() = phase in setOf(
            DebugRecordingPlaybackPhase.READY,
            DebugRecordingPlaybackPhase.PAUSED,
            DebugRecordingPlaybackPhase.COMPLETED,
        )
}

interface SessionAudioRecorder {
    val format: PcmAudioFormat?
    val totalWrittenSampleFrames: Long

    fun start()
    fun pause()
    fun resume()
    fun finish(): FinalizedRecording
    fun cancel()
    fun release()
}

interface RecordedAudioPlayer {
    fun prepare(
        filePath: String,
        onCompletion: () -> Unit,
        onError: (String) -> Unit,
    ): Long

    fun play()
    fun pause()
    fun stop()
    fun replay()
    fun currentPositionMillis(): Long
    fun release()
}
