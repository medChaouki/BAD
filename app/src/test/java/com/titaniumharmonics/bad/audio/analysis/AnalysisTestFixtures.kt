package com.titaniumharmonics.bad.audio.analysis

import com.titaniumharmonics.bad.audio.PcmAudioFormat
import com.titaniumharmonics.bad.audio.PcmEncoding
import com.titaniumharmonics.bad.audio.RecordedSession
import com.titaniumharmonics.bad.audio.runtimeExerciseForAudioTest
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

fun writeTestWav(
    samples: ShortArray,
    sampleRateHz: Int = 48_000,
    channelCount: Int = 1,
    bitsPerSample: Int = 16,
    includeFormat: Boolean = true,
    includeData: Boolean = true,
    unknownOddChunkBeforeData: Boolean = false,
    truncateLastDataByte: Boolean = false,
): File {
    val chunks = ByteArrayOutputStream()
    if (includeFormat) {
        val format = ByteArrayOutputStream().apply {
            writeLeShort(1)
            writeLeShort(channelCount)
            writeLeInt(sampleRateHz)
            val bytesPerSample = (bitsPerSample / 8).coerceAtLeast(1)
            writeLeInt(sampleRateHz * channelCount * bytesPerSample)
            writeLeShort(channelCount * bytesPerSample)
            writeLeShort(bitsPerSample)
        }.toByteArray()
        chunks.writeChunk("fmt ", format)
    }
    if (unknownOddChunkBeforeData) {
        chunks.writeChunk("JUNK", byteArrayOf(1, 2, 3))
    }
    if (includeData) {
        val pcm = ByteArrayOutputStream().apply {
            samples.forEach { sample ->
                write(sample.toInt() and 0xff)
                if (bitsPerSample >= 16) write(sample.toInt() ushr 8 and 0xff)
            }
        }.toByteArray()
        chunks.writeChunk("data", pcm, truncateLastDataByte)
    }
    val chunkBytes = chunks.toByteArray()
    val wav = ByteArrayOutputStream().apply {
        write("RIFF".toByteArray(Charsets.US_ASCII))
        writeLeInt(4 + chunkBytes.size)
        write("WAVE".toByteArray(Charsets.US_ASCII))
        write(chunkBytes)
    }.toByteArray()
    return File(Files.createTempDirectory("bad-analysis-wav").toFile(), "fixture.wav").also {
        it.writeBytes(wav)
    }
}

fun recordedSessionForWav(
    wav: File,
    sampleRateHz: Int,
    totalSampleFrames: Long,
    exerciseStartSampleFrame: Long,
    channelCount: Int = 1,
): RecordedSession = RecordedSession(
    wavFilePath = wav.absolutePath,
    audioFormat = PcmAudioFormat(
        sampleRateHz = sampleRateHz,
        channelCount = channelCount,
        encoding = PcmEncoding.SIGNED_16_BIT_LITTLE_ENDIAN,
    ),
    totalRecordedSampleFrames = totalSampleFrames,
    exerciseStartSampleFrame = exerciseStartSampleFrame,
    runtimeExercise = runtimeExerciseForAudioTest(),
)

private fun ByteArrayOutputStream.writeChunk(
    id: String,
    data: ByteArray,
    truncateLastByte: Boolean = false,
) {
    write(id.toByteArray(Charsets.US_ASCII))
    writeLeInt(data.size)
    val writtenData = if (truncateLastByte && data.isNotEmpty()) {
        data.copyOf(data.size - 1)
    } else {
        data
    }
    write(writtenData)
    if (data.size % 2 != 0 && !truncateLastByte) write(0)
}

private fun ByteArrayOutputStream.writeLeInt(value: Int) {
    write(value and 0xff)
    write(value ushr 8 and 0xff)
    write(value ushr 16 and 0xff)
    write(value ushr 24 and 0xff)
}

private fun ByteArrayOutputStream.writeLeShort(value: Int) {
    write(value and 0xff)
    write(value ushr 8 and 0xff)
}
