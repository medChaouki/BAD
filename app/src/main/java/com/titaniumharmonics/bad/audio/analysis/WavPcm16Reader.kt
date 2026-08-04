package com.titaniumharmonics.bad.audio.analysis

import com.titaniumharmonics.bad.audio.PcmAudioFormat
import com.titaniumharmonics.bad.audio.PcmEncoding
import com.titaniumharmonics.bad.audio.RecordedSession
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.math.abs

class WavPcm16Reader {
    fun read(file: File): Pcm16AudioData {
        if (!file.isFile) throw AudioAnalysisException.MissingWav(file.absolutePath)
        try {
            return RandomAccessFile(file, "r").use(::read)
        } catch (exception: AudioAnalysisException) {
            throw exception
        } catch (exception: EOFException) {
            throw AudioAnalysisException.InvalidWav("WAV file is truncated.")
        } catch (exception: IOException) {
            throw AudioAnalysisException.FileReadFailure(
                "Unable to read recorded WAV file.",
                exception,
            )
        } catch (exception: SecurityException) {
            throw AudioAnalysisException.FileReadFailure(
                "Recorded WAV file cannot be accessed.",
                exception,
            )
        }
    }

    private fun read(wav: RandomAccessFile): Pcm16AudioData {
        if (wav.length() < RIFF_HEADER_BYTES) {
            throw AudioAnalysisException.InvalidWav("WAV file is too short for a RIFF header.")
        }
        if (wav.readFourCc() != "RIFF") {
            throw AudioAnalysisException.InvalidWav("WAV file does not start with RIFF.")
        }
        val riffPayloadBytes = wav.readUnsignedLittleEndianInt()
        if (wav.readFourCc() != "WAVE") {
            throw AudioAnalysisException.InvalidWav("RIFF file is not a WAVE document.")
        }
        val riffEnd = try {
            Math.addExact(8L, riffPayloadBytes)
        } catch (_: ArithmeticException) {
            throw AudioAnalysisException.InvalidWav("RIFF size is outside supported bounds.")
        }
        if (riffEnd > wav.length()) {
            throw AudioAnalysisException.InvalidWav("RIFF payload is truncated.")
        }

        var format: PcmAudioFormat? = null
        var dataOffset: Long? = null
        var dataSizeBytes: Long? = null
        while (wav.filePointer + CHUNK_HEADER_BYTES <= riffEnd) {
            val chunkId = wav.readFourCc()
            val chunkSize = wav.readUnsignedLittleEndianInt()
            val chunkDataStart = wav.filePointer
            val chunkDataEnd = try {
                Math.addExact(chunkDataStart, chunkSize)
            } catch (_: ArithmeticException) {
                throw AudioAnalysisException.InvalidWav("WAV chunk size overflowed.")
            }
            if (chunkDataEnd > riffEnd) {
                throw AudioAnalysisException.InvalidWav("WAV chunk '$chunkId' is truncated.")
            }
            when (chunkId) {
                "fmt " -> format = readFormatChunk(wav, chunkSize)
                "data" -> if (dataOffset == null) {
                    dataOffset = chunkDataStart
                    dataSizeBytes = chunkSize
                }
            }
            val paddedEnd = chunkDataEnd + (chunkSize and 1L)
            if (paddedEnd > riffEnd) {
                throw AudioAnalysisException.InvalidWav("WAV chunk padding is truncated.")
            }
            wav.seek(paddedEnd)
        }
        if (wav.filePointer != riffEnd) {
            throw AudioAnalysisException.InvalidWav("WAV contains an incomplete chunk header.")
        }

        val decodedFormat = format
            ?: throw AudioAnalysisException.InvalidWav("WAV file has no fmt chunk.")
        val pcmOffset = dataOffset
            ?: throw AudioAnalysisException.InvalidWav("WAV file has no data chunk.")
        val pcmBytes = dataSizeBytes ?: 0L
        if (pcmBytes == 0L || pcmBytes % decodedFormat.bytesPerFrame != 0L) {
            throw AudioAnalysisException.InvalidWav(
                "WAV data does not contain complete PCM sample frames.",
            )
        }
        val sampleFrames = pcmBytes / decodedFormat.bytesPerFrame
        if (sampleFrames > Int.MAX_VALUE) {
            throw AudioAnalysisException.UnsupportedWav("WAV recording is too large to decode.")
        }
        wav.seek(pcmOffset)
        val samples = ShortArray(sampleFrames.toInt()) {
            wav.readLittleEndianShort().toShort()
        }
        return Pcm16AudioData.fromOwnedSamples(decodedFormat, samples)
    }

