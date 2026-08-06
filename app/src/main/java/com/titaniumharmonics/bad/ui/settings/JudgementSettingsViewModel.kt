package com.titaniumharmonics.bad.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.titaniumharmonics.bad.audio.matching.JudgementConfiguration
import com.titaniumharmonics.bad.audio.matching.JudgementConfigurationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class JudgementSettingsUiState(
    val configuration: JudgementConfiguration = JudgementConfiguration.DEFAULT,
    val errorMessage: String? = null,
)

interface JudgementSettingsActions {
    fun setOnTimeBefore(value: Double)
    fun setOnTimeAfter(value: Double)
    fun setMaximumEarly(value: Double)
    fun setMaximumLate(value: Double)
    fun reset()
}

class JudgementSettingsViewModel(application: Application) :
    AndroidViewModel(application), JudgementSettingsActions {
    private val repository = JudgementConfigurationRepository(
        SharedPreferencesJudgementConfigurationStore(application),
    )
    private val mutableUiState = MutableStateFlow(
        JudgementSettingsUiState(repository.load()),
    )
    val uiState: StateFlow<JudgementSettingsUiState> = mutableUiState.asStateFlow()

    override fun setOnTimeBefore(value: Double) = update { current ->
        val bounded = value.validWindow()
        current.copy(
            onTimeBeforeMillis = bounded,
            maximumEarlyMillis = maxOf(current.maximumEarlyMillis, bounded),
        )
    }

    override fun setOnTimeAfter(value: Double) = update { current ->
        val bounded = value.validWindow()
        current.copy(
            onTimeAfterMillis = bounded,
            maximumLateMillis = maxOf(current.maximumLateMillis, bounded),
        )
    }

    override fun setMaximumEarly(value: Double) = update { current ->
        val bounded = value.validWindow()
        current.copy(
            maximumEarlyMillis = bounded,
            onTimeBeforeMillis = minOf(current.onTimeBeforeMillis, bounded),
        )
    }

    override fun setMaximumLate(value: Double) = update { current ->
        val bounded = value.validWindow()
        current.copy(
            maximumLateMillis = bounded,
            onTimeAfterMillis = minOf(current.onTimeAfterMillis, bounded),
        )
    }

    override fun reset() {
        runCatching(repository::reset).onSuccess {
            mutableUiState.value = JudgementSettingsUiState(it)
        }.onFailure {
            mutableUiState.value = mutableUiState.value.copy(
                errorMessage = "Unable to reset judgement settings.",
            )
        }
    }

    private fun update(transform: (JudgementConfiguration) -> JudgementConfiguration) {
        val updated = transform(mutableUiState.value.configuration)
        runCatching { repository.save(updated) }.onSuccess {
            mutableUiState.value = JudgementSettingsUiState(updated)
        }.onFailure {
            mutableUiState.value = mutableUiState.value.copy(
                errorMessage = "Unable to save judgement settings.",
            )
        }
    }

    private fun Double.validWindow(): Double =
        takeIf(Double::isFinite)?.coerceIn(0.0, JudgementConfiguration.MAXIMUM_WINDOW_MILLIS)
            ?: 0.0
}
