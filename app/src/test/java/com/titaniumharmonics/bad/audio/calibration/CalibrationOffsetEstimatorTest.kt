package com.titaniumharmonics.bad.audio.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationOffsetEstimatorTest {
    private val config = detectorConfig(clickCount = 8, minimumMatchedClicks = 6)
    private val estimator = CalibrationOffsetEstimator(config)

    @Test
    fun acceptsPositiveOffsetAndRobustPositiveMedianWithNegativeOutlier() {
        val matches = (0 until 8).map { match(it * 24_000L, 2_400L) }
        assertEquals(2_400L, estimator.estimate(matches, 48_000, 123L).getOrThrow().offsetSamples)
        val varied = listOf(70L, 71L, 69L, 70L, 72L, 68L, 70L, -5_000L)
            .mapIndexed { index, offset -> match(index * 24_000L, offset) }
        assertEquals(70L, estimator.estimate(varied, 48_000, 123L).getOrThrow().offsetSamples)
    }

    @Test
    fun rejectsZeroAndNegativeFinalMedianBeforeConstructingSuccess() {
        listOf(0L, -1_200L).forEach { offset ->
            val matches = (0 until 8).map { match(it * 24_000L, offset) }
            val failure = estimator.estimate(matches, 48_000, 123L).exceptionOrNull()
                as CalibrationEstimationException
            assertEquals(CalibrationFailureReason.NON_POSITIVE_OFFSET, failure.reason)
            assertEquals(offset, failure.measuredOffsetSamples)
            assertEquals(8, failure.matchedClickCount)
            assertTrue(failure.reason.userMessage.contains("invalid timing offset"))
        }
    }

    @Test
    fun successfulCalibrationDomainRejectsNonPositiveConstruction() {
        listOf(0L, -1L).forEach { offset ->
            assertThrows(IllegalArgumentException::class.java) {
                TimingCalibration(
                    offset,
                    48_000,
                    CalibrationConfidence.HIGH,
                    8,
                    8,
                    0,
                    0,
                    TimingCalibrationConfig.CURRENT_ALGORITHM_VERSION,
                )
            }
        }
    }

    @Test
    fun rejectsTooFewClicksSeveralOutliersAndWrongSpacing() {
        val tooFew = (0 until 5).map { match(it * 24_000L, 50L) }
        assertEquals(CalibrationFailureReason.TOO_FEW_CLICKS, failureReason(tooFew))
        val outliers = (0 until 8).map { index -> match(index * 24_000L, if (index < 4) 0L else 2_000L) }
        assertEquals(CalibrationFailureReason.INCONSISTENT_TIMING, failureReason(outliers))
        val wrongSpacing = (0 until 8).map { index ->
            CalibrationClickMatch(index * 24_000L, index * 24_000L + index * 500L, 0.8)
        }
        assertEquals(CalibrationFailureReason.INCONSISTENT_TIMING, failureReason(wrongSpacing))
    }

    @Test
    fun calculatesHighAndMediumConfidenceAndPreservesTimestamp() {
        val high = estimator.estimate((0 until 8).map { match(it * 24_000L, 40L, 0.9) }, 48_000, 999L).getOrThrow()
        assertEquals(CalibrationConfidence.HIGH, high.confidence)
        assertEquals(999L, high.calibratedAtEpochMillis)
        val medium = estimator.estimate((0 until 6).map { match(it * 24_000L, 40L, 0.5) }, 48_000, 1_000L).getOrThrow()
        assertEquals(CalibrationConfidence.MEDIUM, medium.confidence)
    }

    @Test
    fun convertsMillisecondsSampleRatesAndRoundsTiesAwayFromZero() {
        val calibration = TimingCalibration(
            4_800L,
            48_000,
            CalibrationConfidence.HIGH,
            8,
            8,
            0,
            0,
            TimingCalibrationConfig.CURRENT_ALGORITHM_VERSION,
        )
        assertEquals(100.0, calibration.offsetMillis, 0.0)
        assertEquals(4_410L, calibration.offsetSamplesAt(44_100))
        assertTrue(calibration.offsetSamplesAt(44_100) > 0L)
        assertEquals(4_700L, calibration.calibrateHit(9_500L, 48_000))
        assertEquals(1L, TimingCalibrationMath.convertSampleRate(1L, 2, 1))
        assertEquals(-1L, TimingCalibrationMath.convertSampleRate(-1L, 2, 1))
        assertEquals(2L, TimingCalibrationMath.median(listOf(1L, 2L)))
        assertEquals(-2L, TimingCalibrationMath.median(listOf(-2L, -1L)))
    }

    @Test
    fun sampleRateConversionHandlesLargeValuesWithoutIntermediateOverflow() {
        val converted = TimingCalibrationMath.convertSampleRate(Long.MAX_VALUE / 4, 48_000, 44_100)
        assertTrue(converted > 0L)
    }

    private fun failureReason(matches: List<CalibrationClickMatch>): CalibrationFailureReason =
        (estimator.estimate(matches, 48_000, 0L).exceptionOrNull() as CalibrationEstimationException).reason
}
