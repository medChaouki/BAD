package com.titaniumharmonics.bad.audio.calibration

import com.titaniumharmonics.bad.audio.FinalizedRecording
import com.titaniumharmonics.bad.audio.analysis.AudioAnalysisException
import com.titaniumharmonics.bad.audio.analysis.WavPcm16Reader
import java.io.File

class TimingCalibrationProcessor(
    private val configuration: TimingCalibrationConfig = TimingCalibrationConfig(),
    private val wavReader: WavPcm16Reader = WavPcm16Reader(),
    private val detector: CalibrationClickDetector = CalibrationClickDetector(configuration),
    private val estimator: CalibrationOffsetEstimator = CalibrationOffsetEstimator(configuration),
) {
    fun process(
        recording: FinalizedRecording,
        playbackStartSampleFrame: Long,
        calibratedAtEpochMillis: Long,
        cancellationCheck: () -> Unit = {},
    ): CalibrationProcessingResult {
        val wav = try {
            wavReader.read(File(recording.filePath))
        } catch (_: AudioAnalysisException) {
            return CalibrationProcessingResult.Failure(CalibrationFailureReason.INVALID_RECORDING)
        }
        if (
            wav.format != recording.format ||
            wav.sampleFrameCount != recording.totalSampleFrames ||
            playbackStartSampleFrame !in 0 until wav.sampleFrameCount
        ) {
            return CalibrationProcessingResult.Failure(CalibrationFailureReason.INVALID_RECORDING)
        }
        val expected = runCatching {
            configuration.expectedClickSamples(wav.format.sampleRateHz, playbackStartSampleFrame)
        }.getOrElse {
            return CalibrationProcessingResult.Failure(CalibrationFailureReason.INVALID_RECORDING)
        }
        if (expected.last() >= wav.sampleFrameCount) {
            return CalibrationProcessingResult.Failure(CalibrationFailureReason.INVALID_RECORDING)
        }
        cancellationCheck()
        val samples = wav.copyRange(0L, wav.sampleFrameCount)
        val matches = detector.detect(
            recordedPcm = samples,
            sampleRateHz = wav.format.sampleRateHz,
            expectedClickSamples = expected,
            cancellationCheck = cancellationCheck,
        )
        val estimate = estimator.estimate(matches, wav.format.sampleRateHz, calibratedAtEpochMillis)
        val calibration = estimate.getOrNull()
        val estimationFailure = estimate.exceptionOrNull() as? CalibrationEstimationException
        val reviewableCalibration = if (calibration == null) {
            createReviewableCalibration(
                matches = matches,
                sampleRateHz = wav.format.sampleRateHz,
                calibratedAtEpochMillis = calibratedAtEpochMillis,
            )
        } else {
            null
        }
        val measuredCalibration = calibration ?: reviewableCalibration
        val rawOffsets = matches.map(CalibrationClickMatch::offsetSamples)
        val diagnostics = CalibrationDiagnostics(
            wavFilePath = recording.filePath,
            sampleRateHz = wav.format.sampleRateHz,
            totalSampleFrames = wav.sampleFrameCount,
            expectedClickSamples = immutableList(expected.asList()),
            matches = immutableList(matches),
            medianOffsetSamples = measuredCalibration?.offsetSamples
                ?: estimationFailure?.measuredOffsetSamples
                ?: rawOffsets.takeIf(List<Long>::isNotEmpty)?.let(TimingCalibrationMath::median),
            offsetSpreadSamples = measuredCalibration?.offsetSpreadSamples
                ?: estimationFailure?.offsetSpreadSamples
                ?: rawOffsets.takeIf(List<Long>::isNotEmpty)?.let { it.max() - it.min() },
            waveform = downsampleWaveform(samples),
        )
        return if (calibration != null) {
            CalibrationProcessingResult.Success(calibration, diagnostics)
        } else {
            val reason = if (matches.isEmpty()) {
                CalibrationFailureReason.LOW_CORRELATION
            } else {
                estimationFailure?.reason
                    ?: CalibrationFailureReason.UNKNOWN
            }
            CalibrationProcessingResult.Failure(
                reason = reason,
                diagnostics = diagnostics,
                reviewableCalibration = reviewableCalibration,
            )
        }
    }

    private fun createReviewableCalibration(
        matches: List<CalibrationClickMatch>,
        sampleRateHz: Int,
        calibratedAtEpochMillis: Long,
    ): TimingCalibration? {
        if (matches.isEmpty()) return null
        val offsets = matches.map { it.offsetSamples }
        val median = TimingCalibrationMath.median(offsets)
        if (median <= 0L) return null
        return TimingCalibration(
            offsetSamples = median,
            sampleRateHz = sampleRateHz,
            confidence = CalibrationConfidence.LOW,
            expectedClickCount = configuration.clickCount,
            matchedClickCount = matches.size,
            offsetSpreadSamples = offsets.max() - offsets.min(),
            calibratedAtEpochMillis = calibratedAtEpochMillis,
            algorithmVersion = configuration.algorithmVersion,
        )
    }

    private fun downsampleWaveform(samples: ShortArray): List<CalibrationWaveformPoint> {
        val bucketSize = (samples.size + DEBUG_WAVEFORM_BUCKETS - 1) / DEBUG_WAVEFORM_BUCKETS
        val points = ArrayList<CalibrationWaveformPoint>(DEBUG_WAVEFORM_BUCKETS * 2)
        var start = 0
        while (start < samples.size) {
            val end = minOf(samples.size, start + bucketSize)
            var minimum = start
            var maximum = start
            for (index in start + 1 until end) {
                if (samples[index] < samples[minimum]) minimum = index
                if (samples[index] > samples[maximum]) maximum = index
            }
            listOf(minimum, maximum).distinct().sorted().forEach { index ->
                points += CalibrationWaveformPoint(
                    sampleFrame = index.toLong(),
                    normalizedAmplitude = if (samples[index] < 0) {
                        samples[index] / 32_768.0f
                    } else {
                        samples[index] / 32_767.0f
                    },
                )
            }
            start = end
        }
        return immutableList(points)
    }

    private companion object {
        const val DEBUG_WAVEFORM_BUCKETS = 750
    }
}
