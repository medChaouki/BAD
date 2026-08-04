package com.titaniumharmonics.bad.audio

import android.media.AudioAttributes
import android.media.MediaPlayer

class MediaPlayerRecordedAudioPlayer : RecordedAudioPlayer {
    private var mediaPlayer: MediaPlayer? = null

    override fun prepare(
        filePath: String,
        onCompletion: () -> Unit,
        onError: (String) -> Unit,
    ): Long {
        release()
        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            setDataSource(filePath)
            setOnCompletionListener { onCompletion() }
            setOnErrorListener { _, what, extra ->
                onError("Recorded audio playback failed ($what/$extra).")
                true
            }
            prepare()
        }
        mediaPlayer = player
        return player.duration.toLong()
    }

    override fun play() {
        checkNotNull(mediaPlayer) { "Recorded audio is not prepared." }.start()
    }

    override fun pause() {
        val player = checkNotNull(mediaPlayer) { "Recorded audio is not prepared." }
        if (player.isPlaying) player.pause()
    }

    override fun stop() {
        val player = checkNotNull(mediaPlayer) { "Recorded audio is not prepared." }
        if (player.isPlaying) player.pause()
        player.seekTo(0)
    }

    override fun replay() {
        val player = checkNotNull(mediaPlayer) { "Recorded audio is not prepared." }
        player.seekTo(0)
        player.start()
    }

    override fun currentPositionMillis(): Long =
        mediaPlayer?.currentPosition?.toLong() ?: 0L

    override fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
