package com.titaniumharmonics.bad.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.annotation.WorkerThread
import com.titaniumharmonics.bad.exercise.RuntimeExercise
import com.titaniumharmonics.bad.audio.metronome.MetronomeConfiguration
import com.titaniumharmonics.bad.timing.MonotonicClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport
import kotlin.math.min

class AudioTrackMetronome(
    private val clock: MonotonicClock,
) : MetronomePlayer {
    private var activePlayback: ActivePlayback? = null
    private var resumeCountInPlayback: ActivePlayback? = null

    @WorkerThread
    @Synchronized
    override fun start(
        exercise: RuntimeExercise,
        downbeatsOnly: Boolean,
        configuration: MetronomeConfiguration,
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
            configuration = configuration,
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

            val playbackControl = PlaybackControl()
            val writerThread = Thread(
                {
                    writeRemainingSamples(
                        track = track,
                        samples = samples,
                        initialOffset = initialSampleCount,
                        playbackControl = playbackControl,
                    )
                },
                AUDIO_WRITER_THREAD_NAME,
            )

            track.play()
            val playbackStartedNanos = clock.nowNanos()
            activePlayback = ActivePlayback(
                track = track,
                playbackControl = playbackControl,
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

    @WorkerThread
    @Synchronized
    override fun pause() {
        val playback = activePlayback ?: return
        if (playback.track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            playback.playbackControl.pauseRequested.set(true)
            playback.track.pause()
        }
    }

    @WorkerThread
    @Synchronized
    override fun startResumeCountIn(
        exercise: RuntimeExercise,
        configuration: MetronomeConfiguration,
    ): Long {
        val playback = checkNotNull(activePlayback) {
            "Cannot start a resume count-in without active playback."
        }
        check(playback.track.playState == AudioTrack.PLAYSTATE_PAUSED) {
            "Cannot start a resume count-in unless playback is paused."
        }
        stopResumeCountIn()

        val sampleRateHz = ClickTrackGenerator.DEFAULT_SAMPLE_RATE_HZ
        val samples = ClickTrackGenerator.generateCountIn(
            exercise = exercise,
            sampleRateHz = sampleRateHz,
            configuration = configuration,
        )
        val minimumBufferSizeBytes = AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minimumBufferSizeBytes > 0) {
            "Device does not support resume count-in audio output."
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(metronomeAudioAttributes())
            .setAudioFormat(monoPcmFormat(sampleRateHz))
            .setBufferSizeInBytes(minimumBufferSizeBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            throw IllegalStateException(
                "Unable to initialise resume count-in audio output.",
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
            check(writtenSampleCount == initialSampleCount) {
                "Resume count-in buffer write was incomplete: " +
                    "$writtenSampleCount of $initialSampleCount samples."
            }

            val playbackControl = PlaybackControl()
            val writerThread = Thread(
                {
                    writeRemainingSamples(
                        track = track,
                        samples = samples,
                        initialOffset = initialSampleCount,
                        playbackControl = playbackControl,
                    )
                },
                RESUME_COUNT_IN_WRITER_THREAD_NAME,
            )
            resumeCountInPlayback = ActivePlayback(
                track = track,
                playbackControl = playbackControl,
                writerThread = writerThread,
            )
            track.play()
            writerThread.start()
            return clock.nowNanos()
        } catch (exception: Exception) {
            resumeCountInPlayback = null
            track.release()
            throw exception
        }
    }

    @WorkerThread
    @Synchronized
    override fun resume() {
        stopResumeCountIn()
        val playback = activePlayback ?: return
        if (playback.track.playState == AudioTrack.PLAYSTATE_PAUSED) {
            playback.track.play()
            playback.playbackControl.pauseRequested.set(false)
        }
    }

    @Synchronized
    override fun stop() {
        stopResumeCountIn()
        val playback = activePlayback ?: return
        activePlayback = null
        playback.playbackControl.stopRequested.set(true)
        playback.playbackControl.pauseRequested.set(false)

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
        playbackControl: PlaybackControl,
    ) {
        var sampleOffset = initialOffset
        while (sampleOffset < samples.size && !playbackControl.stopRequested.get()) {
            while (
                playbackControl.pauseRequested.get() &&
                !playbackControl.stopRequested.get()
            ) {
                LockSupport.parkNanos(WRITER_RETRY_DELAY_NANOS)
            }
            if (playbackControl.stopRequested.get()) return

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
            when {
                writtenSampleCount > 0 -> sampleOffset += writtenSampleCount
                writtenSampleCount == 0 -> {
                    // A blocking write may return zero when AudioTrack is paused.
                    // Keep the writer alive so playback can continue after resume.
                    LockSupport.parkNanos(WRITER_RETRY_DELAY_NANOS)
                }
                !playbackControl.stopRequested.get() -> {
                    Log.e(
                        LOG_TAG,
                        "Metronome stream write failed with AudioTrack error " +
                            "$writtenSampleCount.",
                    )
                    return
                }
            }
        }
    }

    private fun stopResumeCountIn() {
        val playback = resumeCountInPlayback ?: return
        resumeCountInPlayback = null
        playback.playbackControl.stopRequested.set(true)
        playback.playbackControl.pauseRequested.set(false)
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

    private fun metronomeAudioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

    private fun monoPcmFormat(sampleRateHz: Int): AudioFormat =
        AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRateHz)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

    private data class ActivePlayback(
        val track: AudioTrack,
        val playbackControl: PlaybackControl,
        val writerThread: Thread,
    )

    private data class PlaybackControl(
        val stopRequested: AtomicBoolean = AtomicBoolean(false),
        val pauseRequested: AtomicBoolean = AtomicBoolean(false),
    )

    private companion object {
        const val LOG_TAG = "BAD-Metronome"
        const val AUDIO_WRITER_THREAD_NAME = "BAD metronome writer"
        const val RESUME_COUNT_IN_WRITER_THREAD_NAME = "BAD resume count-in writer"
        const val WRITER_STOP_TIMEOUT_MILLIS = 500L
        const val WRITER_RETRY_DELAY_NANOS = 2_000_000L
    }
}
