package com.titaniumharmonics.bad.ui.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.titaniumharmonics.bad.audio.metronome.AndroidMetronomeTestTonePlayer
import com.titaniumharmonics.bad.audio.metronome.MetronomeConfiguration
import com.titaniumharmonics.bad.audio.metronome.MetronomeConfigurationRepository
import com.titaniumharmonics.bad.audio.metronome.MetronomeNotchConfiguration
import com.titaniumharmonics.bad.audio.metronome.MetronomeToneConfiguration
import com.titaniumharmonics.bad.audio.metronome.SharedPreferencesMetronomeConfigurationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MetronomeSettingsUiState(
    val configuration: MetronomeConfiguration = MetronomeConfiguration.DEFAULT,
    val testTonePlaying: Boolean = false,
    val errorMessage: String? = null,
) {
    val isValid: Boolean
        get() = configuration.validationErrors(48_000).isEmpty()
}

class MetronomeSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MetronomeConfigurationRepository(
        SharedPreferencesMetronomeConfigurationStore(application),
    )
    private val testTonePlayer = AndroidMetronomeTestTonePlayer()
    private val mutableUiState = MutableStateFlow(
        MetronomeSettingsUiState(configuration = repository.load()),
    )
    val uiState: StateFlow<MetronomeSettingsUiState> = mutableUiState.asStateFlow()
    private var testToneJob: Job? = null

    fun setToneFrequency(value: Int) = updateConfiguration {
        it.withToneFrequency(
            value.coerceIn(
                MetronomeToneConfiguration.MIN_FREQUENCY_HZ,
                MetronomeToneConfiguration.MAX_FREQUENCY_HZ,
            ),
        )
    }

    fun setToneDuration(value: Int) = updateConfiguration {
        it.copy(
            tone = it.tone.copy(
                durationMillis = value.coerceIn(
                    MetronomeToneConfiguration.MIN_DURATION_MILLIS,
                    MetronomeToneConfiguration.MAX_DURATION_MILLIS,
                ),
            ),
        )
    }

    fun setNormalVolume(value: Int) = updateConfiguration {
        it.copy(
            tone = it.tone.copy(
                normalVolumePercent = value.coerceIn(
                    MetronomeToneConfiguration.MIN_VOLUME_PERCENT,
                    MetronomeToneConfiguration.MAX_VOLUME_PERCENT,
                ),
            ),
        )
    }

    fun setAccentVolume(value: Int) = updateConfiguration {
        it.copy(
            tone = it.tone.copy(
                accentVolumePercent = value.coerceIn(
                    MetronomeToneConfiguration.MIN_VOLUME_PERCENT,
                    MetronomeToneConfiguration.MAX_VOLUME_PERCENT,
                ),
            ),
        )
    }

    fun setNotchEnabled(value: Boolean) = updateConfiguration {
        it.copy(notch = it.notch.copy(enabled = value))
    }

    fun setNotchCenter(value: Int) = updateConfiguration {
        it.withCustomNotchCenter(
            value.coerceIn(
                MetronomeNotchConfiguration.MIN_CENTER_FREQUENCY_HZ,
                MetronomeNotchConfiguration.MAX_CENTER_FREQUENCY_HZ,
            ),
        )
    }

    fun setNotchQ(value: Double) = updateConfiguration {
        it.copy(
            notch = it.notch.copy(
                qFactor = value.coerceIn(
                    MetronomeNotchConfiguration.MIN_Q_FACTOR,
                    MetronomeNotchConfiguration.MAX_Q_FACTOR,
                ),
            ),
        )
    }

    fun relinkNotchCenter() = updateConfiguration(MetronomeConfiguration::relinkNotchCenter)

    fun reset() {
        stopTestTone()
        runCatching { repository.reset() }
            .onSuccess { defaults ->
                mutableUiState.value = MetronomeSettingsUiState(defaults)
            }
            .onFailure {
                mutableUiState.value = mutableUiState.value.copy(
                    errorMessage = "Unable to reset metronome settings.",
                )
            }
    }

    fun playTestTone(accent: Boolean) {
        val configuration = mutableUiState.value.configuration
        if (!mutableUiState.value.isValid) return
        stopTestTone()
        mutableUiState.value = mutableUiState.value.copy(
            testTonePlaying = true,
            errorMessage = null,
        )
        testToneJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching { testTonePlayer.play(configuration, accent) }
                .onFailure {
                    Log.w(LOG_TAG, "Metronome test-tone playback failed.", it)
                    mutableUiState.value = mutableUiState.value.copy(
                        errorMessage = "Unable to play the metronome test tone.",
                    )
                }
            mutableUiState.value = mutableUiState.value.copy(testTonePlaying = false)
        }
    }

    fun stopTestTone() {
        testToneJob?.cancel()
        testToneJob = null
        testTonePlayer.stop()
        mutableUiState.value = mutableUiState.value.copy(testTonePlaying = false)
    }

    private fun updateConfiguration(
        transform: (MetronomeConfiguration) -> MetronomeConfiguration,
    ) {
        val updated = transform(mutableUiState.value.configuration)
        runCatching { repository.save(updated) }
            .onSuccess {
                mutableUiState.value = mutableUiState.value.copy(
                    configuration = updated,
                    errorMessage = null,
                )
            }
            .onFailure {
                mutableUiState.value = mutableUiState.value.copy(
                    errorMessage = "Unable to save metronome settings.",
                )
            }
    }

    override fun onCleared() {
        stopTestTone()
        super.onCleared()
    }

    private companion object {
        const val LOG_TAG = "MetronomeSettings"
    }
}
