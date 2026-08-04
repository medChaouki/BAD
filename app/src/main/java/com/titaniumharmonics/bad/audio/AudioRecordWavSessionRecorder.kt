package com.titaniumharmonics.bad.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioDeviceInfo
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
    recordingsDirectoryName: String = RECORDINGS_DIRECTORY,
    partialFileName: String = PARTIAL_FILE_NAME,
    recordingFileName: String = RECORDING_FILE_NAME,
    private val preferredInputDevice: AudioDeviceInfo? = null,
) : SessionAudioRecorder {
    private val recordingsDirectory = File(
        context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir,
        recordingsDirectoryName,
    )
    private val partialFile = File(recordingsDirectory, partialFileName)
    private val recordingFile = File(recordingsDirectory, recordingFileName)

    private var output: BufferedOutputStream? = null
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private val captureRequested = AtomicBoolean(false)
    private val sampleWriteLock = Any()
    @Volatile private var currentFormat: PcmAudioFormat? = null
    private var writtenSampleFrames = 0L
    @Volatile private var captureFailure: Throwable? = null

    override val format: PcmAudioFormat?
        get() = currentFormat

    override val totalWrittenSampleFrames: Long
        get() = synchronized(sampleWriteLock) { writtenSampleFrames }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @Synchronized
    override fun start() {
        cancel()
        check(recordingsDirectory.exists() || recordingsDirectory.mkdirs()) {
            "Unable to create the recording directory."
        }
        recordingFile.delete()
        try {
            output = BufferedOutputStream(FileOutputStream(partialFile)).also { stream ->
                stream.write(ByteArray(WAV_HEADER_SIZE))
            }
            writtenSampleFrames = 0L
            captureFailure = null
            startCapture()
        } catch (exception: Exception) {
            cancel()
            throw exception
        }
    }

    @Synchronized
    override fun pause() {
        stopCapture()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @Synchronized
    override fun resume() {
        check(output != null) { "No recording session is active." }
        check(captureFailure == null) { "Microphone recording has already failed." }
        if (audioRecord == null) startCapture()
    }

    @Synchronized
    override fun finish(): FinalizedRecording {
        stopCapture()
        val failure = captureFailure
        val completedFormat = checkNotNull(currentFormat) {
            "Recording format is unavailable."
        }
        output?.flush()
        output?.close()
        output = null
        if (failure != null || writtenSampleFrames <= 0L) {
            partialFile.delete()
            throw IllegalStateException(
                failure?.message ?: "Microphone recording did not contain audio.",
                failure,
            )
        }
        val pcmByteCount = Math.multiplyExact(
            writtenSampleFrames,
            completedFormat.bytesPerFrame.toLong(),
        )
        WavFileMetadata.finalizePcm16(
            file = partialFile,
            pcmByteCount = pcmByteCount,
            format = completedFormat,
        )
        check(partialFile.renameTo(recordingFile)) {
            "Unable to finalize the WAV recording."
        }
        return FinalizedRecording(
            filePath = recordingFile.absolutePath,
            format = completedFormat,
            totalSampleFrames = writtenSampleFrames,
        )
    }

    @Synchronized
    override fun cancel() {
        stopCapture()
        runCatching { output?.close() }
        output = null
        partialFile.delete()
        currentFormat = null
        writtenSampleFrames = 0L
        captureFailure = null
    }

    override fun release() {
        cancel()
    }

    fun routedDeviceType(): Int? = audioRecord?.routedDevice?.type

    @SuppressLint("MissingPermission")
    private fun startCapture() {
        val requiredFormat = currentFormat
        val candidateSampleRates = requiredFormat?.let {
            listOf(it.sampleRateHz)
        } ?: SUPPORTED_SAMPLE_RATES
        var selectedSetup: CaptureSetup? = null
        var lastStartFailure: Exception? = null
        candidateSampleRates.forEach { sampleRateHz ->
            if (selectedSetup != null) return@forEach
            val setup = createCaptureSetup(sampleRateHz) ?: return@forEach
            if (requiredFormat != null && setup.format != requiredFormat) {
                setup.recorder.release()
                lastStartFailure = IllegalStateException(
                    "Microphone format changed while resuming recording.",
                )
                return@forEach
            }
            captureRequested.set(true)
            try {
                setup.recorder.startRecording()
                check(setup.recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    "Unable to start microphone recording."
                }
                selectedSetup = setup
            } catch (exception: SecurityException) {
                captureRequested.set(false)
                setup.recorder.release()
                throw exception
            } catch (exception: Exception) {
                captureRequested.set(false)
                setup.recorder.release()
                lastStartFailure = exception
            }
        }
        val captureSetup = selectedSetup ?: throw IllegalStateException(
            "Device does not support the required mono PCM microphone input.",
            lastStartFailure,
        )
        val recorder = captureSetup.recorder
        currentFormat = captureSetup.format
        audioRecord = recorder
        captureThread = Thread(
            { captureLoop(recorder, captureSetup.bufferSizeBytes, captureSetup.format) },
            CAPTURE_THREAD_NAME,
        ).also(Thread::start)
    }

    @SuppressLint("MissingPermission")
    private fun createCaptureSetup(requestedSampleRateHz: Int): CaptureSetup? {
        val minimumBufferSize = AudioRecord.getMinBufferSize(
            requestedSampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minimumBufferSize <= 0) return null
        val recorder = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.DEFAULT)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(requestedSampleRateHz)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(minimumBufferSize * BUFFER_SIZE_MULTIPLIER)
                .build()
        } catch (_: IllegalArgumentException) {
            return null
        } catch (_: UnsupportedOperationException) {
            return null
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return null
        }
        if (preferredInputDevice != null && !recorder.setPreferredDevice(preferredInputDevice)) {
            recorder.release()
            return null
        }
        val format = PcmAudioFormat(
            sampleRateHz = recorder.sampleRate,
            channelCount = recorder.channelCount,
            encoding = PcmEncoding.SIGNED_16_BIT_LITTLE_ENDIAN,
        )
        if (format.channelCount != CHANNEL_COUNT) {
            recorder.release()
            return null
        }
        return CaptureSetup(
            recorder = recorder,
            bufferSizeBytes = minimumBufferSize * BUFFER_SIZE_MULTIPLIER,
            format = format,
        )
    }

    private fun captureLoop(
        recorder: AudioRecord,
        bufferSize: Int,
        format: PcmAudioFormat,
    ) {
        val buffer = ByteArray(bufferSize)
        try {
            while (captureRequested.get()) {
                val bytesRead = recorder.read(buffer, 0, buffer.size)
                when {
                    bytesRead > 0 -> {
                        check(bytesRead % format.bytesPerFrame == 0) {
                            "Microphone returned an incomplete PCM sample frame."
                        }
                        synchronized(sampleWriteLock) {
                            checkNotNull(output) {
                                "Recording output closed while capture was active."
                            }.write(buffer, 0, bytesRead)
                            writtenSampleFrames = Math.addExact(
                                writtenSampleFrames,
                                bytesRead.toLong() / format.bytesPerFrame,
                            )
                        }
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
            .onFailure { exception -> captureFailure = exception }
        val thread = captureThread
        thread?.join(CAPTURE_STOP_TIMEOUT_MILLIS)
        if (thread?.isAlive == true) {
            captureFailure = IllegalStateException(
                "Microphone capture thread did not stop cleanly.",
            )
            thread.interrupt()
        }
        captureThread = null
        recorder.release()
    }

    private data class CaptureSetup(
        val recorder: AudioRecord,
        val bufferSizeBytes: Int,
        val format: PcmAudioFormat,
    )

    companion object {
        const val PREFERRED_SAMPLE_RATE_HZ = 48_000
        const val FALLBACK_SAMPLE_RATE_HZ = 44_100
        private const val CHANNEL_COUNT = 1
        private const val BUFFER_SIZE_MULTIPLIER = 2
        private val SUPPORTED_SAMPLE_RATES = listOf(
            PREFERRED_SAMPLE_RATE_HZ,
            FALLBACK_SAMPLE_RATE_HZ,
        )
        const val RECORDINGS_DIRECTORY = "B.A.D/recordings"
        const val PARTIAL_FILE_NAME = "debug-recording.partial"
        const val RECORDING_FILE_NAME = "debug-recording.wav"
        private const val WAV_HEADER_SIZE = 44
        private const val CAPTURE_THREAD_NAME = "BAD microphone capture"
        private const val CAPTURE_STOP_TIMEOUT_MILLIS = 1_000L
    }
}

object WavFileMetadata {
    fun finalizePcm16(file: File, pcmByteCount: Long, format: PcmAudioFormat) {
        require(format.encoding == PcmEncoding.SIGNED_16_BIT_LITTLE_ENDIAN)
        require(pcmByteCount in 1..(UInt.MAX_VALUE.toLong() - 36L))
        require(pcmByteCount % format.bytesPerFrame == 0L) {
            "PCM data must contain complete sample frames."
        }
        RandomAccessFile(file, "rw").use { wav ->
            wav.seek(0)
            wav.writeAscii("RIFF")
            wav.writeLittleEndianInt((36L + pcmByteCount).toInt())
            wav.writeAscii("WAVEfmt ")
            wav.writeLittleEndianInt(16)
            wav.writeLittleEndianShort(1)
            wav.writeLittleEndianShort(format.channelCount)
            wav.writeLittleEndianInt(format.sampleRateHz)
            wav.writeLittleEndianInt(
                Math.multiplyExact(format.sampleRateHz, format.bytesPerFrame),
            )
            wav.writeLittleEndianShort(format.bytesPerFrame)
            wav.writeLittleEndianShort(format.encoding.bitsPerSample)
            wav.writeAscii("data")
            wav.writeLittleEndianInt(pcmByteCount.toInt())
        }
    }

    fun readDurationMillis(file: File): Long = RandomAccessFile(file, "r").use { wav ->
        require(wav.length() >= 44L) { "WAV file is incomplete." }
        wav.seek(22)
        val channelCount = wav.readLittleEndianShort()
        wav.seek(24)
        val sampleRateHz = wav.readLittleEndianInt().toLong() and 0xffffffffL
        wav.seek(34)
        val bitsPerSample = wav.readLittleEndianShort()
        wav.seek(40)
        val pcmByteCount = wav.readLittleEndianInt().toLong() and 0xffffffffL
        require(
            sampleRateHz in 1L..Int.MAX_VALUE &&
                channelCount > 0 &&
                bitsPerSample > 0 &&
                bitsPerSample % Byte.SIZE_BITS == 0,
        ) {
            "WAV audio format is invalid."
        }
        val bytesPerFrame = channelCount * (bitsPerSample / Byte.SIZE_BITS)
        require(bytesPerFrame > 0) { "WAV frame size is invalid." }
        require(pcmByteCount > 0L && pcmByteCount <= wav.length() - 44L) {
            "WAV PCM data is missing or incomplete."
        }
        val sampleFrames = pcmByteCount / bytesPerFrame
        SampleFrameTiming.sampleFramesToDurationNanos(
            sampleFrames = sampleFrames,
            sampleRateHz = sampleRateHz.toInt(),
        ) / 1_000_000L
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
    private fun RandomAccessFile.readLittleEndianShort(): Int =
        readUnsignedByte() or (readUnsignedByte() shl 8)
}