    private fun readFormatChunk(wav: RandomAccessFile, chunkSize: Long): PcmAudioFormat {
        if (chunkSize < PCM_FORMAT_CHUNK_BYTES) {
            throw AudioAnalysisException.InvalidWav("WAV fmt chunk is truncated.")
        }
        val audioFormatCode = wav.readLittleEndianUnsignedShort()
        val channelCount = wav.readLittleEndianUnsignedShort()
        val sampleRate = wav.readUnsignedLittleEndianInt()
        wav.readUnsignedLittleEndianInt() // Byte rate is redundant for supported PCM.
        val blockAlign = wav.readLittleEndianUnsignedShort()
        val bitsPerSample = wav.readLittleEndianUnsignedShort()

        if (audioFormatCode != PCM_FORMAT_CODE) {
            throw AudioAnalysisException.UnsupportedWav(
                "WAV encoding $audioFormatCode is unsupported; PCM is required.",
            )
        }
        if (channelCount != SUPPORTED_CHANNEL_COUNT) {
            throw AudioAnalysisException.UnsupportedWav(
                "WAV channel count $channelCount is unsupported; mono is required.",
            )
        }
        if (bitsPerSample != SUPPORTED_BITS_PER_SAMPLE) {
            throw AudioAnalysisException.UnsupportedWav(
                "WAV bit depth $bitsPerSample is unsupported; 16-bit PCM is required.",
            )
        }
        if (sampleRate !in 1L..Int.MAX_VALUE) {
            throw AudioAnalysisException.InvalidWav("WAV sample rate is invalid.")
        }
        if (blockAlign != EXPECTED_BLOCK_ALIGN) {
            throw AudioAnalysisException.InvalidWav("WAV block alignment is invalid.")
        }
        return PcmAudioFormat(
            sampleRateHz = sampleRate.toInt(),
            channelCount = channelCount,
            encoding = PcmEncoding.SIGNED_16_BIT_LITTLE_ENDIAN,
        )
    }

    private fun RandomAccessFile.readFourCc(): String {
        val bytes = ByteArray(4)
        readFully(bytes)
        return bytes.toString(Charsets.US_ASCII)
    }

    private fun RandomAccessFile.readUnsignedLittleEndianInt(): Long =
        readLittleEndianInt().toLong() and 0xffff_ffffL

    private fun RandomAccessFile.readLittleEndianInt(): Int =
        readUnsignedByte() or
            (readUnsignedByte() shl 8) or
            (readUnsignedByte() shl 16) or
            (readUnsignedByte() shl 24)

    private fun RandomAccessFile.readLittleEndianUnsignedShort(): Int =
        readUnsignedByte() or (readUnsignedByte() shl 8)

    private fun RandomAccessFile.readLittleEndianShort(): Int =
        readUnsignedByte() or (readUnsignedByte() shl 8)

    private companion object {
        const val RIFF_HEADER_BYTES = 12L
        const val CHUNK_HEADER_BYTES = 8L
        const val PCM_FORMAT_CHUNK_BYTES = 16L
        const val PCM_FORMAT_CODE = 1
        const val SUPPORTED_CHANNEL_COUNT = 1
        const val SUPPORTED_BITS_PER_SAMPLE = 16
        const val EXPECTED_BLOCK_ALIGN = 2
    }
}

internal class RecordedSessionAudioValidator(
    private val sampleCountToleranceFrames: Long = 0L,
) {
    init {
        require(sampleCountToleranceFrames >= 0L)
    }

    fun validateAndExtract(
        session: RecordedSession,
        wav: Pcm16AudioData,
    ): ValidatedGradedAudio {
        if (wav.format.sampleRateHz != session.audioFormat.sampleRateHz) {
            throw AudioAnalysisException.SessionMismatch(
                "WAV sample rate does not match the recorded session.",
            )
        }
        if (wav.format.channelCount != session.audioFormat.channelCount) {
            throw AudioAnalysisException.SessionMismatch(
                "WAV channel count does not match the recorded session.",
            )
        }
        val sampleCountDifference = abs(
            wav.sampleFrameCount - session.totalRecordedSampleFrames,
        )
        if (sampleCountDifference > sampleCountToleranceFrames) {
            throw AudioAnalysisException.SessionMismatch(
                "WAV sample-frame count does not match the recorded session.",
            )
        }
        val graded = GradedRangeExtractor.extract(
            wav = wav,
            exerciseStartSampleFrame = session.exerciseStartSampleFrame,
        )
        return ValidatedGradedAudio(session, graded)
    }
}

object GradedRangeExtractor {
    fun extract(wav: Pcm16AudioData, exerciseStartSampleFrame: Long): ShortArray {
        if (exerciseStartSampleFrame !in 0 until wav.sampleFrameCount) {
            throw AudioAnalysisException.SessionMismatch(
                "Exercise-start sample must precede the end of the WAV.",
            )
        }
        val graded = wav.copyRange(exerciseStartSampleFrame, wav.sampleFrameCount)
        if (graded.isEmpty()) {
            throw AudioAnalysisException.SessionMismatch("Graded WAV range is empty.")
        }
        return graded
    }
}
