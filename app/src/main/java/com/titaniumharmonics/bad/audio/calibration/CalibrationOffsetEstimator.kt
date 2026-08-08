package com.titaniumharmonics.bad.audio.calibration

import kotlin.math.abs

class CalibrationOffsetEstimator(
    private val configuration: TimingCalibrationConfig = TimingCalibrationConfig(),
) {
    fun estimate(
        matches: List<CalibrationClickMatch>,
        sampleRateHz: Int,
        calibratedAtEpochMillis: Long,
    ): Result<TimingCalibration> {
        if (matches.size < configuration.minimumMatchedClicks) {
            return Result.failure(CalibrationEstimationException(CalibrationFailureReason.TOO_FEW_CLICKS))
        }
        val initialMedian = TimingCalibrationMath.median(matches.map { it.offsetSamples })
        val maximumDeviationSamples = millisecondsToSamples(
            configuration.maximumOffsetSpreadMillis.toLong(),
            sampleRateHz,
        )
        val inliers = matches.filter {
            abs(it.offsetSamples - initialMedian) <= maximumDeviationSamples
        }
        if (inliers.size < configuration.minimumMatchedClicks) {
            return Result.failure(CalibrationEstimationException(CalibrationFailureReason.INCONSISTENT_TIMING))
        }
        val median = TimingCalibrationMath.median(inliers.map { it.offsetSamples })
        val offsets = inliers.map { it.offsetSamples }
        val spread = offsets.max() - offsets.min()
        val maximumSpread = configuration.maximumOffsetSpreadMillis * sampleRateHz / 1_000.0
        if (spread > maximumSpread) {
            return Result.failure(CalibrationEstimationException(CalibrationFailureReason.INCONSISTENT_TIMING))
        }
        val spacingErrorLimit = configuration.maximumSpacingErrorMillis * sampleRateHz / 1_000.0
        val spacingIsValid = inliers.zipWithNext().all { (first, second) ->
            val expectedSpacing = second.expectedSample - first.expectedSample
            val detectedSpacing = second.detectedSample - first.detectedSample
            abs(detectedSpacing - expectedSpacing) <= spacingErrorLimit
        }
        if (!spacingIsValid) {
            return Result.failure(CalibrationEstimationException(CalibrationFailureReason.INCONSISTENT_TIMING))
        }
        if (median <= 0L) {
            return Result.failure(
                CalibrationEstimationException(
                    reason = CalibrationFailureReason.NON_POSITIVE_OFFSET,
                    measuredOffsetSamples = median,
                    matchedClickCount = inliers.size,
                    offsetSpreadSamples = spread,
                ),
            )
        }
        val medianCorrelation = inliers.map { (it.correlation * 1_000_000).toLong() }
            .let(TimingCalibrationMath::median) / 1_000_000.0
        val highSpread = configuration.highConfidenceSpreadMillis * sampleRateHz / 1_000.0
        val confidence = if (
            inliers.size == configuration.clickCount &&
            medianCorrelation >= configuration.highConfidenceCorrelation &&
            spread <= highSpread
        ) {
            CalibrationConfidence.HIGH
        } else {
            CalibrationConfidence.MEDIUM
        }
        return Result.success(
            TimingCalibration(
                offsetSamples = median,
                sampleRateHz = sampleRateHz,
                confidence = confidence,
                expectedClickCount = configuration.clickCount,
                matchedClickCount = inliers.size,
                offsetSpreadSamples = spread,
                calibratedAtEpochMillis = calibratedAtEpochMillis,
                algorithmVersion = configuration.algorithmVersion,
            ),
        )
    }
}

class CalibrationEstimationException(
    val reason: CalibrationFailureReason,
    val measuredOffsetSamples: Long? = null,
    val matchedClickCount: Int? = null,
    val offsetSpreadSamples: Long? = null,
) : Exception(reason.userMessage)
