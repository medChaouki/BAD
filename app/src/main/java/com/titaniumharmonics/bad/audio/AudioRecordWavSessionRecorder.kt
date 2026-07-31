package com.titaniumharmonics.bad.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Environment
import androidx.annotation.RequiresPermission
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean

class AudioRecordWavSessionRecorder(
    context: Context,
) : SessionAudioRecorder {
    private val recordingsDirectory = File(
        context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir,
        RECORDINGS_DIRECTORY,
    )
    private val partialFile = File(recordingsDirectory, PARTIAL_FILE_NAME)
    private val recordingFile = File(recordingsDirectory, RECORDING_FILE_NAME)

    private var output: BufferedOutputStream? = null
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private val captureRequested = AtomicBoolean(false)
    private var pcmByteCount = 0L
    @Volatile private var captureFailure: Throwable? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @Synchronized
    override fun start() {
        cancel()
        check(recordingsDirectory.exists() || recordingsDirectory.mkdirs()) {
            "Unable to create the recording directory."
        }
        recordingFile.delete()
        output = BufferedOutputStream(FileOutputStream(partialFile)).also { stream ->
            stream.write(ByteArray(WAV_HEADER_SIZE))
        }
        pcmByteCount = 0L
        captureFailure = null
        startCapture()
    }

    @Synchronized
    override fun pause() {
        stopCapture()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @Synchronized
    override fun resume() {
        check(output != null) { "No recording session is active." }
        if (audioRecord == null) startCapture()
    }

    @Synchronized
    override fun finish(): DebugRecording {
        stopCapture()
        val failure = captureFailure
        output?.flush()
        output?.close()
        output = null
        if (failure != null || pcmByteCount <= 0L) {
            partialFile.delete()
            throw IllegalStateException(
                failure?.message ?: "Microphone recording did not contain audio.",
                failure,
            )
        }
        WavFileMetadata.finalizePcm16Mono(
            file = partialFile,
            pcmByteCount = pcmByteCount,
            sampleRateHz = SAMPLE_RATE_HZ,
        )
        check(partialFile.renameTo(recordingFile)) {
            "Unable to finalize the WAV recording."
        }
        return DebugRecording(
            filePath = recordingFile.absolutePath,
            durationMillis = WavFileMetadata.readDurationMillis(recordingFile),
        )
    }

    @Synchronized
    override fun cancel() {
        stopCapture()
        runCatching { output?.close() }
        output = null
        partialFile.delete()
        pcmByteCount = 0L
        captureFailure = null
    }

    override fun release() {
        cancel()
    }

    @SuppressLint("MissingPermission")
    private fun startCapture() {
        val minimumBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minimumBufferSize > 0) {
            "Device does not support 48 kHz mono PCM microphone input."
        }
        val recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.DEFAULT)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE_HZ)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minimumBufferSize * 2)
            .build()
        check(recorder.state == AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            "Unable to initialize microphone recording."
        }
        captureRequested.set(true)
        recorder.startRecording()
        check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            captureRequested.set(false)
            recorder.release()
            "Unable to start microphone recording."
        }
        audioRecord = recorder
        captureThread = Thread(
            { captureLoop(recorder, minimumBufferSize) },
            CAPTURE_THREAD_NAME,
        ).also(Thread::start)
    }

    private fun captureLoop(recorder: AudioRecord, bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        try {
            while (captureRequested.get()) {
                val bytesRead = recorder.read(buffer, 0, buffer.size)
                when {
                    bytesRead > 0 -> {
                        output?.write(buffer, 0, bytesRead)
                        pcmByteCount += bytesRead
                    }
                    bytesRead < 0 && captureRequested.get() -> error(
                        "Microphone read failed with AudioRecord error $bytesRead.",
                    )
                }
            }
        } catch (exception: Throwable) {
            captureFailure = exception
            captureRequested.set(false)
        }
    }

    private fun stopCapture() {
        val recorder = audioRecord ?: return
        audioRecord = null
        captureRequested.set(false)
        runCatching { recorder.stop() }
        captureThread?.join(CAPTURE_STOP_TIMEOUT_MILLIS)
        captureThread = null
        recorder.release()
    }

    companion object {
        const val SAMPLE_RATE_HZ = 48_000
        private const val RECORDINGS_DIRECTORY = "B.A.D/recordings"
        private const val PARTIAL_FILE_NAME = "debug-recording.partial"
        private const val RECORDING_FILE_NAME = "debug-recording.wav"
        private const val WAV_HEADER_SIZE = 44
        private const val CAPTURE_THREAD_NAME = "BAD microphone capture"
        private const val CAPTURE_STOP_TIMEOUT_MILLIS = 1_000L
    }
}

object WavFileMetadata {
    fun finalizePcm16Mono(file: File, pcmByteCount: Long, sampleRateHz: Int) {
        require(pcmByteCount in 1..(UInt.MAX_VALUE.toLong() - 36L))
        RandomAccessFile(file, "rw").use { wav ->
            wav.seek(0)
            wav.writeAscii("RIFF")
            wav.writeLittleEndianInt((36L + pcmByteCount).toInt())
            wav.writeAscii("WAVEfmt ")
            wav.writeLittleEndianInt(16)
            wav.writeLittleEndianShort(1)
            wav.writeLittleEndianShort(1)
            wav.writeLittleEndianInt(sampleRateHz)
            wav.writeLittleEndianInt(sampleRateHz * 2)
            wav.writeLittleEndianShort(2)
            wav.writeLittleEndianShort(16)
            wav.writeAscii("data")
            wav.writeLittleEndianInt(pcmByteCount.toInt())
        }
    }

    fun readDurationMillis(file: File): Long = RandomAccessFile(file, "r").use { wav ->
        require(wav.length() >= 44L) { "WAV file is incomplete." }
        wav.seek(24)
        val sampleRateHz = wav.readLittleEndianInt().toLong() and 0xffffffffL
        wav.seek(40)
        val pcmByteCount = wav.readLittleEndianInt().toLong() and 0xffffffffL
        require(sampleRateHz > 0L) { "WAV sample rate is invalid." }
        pcmByteCount * 1_000L / (sampleRateHz * 2L)
    }

    private fun RandomAccessFile.writeAscii(value: String) = write(value.toByteArray(Charsets.US_ASCII))
    private fun RandomAccessFile.writeLittleEndianInt(value: Int) {
        write(value and 0xff)
        write(value ushr 8 and 0xff)
        write(value ushr 16 and 0xff)
        write(value ushr 24 and 0xff)
    }
    private fun RandomAccessFile.writeLittleEndianShort(value: Int) {
        write(value and 0xff)
        write(value ushr 8 and 0xff)
    }
    private fun RandomAccessFile.readLittleEndianInt(): Int =
        readUnsignedByte() or
            (readUnsignedByte() shl 8) or
            (readUnsignedByte() shl 16) or
            (readUnsignedByte() shl 24)
}
