package com.titaniumharmonics.bad.audio.analysis

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WavPcm16ReaderTest {
    private val reader = WavPcm16Reader()

    @Test
    fun readsMonoPcm16At48kAnd44100() {
        listOf(48_000, 44_100).forEach { sampleRate ->
            val expected = shortArrayOf(Short.MIN_VALUE, -1, 0, 1, Short.MAX_VALUE)
            val decoded = reader.read(writeTestWav(expected, sampleRateHz = sampleRate))

            assertEquals(sampleRate, decoded.format.sampleRateHz)
            assertEquals(1, decoded.format.channelCount)
            assertEquals(expected.size.toLong(), decoded.sampleFrameCount)
            assertArrayEquals(expected, decoded.copyRange(0, decoded.sampleFrameCount))
        }
    }

    @Test
    fun skipsUnknownOddPaddedChunkBeforeData() {
        val decoded = reader.read(
            writeTestWav(
                samples = shortArrayOf(100, -100),
                unknownOddChunkBeforeData = true,
            ),
        )

        assertEquals(100, decoded.sampleAt(0).toInt())
        assertEquals(-100, decoded.sampleAt(1).toInt())
    }

    @Test
    fun rejectsInvalidRiffAndWaveHeaders() {
        val invalidRiff = tempFile("NOPE0000WAVE".toByteArray())
        assertThrows(AudioAnalysisException.InvalidWav::class.java) {
            reader.read(invalidRiff)
        }

        val invalidWave = writeTestWav(shortArrayOf(1)).apply {
            val bytes = readBytes()
            "NOPE".toByteArray().copyInto(bytes, destinationOffset = 8)
            writeBytes(bytes)
        }
        assertThrows(AudioAnalysisException.InvalidWav::class.java) {
            reader.read(invalidWave)
        }
    }

    @Test
    fun rejectsMissingFormatOrDataChunk() {
        assertThrows(AudioAnalysisException.InvalidWav::class.java) {
            reader.read(writeTestWav(shortArrayOf(1), includeFormat = false))
        }
        assertThrows(AudioAnalysisException.InvalidWav::class.java) {
            reader.read(writeTestWav(shortArrayOf(1), includeData = false))
        }
    }

    @Test
    fun rejectsStereoAndUnsupportedBitDepth() {
        assertThrows(AudioAnalysisException.UnsupportedWav::class.java) {
            reader.read(writeTestWav(shortArrayOf(1, 2), channelCount = 2))
        }
        assertThrows(AudioAnalysisException.UnsupportedWav::class.java) {
            reader.read(writeTestWav(shortArrayOf(1), bitsPerSample = 8))
        }
    }

    @Test
    fun rejectsTruncatedPcmData() {
        assertThrows(AudioAnalysisException.InvalidWav::class.java) {
            reader.read(
                writeTestWav(
                    samples = shortArrayOf(1, 2),
                    truncateLastDataByte = true,
                ),
            )
        }
    }

    @Test
    fun missingFileReturnsDomainFailure() {
        val missing = File(Files.createTempDirectory("bad-missing-wav").toFile(), "missing.wav")
        assertThrows(AudioAnalysisException.MissingWav::class.java) {
            reader.read(missing)
        }
    }

    private fun tempFile(bytes: ByteArray): File =
        File(Files.createTempDirectory("bad-invalid-wav").toFile(), "invalid.wav").also {
            it.writeBytes(bytes)
        }
}
