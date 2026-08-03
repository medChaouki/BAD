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
        val format = PcmAudioFormat(
            sampleRateHz = 44_100,
            channelCount = 1,
            encoding = PcmEncoding.SIGNED_16_BIT_LITTLE_ENDIAN,
        )
        val pcmByteCount = format.sampleRateHz * format.bytesPerFrame
        file.writeBytes(ByteArray(44 + pcmByteCount))

        WavFileMetadata.finalizePcm16(
            file = file,
            pcmByteCount = pcmByteCount.toLong(),
            format = format,
        )

        assertEquals("RIFF", file.readBytes().copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals(1_000L, WavFileMetadata.readDurationMillis(file))
        assertTrue(file.length() == 44L + pcmByteCount)
    }
}
