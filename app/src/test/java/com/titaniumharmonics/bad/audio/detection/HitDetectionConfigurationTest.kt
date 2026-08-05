package com.titaniumharmonics.bad.audio.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HitDetectionConfigurationTest {
    @Test
    fun defaultsCoverEveryDetectorAndFftParameter() {
        val value = HitDetectionConfiguration.DEFAULT
        assertTrue(value.enabled)
        assertEquals(0.02, value.minimumAbsoluteThreshold, 0.0)
        assertEquals(3.0, value.noiseFloorMultiplier, 0.0)
        assertEquals(2.0, value.minimumSignalToNoiseRatio, 0.0)
        assertEquals(12.0, value.onsetLookBackMillis, 0.0)
        assertEquals(25.0, value.peakSearchMillis, 0.0)
        assertEquals(35.0, value.minimumHitSpacingMillis, 0.0)
        assertTrue(value.applyTimingCalibration)
        assertTrue(value.metronomeRejection.enabled)
        assertEquals(1_024, value.metronomeRejection.fftSize)
        assertEquals(
            UncertainCandidateBehaviour.RETAIN_AS_DRUM,
            value.metronomeRejection.uncertainCandidateBehaviour,
        )
    }

    @Test
    fun codecPersistenceResetAndCorruptionAreSafe() {
        val configured = HitDetectionConfiguration.DEFAULT.copy(
            enabled = false,
            minimumAbsoluteThreshold = 0.123456789,
            noiseFloorMultiplier = 4.5,
            minimumSignalToNoiseRatio = 3.5,
            minimumAttackRise = 0.05,
            onsetLookBackMillis = 20.0,
            peakSearchMillis = 40.0,
            releaseHysteresisRatio = 0.4,
            minimumHitSpacingMillis = 50.0,
            minimumConfidence = 0.6,
            applyTimingCalibration = false,
            metronomeRejection = HitDetectionConfiguration.DEFAULT.metronomeRejection.copy(
                enabled = false,
                fftSize = 2_048,
                analysisWindowMillis = 22.0,
                metronomeBandWidthHz = 900.0,
                minimumMetronomeBandEnergyRatio = 0.7,
                minimumBroadbandResidualEnergy = 0.04,
                spectralConfidenceThreshold = 0.75,
                maximumScheduledDistanceMillis = 40.0,
                uncertainCandidateBehaviour = UncertainCandidateBehaviour.REJECT_AS_METRONOME,
            ),
        )
        val store = FakeStore()
        val repository = HitDetectionConfigurationRepository(store)
        repository.save(configured)
        assertEquals(configured, repository.load())
        assertEquals(HitDetectionConfiguration.DEFAULT, repository.reset())
        assertEquals(HitDetectionConfiguration.DEFAULT, repository.load())

        store.values = mapOf("version" to 1, "minimum_absolute_threshold" to "NaN")
        assertEquals(HitDetectionConfiguration.DEFAULT, repository.load())
        store.values = mapOf("version" to 99)
        assertEquals(HitDetectionConfiguration.DEFAULT, repository.load())
    }

    @Test
    fun invalidValuesAndUnsupportedFftSizesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            HitDetectionConfiguration(minimumHitSpacingMillis = 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HitDetectionConfiguration(noiseFloorMultiplier = Double.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MetronomeRejectionConfiguration(fftSize = 300)
        }
    }

    @Test
    fun sessionSnapshotDoesNotChangeWithLaterGlobalConfiguration() {
        val original = HitDetectionConfiguration.DEFAULT.copy(minimumAbsoluteThreshold = 0.04)
        val snapshot = SessionDetectionSnapshot(original)
        val later = original.copy(minimumAbsoluteThreshold = 0.2)
        assertEquals(0.04, snapshot.configuration.minimumAbsoluteThreshold, 0.0)
        assertFalse(snapshot.configuration == later)
    }

    private class FakeStore : HitDetectionConfigurationStore {
        var values: Map<String, *> = emptyMap<String, Any>()
        override fun load(): Map<String, *> = values
        override fun save(values: Map<String, Any>) {
            this.values = values.toMap()
        }
        override fun reset() {
            values = emptyMap<String, Any>()
        }
    }
}
