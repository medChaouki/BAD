package com.titaniumharmonics.bad.audio.calibration

import com.titaniumharmonics.bad.audio.FinalizedRecording
import com.titaniumharmonics.bad.audio.PcmAudioFormat
import com.titaniumharmonics.bad.audio.PcmEncoding
import com.titaniumharmonics.bad.audio.WavFileMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TimingCalibrationProcessorTest {
    @Test
    fun generatedWavProducesExpectedOffsetAt48And441KHz() {
        listOf(48_000, 44_100).forEach { rate ->
            val config = detectorConfig()
            val expectedOffset = rate / 1_000 * 3
            val (pcm, _) = recordedClicks(rate, config, expectedOffset, noiseAmplitude = 50)
            val recording = writeWav(pcm, rate)
            val result = TimingCalibrationProcessor(config).process(recording, 0L, 123L)
            assertTrue(result is CalibrationProcessingResult.Success)
            result as CalibrationProcessingResult.Success
            assertEquals(expectedOffset.toLong(), result.calibration.offsetSamples)
            assertEquals(rate, result.calibration.sampleRateHz)
            assertEquals(123L, result.calibration.calibratedAtEpochMillis)
        }
    }

    @Test
    fun invalidWavFailsWithoutCreatingCalibration() {
        val file = File.createTempFile("bad-calibration", ".wav").apply { writeText("invalid") }
        val format = PcmAudioFormat(48_000, 1, PcmEncoding.SIGNED_16_BIT_LITTLE_ENDIAN)
        val result = TimingCalibrationProcessor(detectorConfig()).process(
            FinalizedRecording(file.absolutePath, format, 1L),
            0L,
            0L,
        )
        assertEquals(
            CalibrationFailureReason.INVALID_RECORDING,
            (result as CalibrationProcessingResult.Failure).reason,
        )
    }

    @Test
    fun inconsistentButMeasuredClicksProduceReviewableCalibration() {
        val sampleRate = 48_000
        val config = detectorConfig(clickCount = 3, minimumMatchedClicks = 2)
        val pcm = recordedClicksWithOffsets(
            sampleRateHz = sampleRate,
            configuration = config,
            offsets = intArrayOf(0, 480, 960),
        )

        val result = TimingCalibrationProcessor(config).process(
            recording = writeWav(pcm, sampleRate),
            playbackStartSampleFrame = 0L,
            calibratedAtEpochMillis = 456L,
        ) as CalibrationProcessingResult.Failure

        assertEquals(CalibrationFailureReason.INCONSISTENT_TIMING, result.reason)
        assertEquals(480L, result.reviewableCalibration?.offsetSamples)
        assertEquals(CalibrationConfidence.LOW, result.reviewableCalibration?.confidence)
        assertEquals(480L, result.diagnostics?.medianOffsetSamples)
    }

    private fun writeWav(samples: ShortArray, sampleRateHz: Int): FinalizedRecording {
        val file = File.createTempFile("bad-calibration", ".wav").apply { deleteOnExit() }
        file.outputStream().buffered().use { output ->
            output.write(ByteArray(44))
            samples.forEach { sample ->
                output.write(sample.toInt() and 0xff)
                output.write(sample.toInt() ushr 8 and 0xff)
            }
        }
        val format = PcmAudioFormat(
            sampleRateHz,
            1,
            PcmEncoding.SIGNED_16_BIT_LITTLE_ENDIAN,
        )
        WavFileMetadata.finalizePcm16(file, samples.size * 2L, format)
        return FinalizedRecording(file.absolutePath, format, samples.size.toLong())
    }
}
