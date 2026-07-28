package com.titaniumharmonics.bad.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.annotation.WorkerThread
import com.titaniumharmonics.bad.exercise.Exercise
import com.titaniumharmonics.bad.timing.MonotonicClock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

class AudioTrackMetronome(
    private val clock: MonotonicClock,
) : MetronomePlayer {
    private var activePlayback: ActivePlayback? = null

    @WorkerThread
    @Synchronized
    override fun start(
        exercise: Exercise,
        downbeatsOnly: Boolean,
    ): Long {
        stop()

        val sampleRateHz = ClickTrackGenerator.DEFAULT_SAMPLE_RATE_HZ
        val minimumBufferSizeBytes = AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minimumBufferSizeBytes > 0) {
            "Device does not support 48 kHz mono PCM audio output " +
                "(AudioTrack error $minimumBufferSizeBytes)."
        }

        val samples = ClickTrackGenerator.generate(
            exercise = exercise,
            sampleRateHz = sampleRateHz,
            downbeatsOnly = downbeatsOnly,
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
            .setBufferSizeInBytes(minimumBufferSizeBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            throw IllegalStateException(
                "Unable to initialise streaming metronome audio output " +
                    "at $sampleRateHz Hz with a $minimumBufferSizeBytes-byte buffer.",
            )
        }

        try {
            val initialSampleCount = min(
                samples.size,
                track.bufferCapacityInFrames,
            )
            val writtenSampleCount = track.write(
                samples,
                0,
                initialSampleCount,
                AudioTrack.WRITE_BLOCKING,
            )
            if (writtenSampleCount != initialSampleCount) {
                throw IllegalStateException(
                    "Initial metronome buffer write was incomplete: " +
                        "$writtenSampleCount of $initialSampleCount samples.",
                )
            }

            val stopRequested = AtomicBoolean(false)
            val writerThread = Thread(
                {
                    writeRemainingSamples(
                        track = track,
                        samples = samples,
                        initialOffset = initialSampleCount,
                        stopRequested = stopRequested,
                    )
                },
                AUDIO_WRITER_THREAD_NAME,
            )

            track.play()
            val playbackStartedNanos = clock.nowNanos()
            activePlayback = ActivePlayback(
                track = track,
                stopRequested = stopRequested,
                writerThread = writerThread,
            )
            writerThread.start()
            return playbackStartedNanos
        } catch (exception: Exception) {
            activePlayback = null
            track.release()
            throw exception
        }
    }

    @Synchronized
    override fun stop() {
        val playback = activePlayback ?: return
        activePlayback = null
        playback.stopRequested.set(true)

        try {
            if (playback.track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                playback.track.pause()
            }
            playback.track.flush()
            playback.track.stop()
        } finally {
            playback.writerThread.join(WRITER_STOP_TIMEOUT_MILLIS)
            playback.track.release()
        }
    }

    private fun writeRemainingSamples(
        track: AudioTrack,
        samples: ShortArray,
        initialOffset: Int,
        stopRequested: AtomicBoolean,
    ) {
        var sampleOffset = initialOffset
        while (sampleOffset < samples.size && !stopRequested.get()) {
            val sampleCount = min(
                track.bufferCapacityInFrames,
                samples.size - sampleOffset,
            )
            val writtenSampleCount = track.write(
                samples,
                sampleOffset,
                sampleCount,
                AudioTrack.WRITE_BLOCKING,
            )
            if (writtenSampleCount <= 0) {
                if (!stopRequested.get()) {
                    Log.e(
                        LOG_TAG,
                        "Metronome stream write failed with AudioTrack error " +
                            "$writtenSampleCount.",
                    )
                }
                return
            }
            sampleOffset += writtenSampleCount
        }
    }

    private data class ActivePlayback(
        val track: AudioTrack,
        val stopRequested: AtomicBoolean,
        val writerThread: Thread,
    )

    private companion object {
        const val LOG_TAG = "BAD-Metronome"
        const val AUDIO_WRITER_THREAD_NAME = "BAD metronome writer"
        const val WRITER_STOP_TIMEOUT_MILLIS = 500L
    }
}
