package com.titaniumharmonics.bad.audio.metronome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MetronomeConfigurationTest {
    @Test
    fun defaultsRangesAndSampleRateValidationAreStable() {
        val defaults = MetronomeConfiguration.DEFAULT
        assertEquals(6_000, defaults.tone.frequencyHz)
        assertEquals(10, defaults.tone.durationMillis)
        assertEquals(55, defaults.tone.normalVolumePercent)
        assertEquals(85, defaults.tone.accentVolumePercent)
        assertTrue(defaults.notch.enabled)
        assertEquals(10.0, defaults.notch.qFactor, 0.0)
        assertTrue(defaults.validationErrors(48_000).isEmpty())
        assertTrue(defaults.validationErrors(44_100).isEmpty())
        assertFalse(defaults.copy(
            tone = defaults.tone.copy(frequencyHz = 9_000),
            notch = defaults.notch.copy(centerFrequencyHz = 9_000),
        ).validationErrors(16_000).isEmpty())
    }

    @Test
    fun linkedCenterFollowsToneCustomOverrideSurvivesAndRelinkRestoresFollowing() {
        val moved = MetronomeConfiguration.DEFAULT.withToneFrequency(5_500)
        assertEquals(5_500, moved.notch.centerFrequencyHz)
        assertTrue(moved.notch.centerLinkedToTone)

        val custom = moved.withCustomNotchCenter(5_200).withToneFrequency(6_500)
        assertEquals(5_200, custom.notch.centerFrequencyHz)
        assertFalse(custom.notch.centerLinkedToTone)

        val relinked = custom.relinkNotchCenter()
        assertEquals(6_500, relinked.notch.centerFrequencyHz)
        assertTrue(relinked.notch.centerLinkedToTone)
    }

    @Test
    fun invalidRangesAndNonFiniteQAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            MetronomeToneConfiguration(frequencyHz = 2_999)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MetronomeToneConfiguration(normalVolumePercent = 101)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MetronomeNotchConfiguration(qFactor = Double.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MetronomeNotchConfiguration(qFactor = 31.0)
        }
    }

    @Test
    fun persistenceRoundTripResetLinkAndCorruptionFallback() {
        val store = FakeStore()
        val repository = MetronomeConfigurationRepository(store)
        val custom = MetronomeConfiguration.DEFAULT
            .withToneFrequency(5_000)
            .withCustomNotchCenter(5_400)
            .copy(
                tone = MetronomeConfiguration.DEFAULT.tone.copy(
                    frequencyHz = 5_000,
                    durationMillis = 15,
                    normalVolumePercent = 40,
                    accentVolumePercent = 90,
                ),
                notch = MetronomeConfiguration.DEFAULT.notch.copy(
                    centerFrequencyHz = 5_400,
                    centerLinkedToTone = false,
                    qFactor = 14.0,
                    enabled = false,
                ),
            )
        repository.save(custom)
        assertEquals(custom, repository.load())
        assertFalse(repository.load().notch.centerLinkedToTone)
        assertEquals(MetronomeConfiguration.DEFAULT, repository.reset())

        store.values = mapOf("version" to 1, "tone_frequency_hz" to Int.MIN_VALUE)
        assertEquals(MetronomeConfiguration.DEFAULT, repository.load())
        store.values = mapOf("version" to 99)
        assertEquals(MetronomeConfiguration.DEFAULT, repository.load())
    }

    private class FakeStore : MetronomeConfigurationStore {
        var values: Map<String, *> = emptyMap<String, Any>()
        override fun load(): Map<String, *> = values
        override fun save(values: Map<String, Any>) { this.values = values }
        override fun reset() { values = emptyMap<String, Any>() }
    }
}
