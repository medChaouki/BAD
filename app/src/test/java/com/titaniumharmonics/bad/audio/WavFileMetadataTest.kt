package com.titaniumharmonics.bad.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WavFileMetadataTest {
    @Test
    fun finalizedHeader_reportsActualPcmDuration() {
        val file = File(Files.createTempDirectory("bad-wav").toFile(), "test.wav")
        val pcmByteCount = AudioRecordWavSessionRecorder.SAMPLE_RATE_HZ * 2
        file.writeBytes(ByteArray(44 + pcmByteCount))

        WavFileMetadata.finalizePcm16Mono(
            file = file,
            pcmByteCount = pcmByteCount.toLong(),
            sampleRateHz = AudioRecordWavSessionRecorder.SAMPLE_RATE_HZ,
        )

        assertEquals("RIFF", file.readBytes().copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals(1_000L, WavFileMetadata.readDurationMillis(file))
        assertTrue(file.length() == 44L + pcmByteCount)
    }
}

