package com.titaniumharmonics.bad.audio.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TimingCalibrationRepositoryTest {
    @Test
    fun savesLoadsResetsAndPreservesAllFields() {
        val store = FakeStore()
        val repository = TimingCalibrationRepository(store)
        val calibration = calibration(offset = 72L, timestamp = 123_456L)
        repository.saveSuccessful(calibration)
        assertEquals(calibration, repository.activeCalibration())
        assertEquals(66L, repository.offsetSamplesFor(44_100))
        assertTrue(repository.hasValidCalibration())
        repository.reset()
        assertNull(repository.activeCalibration())
        assertFalse(repository.hasValidCalibration())
    }

    @Test
    fun failedRecalibrationPreservesPreviousAndSuccessReplacesIt() {
        val store = FakeStore(calibration(10L))
        val repository = TimingCalibrationRepository(store)
        // Failure does not call saveSuccessful.
        assertEquals(10L, repository.activeCalibration()?.offsetSamples)
        repository.saveSuccessful(calibration(20L))
        assertEquals(20L, repository.activeCalibration()?.offsetSamples)
    }

    @Test
    fun nonPositiveEstimatorFailuresAreNeverPersistedAndPreservePreviousCalibration() {
        listOf(0L, -50L).forEach { offset ->
            val store = FakeStore(calibration(10L))
            val repository = TimingCalibrationRepository(store)
            val config = TimingCalibrationConfig()
            val estimate = CalibrationOffsetEstimator(config).estimate(
                matches = (0 until config.clickCount).map { index ->
                    match(index * 24_000L, offset)
                },
                sampleRateHz = 48_000,
                calibratedAtEpochMillis = 2L,
            )

            estimate.getOrNull()?.let(repository::saveSuccessful)

            assertEquals(CalibrationFailureReason.NON_POSITIVE_OFFSET, (
                estimate.exceptionOrNull() as CalibrationEstimationException
                ).reason)
            assertEquals(0, store.saveCount)
            assertEquals(10L, repository.activeCalibration()?.offsetSamples)
        }
    }

    @Test
    fun versionMismatchIsIgnoredSafely() {
        val store = FakeStore(calibration(10L).copy(algorithmVersion = 3))
        assertNull(
            TimingCalibrationRepository(
                store,
                algorithmVersion = TimingCalibrationConfig.CURRENT_ALGORITHM_VERSION,
            ).activeCalibration(),
        )
    }

    @Test
    fun explicitlyAcceptedLowConfidenceCalibrationCanBeSaved() {
        val store = FakeStore()
        val repository = TimingCalibrationRepository(store)
        val candidate = calibration(72L).copy(confidence = CalibrationConfidence.LOW)

        assertThrows(IllegalArgumentException::class.java) {
            repository.saveSuccessful(candidate)
        }
        repository.saveAccepted(candidate)

        assertEquals(candidate, repository.activeCalibration())
    }

    @Test
    fun corruptedPersistedValuesAreIgnoredSafely() {
        val valid = mapOf(
            "offset_samples" to 72L,
            "sample_rate_hz" to 48_000,
            "confidence" to "HIGH",
            "expected_click_count" to 8,
            "matched_click_count" to 8,
            "offset_spread_samples" to 4L,
            "calibrated_at_epoch_millis" to 123L,
            "algorithm_version" to TimingCalibrationConfig.CURRENT_ALGORITHM_VERSION,
        )
        val currentVersion = TimingCalibrationConfig.CURRENT_ALGORITHM_VERSION
        assertEquals(72L, TimingCalibrationPersistenceCodec.decode(valid, currentVersion)?.offsetSamples)
        assertNull(TimingCalibrationPersistenceCodec.decode(valid + ("offset_samples" to 0L), currentVersion))
        assertNull(TimingCalibrationPersistenceCodec.decode(valid + ("offset_samples" to -1L), currentVersion))
        assertNull(TimingCalibrationPersistenceCodec.decode(valid + ("sample_rate_hz" to 0), currentVersion))
        assertNull(TimingCalibrationPersistenceCodec.decode(valid + ("confidence" to "BROKEN"), currentVersion))
        assertNull(TimingCalibrationPersistenceCodec.decode(valid - "offset_samples", currentVersion))
        assertNull(TimingCalibrationPersistenceCodec.decode(valid + ("algorithm_version" to 99), currentVersion))

        val migrated = TimingCalibrationPersistenceCodec.decode(
            valid + ("algorithm_version" to TimingCalibrationConfig.LEGACY_POSITIVE_OFFSET_VERSION),
            currentVersion,
        )
        assertEquals(72L, migrated?.offsetSamples)
        assertEquals(currentVersion, migrated?.algorithmVersion)
    }

    private fun calibration(offset: Long, timestamp: Long = 1L) = TimingCalibration(
        offsetSamples = offset,
        sampleRateHz = 48_000,
        confidence = CalibrationConfidence.HIGH,
        expectedClickCount = 8,
        matchedClickCount = 8,
        offsetSpreadSamples = 2,
        calibratedAtEpochMillis = timestamp,
        algorithmVersion = TimingCalibrationConfig.CURRENT_ALGORITHM_VERSION,
    )

    private class FakeStore(initial: TimingCalibration? = null) : TimingCalibrationStore {
        private var value = initial
        var saveCount = 0
            private set
        override fun load(expectedAlgorithmVersion: Int): TimingCalibration? =
            value?.takeIf { it.algorithmVersion == expectedAlgorithmVersion }
        override fun save(calibration: TimingCalibration) {
            saveCount++
            value = calibration
        }
        override fun reset() { value = null }
    }
}
