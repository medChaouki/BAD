package com.titaniumharmonics.bad.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.titaniumharmonics.bad.exercise.ExerciseStorageInitializer
import com.titaniumharmonics.bad.audio.calibration.SharedPreferencesTimingCalibrationStore
import com.titaniumharmonics.bad.audio.calibration.TimingCalibration
import com.titaniumharmonics.bad.audio.calibration.TimingCalibrationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val timingCalibrationRepository = TimingCalibrationRepository(
        SharedPreferencesTimingCalibrationStore(application),
    )
    private val mutableUiState = MutableStateFlow(
        initialAppUiState(timingCalibrationRepository.activeCalibration()),
    )
    val uiState: StateFlow<AppUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            val defaultFolderUri = withContext(Dispatchers.IO) {
                ExerciseStorageInitializer(application).initialize()
            }
            mutableUiState.value = mutableUiState.value.copy(
                defaultExerciseFolderUri = defaultFolderUri?.toString(),
                storageInitializationComplete = true,
            )
        }
    }

    fun createExercise() {
        mutableUiState.value = mutableUiState.value.copy(
            destination = AppDestination.EXERCISE_EDITOR,
            editorDocumentUri = null,
            editorReturnDestination = AppDestination.PRACTICE,
        )
    }

    fun openExerciseLibraryForPractice() {
        mutableUiState.value = mutableUiState.value.openExerciseLibrary(
            ExerciseLibraryPurpose.PRACTICE,
        )
    }

    fun openExerciseLibraryForModify() {
        mutableUiState.value = mutableUiState.value.openExerciseLibrary(
            ExerciseLibraryPurpose.MODIFY,
        )
    }

    fun openLibraryExercise(documentUri: String) {
        mutableUiState.value = mutableUiState.value.openLibraryExercise(documentUri)
    }

    fun consumePracticeDocumentToLoad() {
        mutableUiState.value = mutableUiState.value.copy(
            practiceDocumentUriToLoad = null,
            startPracticeAfterLoad = false,
        )
    }

    fun playEditorExercise(documentUri: String) {
        mutableUiState.value = mutableUiState.value.playEditorExercise(documentUri)
    }

    fun openTimingCalibration() {
        mutableUiState.value = mutableUiState.value.copy(
            destination = AppDestination.TIMING_CALIBRATION,
        )
    }

    fun openSettings() {
        mutableUiState.value = mutableUiState.value.openSettings()
    }

    fun timingCalibrationChanged(calibration: TimingCalibration?) {
        mutableUiState.value = mutableUiState.value.copy(activeTimingCalibration = calibration)
    }

    fun navigateBack() {
        val state = mutableUiState.value
        mutableUiState.value = when (state.destination) {
            AppDestination.PRACTICE -> state
            AppDestination.EXERCISE_LIBRARY -> state.copy(
                destination = AppDestination.PRACTICE,
            )
            AppDestination.EXERCISE_EDITOR -> state.copy(
                destination = state.editorReturnDestination,
                editorDocumentUri = null,
            )
            AppDestination.SETTINGS -> state.copy(
                destination = AppDestination.PRACTICE,
            )
            AppDestination.TIMING_CALIBRATION -> state.copy(
                destination = AppDestination.PRACTICE,
            )
        }
    }
}
