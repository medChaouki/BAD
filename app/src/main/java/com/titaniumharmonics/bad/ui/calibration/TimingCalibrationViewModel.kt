package com.titaniumharmonics.bad.ui.calibration

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.titaniumharmonics.bad.BuildConfig
import com.titaniumharmonics.bad.audio.MediaPlayerRecordedAudioPlayer
import com.titaniumharmonics.bad.audio.calibration.AndroidTimingCalibrationRunner
import com.titaniumharmonics.bad.audio.calibration.CalibrationFailureReason
import com.titaniumharmonics.bad.audio.calibration.CalibrationPhase
import com.titaniumharmonics.bad.audio.calibration.CalibrationRunResult
import com.titaniumharmonics.bad.audio.calibration.SharedPreferencesTimingCalibrationStore
import com.titaniumharmonics.bad.audio.calibration.TimingCalibrationRepository
import com.titaniumharmonics.bad.audio.calibration.TimingCalibrationStateMachine
import com.titaniumharmonics.bad.audio.calibration.TimingCalibrationUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class TimingCalibrationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TimingCalibrationRepository(
        SharedPreferencesTimingCalibrationStore(application),
    )
    private val stateMachine = TimingCalibrationStateMachine(repository.activeCalibration())
    private val runner = AndroidTimingCalibrationRunner(
        context = application,
        retainDebugRecording = BuildConfig.DEBUG,
    )
    private val debugPlayer = MediaPlayerRecordedAudioPlayer()
    private val mutableUiState = MutableStateFlow(stateMachine.state)
    val uiState: StateFlow<TimingCalibrationUiState> = mutableUiState.asStateFlow()
    private var calibrationJob: Job? = null

    init {
        checkRoute()
    }

    fun checkRoute() {
        if (stateMachine.state.isActive) return
        stateMachine.checkingRoute()
        publish()
        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = runner.checkRoute()
            stateMachine.routeChecked(snapshot.decision)
            publish()
        }
    }

    fun confirmUncertainRoute(confirmed: Boolean) {
        stateMachine.confirmUncertainRoute(confirmed)
        publish()
    }

    fun startCalibration(microphonePermissionGranted: Boolean) {
        if (!microphonePermissionGranted) {
            stateMachine.fail(CalibrationFailureReason.MICROPHONE_PERMISSION_DENIED)
            publish()
            return
        }
        if (calibrationJob?.isActive == true || !stateMachine.state.canStart) return
        stopDebugPlayback()
        stateMachine.preparing()
        publish()
        calibrationJob = viewModelScope.launch(Dispatchers.IO) {
            val result = runner.run(
                uncertainRouteConfirmed = stateMachine.state.uncertainRouteConfirmed,
                onPhase = ::advancePhase,
            )
            when (result) {
                is CalibrationRunResult.Success -> stateMachine.success(
                    result.calibration,
                    result.diagnostics,
                )
                is CalibrationRunResult.Failure -> {
                    if (result.reviewableCalibration != null) {
                        stateMachine.success(
                            result.reviewableCalibration,
                            result.diagnostics,
                        )
                    } else if (result.reason == CalibrationFailureReason.CANCELLED) {
                        stateMachine.cancel()
                    } else {
                        stateMachine.fail(result.reason, result.diagnostics)
                    }
                }
            }
            publish()
        }
    }

    fun cancelCalibration() {
        if (!stateMachine.state.isActive) return
        runner.cancel()
        calibrationJob?.cancel()
        stateMachine.cancel()
        publish()
    }

    fun resetCalibration() {
        if (stateMachine.state.isActive) return
        stateMachine.state.diagnostics?.wavFilePath?.let { File(it).delete() }
        repository.reset()
        stateMachine.resetActiveCalibration()
        stopDebugPlayback()
        publish()
    }

    fun acceptCalibration() {
        if (!stateMachine.state.isAwaitingDecision) return
        val pending = stateMachine.state.pendingCalibration ?: return
        try {
            repository.saveAccepted(pending)
            stateMachine.acceptPendingCalibration()
        } catch (_: Exception) {
            stateMachine.fail(CalibrationFailureReason.UNKNOWN)
        }
        publish()
    }

    fun rejectCalibration() {
        if (!stateMachine.state.isAwaitingDecision) return
        stateMachine.rejectPendingCalibration()
        stopDebugPlayback()
        publish()
    }

    fun playDebugRecording() {
        if (!BuildConfig.DEBUG) return
        val path = stateMachine.state.diagnostics?.wavFilePath ?: return
        runCatching {
            debugPlayer.release()
            debugPlayer.prepare(path, onCompletion = {}, onError = {})
            debugPlayer.play()
        }
    }

    fun stopDebugPlayback() {
        runCatching { debugPlayer.stop() }
    }

    fun leaveScreen() {
        if (stateMachine.state.isActive) cancelCalibration()
        stopDebugPlayback()
        debugPlayer.release()
    }

    private fun advancePhase(phase: CalibrationPhase) {
        when (phase) {
            CalibrationPhase.PREPARING -> Unit
            CalibrationPhase.RECORDING -> stateMachine.recording()
            CalibrationPhase.PLAYING_CLICKS -> stateMachine.playingClicks()
            CalibrationPhase.PROCESSING -> stateMachine.processing()
            else -> Unit
        }
        publish()
    }

    private fun publish() {
        mutableUiState.value = stateMachine.state
    }

    override fun onCleared() {
        runner.release()
        debugPlayer.release()
        super.onCleared()
    }
}
