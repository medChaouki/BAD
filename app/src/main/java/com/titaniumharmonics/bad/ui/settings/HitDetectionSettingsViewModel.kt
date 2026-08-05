package com.titaniumharmonics.bad.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.titaniumharmonics.bad.audio.detection.HitDetectionConfiguration
import com.titaniumharmonics.bad.audio.detection.HitDetectionConfigurationRepository
import com.titaniumharmonics.bad.audio.detection.MetronomeRejectionConfiguration
import com.titaniumharmonics.bad.audio.detection.SharedPreferencesHitDetectionConfigurationStore
import com.titaniumharmonics.bad.audio.detection.UncertainCandidateBehaviour
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HitDetectionSettingsUiState(
    val configuration: HitDetectionConfiguration = HitDetectionConfiguration.DEFAULT,
    val errorMessage: String? = null,
)

interface HitDetectionSettingsActions {
    fun setEnabled(value: Boolean)
    fun setMinimumAbsoluteThreshold(value: Double)
    fun setNoiseFloorMultiplier(value: Double)
    fun setMinimumSignalToNoise(value: Double)
    fun setMinimumAttackRise(value: Double)
    fun setOnsetLookBack(value: Double)
    fun setPeakSearch(value: Double)
    fun setReleaseRatio(value: Double)
    fun setMinimumHitSpacing(value: Double)
    fun setMinimumConfidence(value: Double)
    fun setApplyCalibration(value: Boolean)
    fun setRejectionEnabled(value: Boolean)
    fun setFftSize(value: Int)
    fun setFftWindow(value: Double)
    fun setBandWidth(value: Double)
    fun setMinimumBandRatio(value: Double)
    fun setMinimumBroadbandEnergy(value: Double)
    fun setSpectralConfidence(value: Double)
    fun setMaximumScheduledDistance(value: Double)
    fun setUncertainBehaviour(value: UncertainCandidateBehaviour)
    fun reset()
}

class HitDetectionSettingsViewModel(application: Application) :
    AndroidViewModel(application), HitDetectionSettingsActions {
    private val repository = HitDetectionConfigurationRepository(
        SharedPreferencesHitDetectionConfigurationStore(application),
    )
    private val mutableUiState = MutableStateFlow(
        HitDetectionSettingsUiState(repository.load()),
    )
    val uiState: StateFlow<HitDetectionSettingsUiState> = mutableUiState.asStateFlow()

    override fun setEnabled(value: Boolean) = update { it.copy(enabled = value) }
    override fun setMinimumAbsoluteThreshold(value: Double) = update {
        it.copy(minimumAbsoluteThreshold = value.coerceIn(0.0, 1.0))
    }
    override fun setNoiseFloorMultiplier(value: Double) = update {
        it.copy(noiseFloorMultiplier = value.coerceIn(1.0, 12.0))
    }
    override fun setMinimumSignalToNoise(value: Double) = update {
        it.copy(minimumSignalToNoiseRatio = value.coerceIn(1.0, 20.0))
    }
    override fun setMinimumAttackRise(value: Double) = update {
        it.copy(minimumAttackRise = value.coerceIn(0.0, 1.0))
    }
    override fun setOnsetLookBack(value: Double) = update {
        it.copy(onsetLookBackMillis = value.coerceIn(0.0, 50.0))
    }
    override fun setPeakSearch(value: Double) = update {
        it.copy(peakSearchMillis = value.coerceIn(1.0, 100.0))
    }
    override fun setReleaseRatio(value: Double) = update {
        it.copy(releaseHysteresisRatio = value.coerceIn(0.1, 0.95))
    }
    override fun setMinimumHitSpacing(value: Double) = update {
        it.copy(minimumHitSpacingMillis = value.coerceIn(5.0, 200.0))
    }
    override fun setMinimumConfidence(value: Double) = update {
        it.copy(minimumConfidence = value.coerceIn(0.0, 1.0))
    }
    override fun setApplyCalibration(value: Boolean) = update {
        it.copy(applyTimingCalibration = value)
    }
    override fun setRejectionEnabled(value: Boolean) = updateRejection { it.copy(enabled = value) }
    override fun setFftSize(value: Int) = updateRejection {
        it.copy(fftSize = value.takeIf(MetronomeRejectionConfiguration.SUPPORTED_FFT_SIZES::contains)
            ?: it.fftSize)
    }
    override fun setFftWindow(value: Double) = updateRejection {
        it.copy(analysisWindowMillis = value.coerceIn(5.0, 40.0))
    }
    override fun setBandWidth(value: Double) = updateRejection {
        it.copy(metronomeBandWidthHz = value.coerceIn(100.0, 3_000.0))
    }
    override fun setMinimumBandRatio(value: Double) = updateRejection {
        it.copy(minimumMetronomeBandEnergyRatio = value.coerceIn(0.0, 1.0))
    }
    override fun setMinimumBroadbandEnergy(value: Double) = updateRejection {
        it.copy(minimumBroadbandResidualEnergy = value.coerceIn(0.0, 1.0))
    }
    override fun setSpectralConfidence(value: Double) = updateRejection {
        it.copy(spectralConfidenceThreshold = value.coerceIn(0.0, 1.0))
    }
    override fun setMaximumScheduledDistance(value: Double) = updateRejection {
        it.copy(maximumScheduledDistanceMillis = value.coerceIn(0.0, 100.0))
    }
    override fun setUncertainBehaviour(value: UncertainCandidateBehaviour) = updateRejection {
        it.copy(uncertainCandidateBehaviour = value)
    }

    override fun reset() {
        runCatching(repository::reset).onSuccess {
            mutableUiState.value = HitDetectionSettingsUiState(it)
        }.onFailure {
            mutableUiState.value = mutableUiState.value.copy(
                errorMessage = "Unable to reset hit-detection settings.",
            )
        }
    }

    private fun updateRejection(
        transform: (MetronomeRejectionConfiguration) -> MetronomeRejectionConfiguration,
    ) = update { it.copy(metronomeRejection = transform(it.metronomeRejection)) }

    private fun update(transform: (HitDetectionConfiguration) -> HitDetectionConfiguration) {
        val updated = transform(mutableUiState.value.configuration)
        runCatching { repository.save(updated) }.onSuccess {
            mutableUiState.value = HitDetectionSettingsUiState(updated)
        }.onFailure {
            mutableUiState.value = mutableUiState.value.copy(
                errorMessage = "Unable to save hit-detection settings.",
            )
        }
    }
}
