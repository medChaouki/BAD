package com.titaniumharmonics.bad.audio.metronome

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

class AndroidMetronomeTestTonePlayer {
    private val lock = Any()
    private var activeTrack: AudioTrack? = null

    fun play(configuration: MetronomeConfiguration, accent: Boolean) {
        stop()
        val sampleRateHz = DEFAULT_TEST_SAMPLE_RATE_HZ
        configuration.requireValidForSampleRate(sampleRateHz)
        val samples = WindowedMetronomeToneGenerator.generateSequence(
            configuration = configuration.tone,
            accent = accent,
            sampleRateHz = sampleRateHz,
            beatCount = TEST_BEAT_COUNT,
            beatIntervalMillis = TEST_BEAT_INTERVAL_MILLIS,
            totalDurationMillis = TEST_SEQUENCE_DURATION_MILLIS,
        )
        val minimumBufferBytes = AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minimumBufferBytes > 0) { "Test-tone audio output is unavailable." }
        val bufferSizeBytes = maxOf(
            minimumBufferBytes,
            configuration.tone.durationMillis * sampleRateHz / 1_000 * Short.SIZE_BYTES,
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSizeBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        check(track.state == AudioTrack.STATE_INITIALIZED) {
            track.release()
            "Unable to initialize test-tone playback."
        }
        synchronized(lock) {
            activeTrack = track
        }
        try {
            track.play()
            val written = try {
                track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            } catch (failure: Throwable) {
                if (!isActive(track)) return
                throw failure
            }
            if (!isActive(track)) return
            check(written == samples.size) { "Test-tone buffer write failed." }
            val deadline = System.nanoTime() + TEST_TONE_TIMEOUT_NANOS
            while (true) {
                val playbackHeadPosition = synchronized(lock) {
                    if (activeTrack !== track) return
                    track.playbackHeadPosition.toLong()
                }
                if (playbackHeadPosition >= samples.size) break
                check(System.nanoTime() < deadline) { "Test-tone playback timed out." }
                Thread.sleep(2L)
            }
        } finally {
            synchronized(lock) {
                if (activeTrack === track) {
                    activeTrack = null
                    release(track)
                }
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            val track = activeTrack ?: return
            activeTrack = null
            release(track)
        }
    }

    private fun release(track: AudioTrack) {
        runCatching { track.pause() }
        runCatching { track.flush() }
        runCatching { track.stop() }
        track.release()
    }

    private fun isActive(track: AudioTrack): Boolean = synchronized(lock) {
        activeTrack === track
    }

    private companion object {
        const val DEFAULT_TEST_SAMPLE_RATE_HZ = 48_000
        const val TEST_BEAT_COUNT = 4
        const val TEST_BEAT_INTERVAL_MILLIS = 500
        const val TEST_SEQUENCE_DURATION_MILLIS = 2_000
        const val TEST_TONE_TIMEOUT_NANOS = 3_000_000_000L
    }
}
