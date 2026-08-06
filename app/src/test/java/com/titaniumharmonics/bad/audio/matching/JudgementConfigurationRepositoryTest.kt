package com.titaniumharmonics.bad.audio.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JudgementConfigurationRepositoryTest {
    @Test
    fun defaultsMatchDocumentedBoundaries() {
        val defaults = JudgementConfiguration.DEFAULT
        assertEquals(40.0, defaults.onTimeBeforeMillis, 0.0)
        assertEquals(40.0, defaults.onTimeAfterMillis, 0.0)
        assertEquals(120.0, defaults.maximumEarlyMillis, 0.0)
        assertEquals(120.0, defaults.maximumLateMillis, 0.0)
    }

    @Test
    fun repositoryPersistsReloadsAndResetsConfiguration() {
        val store = FakeStore()
        val repository = JudgementConfigurationRepository(store)
        val configured = JudgementConfiguration(
            onTimeBeforeMillis = 25.0,
            onTimeAfterMillis = 55.0,
            maximumEarlyMillis = 90.0,
            maximumLateMillis = 180.0,
            minimumDetectedHitConfidence = 0.45,
            extraHitHandlingEnabled = false,
        )

        repository.save(configured)
        assertEquals(configured, repository.load())
        assertEquals(JudgementConfiguration.DEFAULT, repository.reset())
        assertEquals(JudgementConfiguration.DEFAULT, repository.load())
    }

    @Test
    fun corruptedAndVersionMismatchedValuesFallBackToDefaults() {
        val store = FakeStore()
        val repository = JudgementConfigurationRepository(store)
        store.values = mapOf("version" to 1, "on_time_before_ms" to "NaN")
        assertEquals(JudgementConfiguration.DEFAULT, repository.load())

        store.values = mapOf("version" to 99)
        assertEquals(JudgementConfiguration.DEFAULT, repository.load())
    }

    @Test
    fun validationRejectsZeroMaximumWindowsAndInvalidConfidence() {
        assertThrows(IllegalArgumentException::class.java) {
            JudgementConfiguration(
                onTimeBeforeMillis = 0.0,
                maximumEarlyMillis = 0.0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            JudgementConfiguration(minimumDetectedHitConfidence = 1.1)
        }
    }

    @Test
    fun snapshotsRemainFrozenWhileNewLoadsReceiveUpdatedSettings() {
        val store = FakeStore()
        val repository = JudgementConfigurationRepository(store)
        val first = JudgementConfiguration.DEFAULT.copy(onTimeBeforeMillis = 25.0)
        repository.save(first)
        val activeSession = SessionJudgementSnapshot(repository.load())

        val updated = first.copy(onTimeBeforeMillis = 35.0)
        repository.save(updated)
        val newSession = SessionJudgementSnapshot(repository.load())

        assertEquals(25.0, activeSession.configuration.onTimeBeforeMillis, 0.0)
        assertEquals(35.0, newSession.configuration.onTimeBeforeMillis, 0.0)
        assertEquals(JudgementConfiguration.CURRENT_VERSION, activeSession.version)
    }

    private class FakeStore : JudgementConfigurationStore {
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
