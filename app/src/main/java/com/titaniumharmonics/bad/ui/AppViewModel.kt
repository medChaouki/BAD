package com.titaniumharmonics.bad.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.titaniumharmonics.bad.exercise.ExerciseStorageInitializer
import com.titaniumharmonics.bad.exercise.DefaultExerciseLibraryRepository
import com.titaniumharmonics.bad.audio.calibration.SharedPreferencesTimingCalibrationStore
import com.titaniumharmonics.bad.audio.calibration.TimingCalibration
import com.titaniumharmonics.bad.audio.calibration.TimingCalibrationRepository
import com.titaniumharmonics.bad.audio.result.PracticeResult
import com.titaniumharmonics.bad.audio.result.ProductionGraphModel
import com.titaniumharmonics.bad.history.ExerciseRunLoadResult
import com.titaniumharmonics.bad.history.ExerciseRunPersistenceError
import com.titaniumharmonics.bad.history.persistence.RoomExerciseRunRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    private val exerciseRunRepository = RoomExerciseRunRepository.create(application)
    private val exerciseLibraryRepository = DefaultExerciseLibraryRepository(application)
    private var savedRunLoadJob: Job? = null
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
            resultsPresentation = ResultsPresentationState.None,
            resultsDetailVisible = false,
            resultsDebugVisible = false,
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

    fun openResults(result: PracticeResult, graphModel: ProductionGraphModel) {
        savedRunLoadJob?.cancel()
        mutableUiState.value = mutableUiState.value.openResults(result, graphModel)
    }

    fun openSavedRun(runId: String) {
        savedRunLoadJob?.cancel()
        if (runId.isBlank()) {
            mutableUiState.value = mutableUiState.value.copy(
                destination = AppDestination.RESULTS,
                resultsPresentation = ResultsPresentationState.LoadFailed(
                    runId,
                    "The saved run ID is invalid.",
                ),
                resultsDetailVisible = false,
                resultsDebugVisible = false,
            )
            return
        }
        mutableUiState.value = mutableUiState.value.copy(
            destination = AppDestination.RESULTS,
            resultsPresentation = ResultsPresentationState.Loading(runId),
            resultsDetailVisible = false,
            resultsDebugVisible = false,
        )
        savedRunLoadJob = viewModelScope.launch {
            try {
                when (val loaded = exerciseRunRepository.getRun(runId)) {
                    is ExerciseRunLoadResult.Found -> {
                        val retryDocumentUri = withContext(Dispatchers.IO) {
                            exerciseLibraryRepository.findExercise(loaded.run.exerciseId)
                                ?.documentUri
                        }
                        if (mutableUiState.value.resultsPresentation ==
                            ResultsPresentationState.Loading(runId)
                        ) {
                            mutableUiState.value = mutableUiState.value.copy(
                                resultsPresentation = ResultsPresentationState.Ready(
                                    loaded.run.toSavedResultsPresentation(retryDocumentUri),
                                ),
                            )
                        }
                    }
                    is ExerciseRunLoadResult.Failed -> showSavedRunLoadFailure(
                        runId,
                        loaded.error.safeMessage(),
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                showSavedRunLoadFailure(runId, "The saved run could not be loaded.")
            }
        }
    }

    fun retrySavedRun() {
        val ready = mutableUiState.value.resultsPresentation as?
            ResultsPresentationState.Ready ?: return
        if (ready.model.source !is ResultsSource.SavedRun) return
        val documentUri = ready.model.retryDocumentUri ?: return
        mutableUiState.value = mutableUiState.value.copy(
            destination = AppDestination.PRACTICE,
            practiceDocumentUriToLoad = documentUri,
            startPracticeAfterLoad = true,
            resultsPresentation = ResultsPresentationState.None,
            resultsDetailVisible = false,
            resultsDebugVisible = false,
        )
    }

    fun openProcessing() {
        mutableUiState.value = mutableUiState.value.openProcessing()
    }

    fun showResultDetails() {
        val state = mutableUiState.value
        if (state.destination == AppDestination.RESULTS &&
            state.resultsPresentation is ResultsPresentationState.Ready
        ) {
            mutableUiState.value = state.copy(resultsDetailVisible = true)
        }
    }

    fun showResultDebug() {
        val state = mutableUiState.value
        if (state.destination == AppDestination.RESULTS &&
            state.resultsPresentation is ResultsPresentationState.Ready
        ) {
            mutableUiState.value = state.copy(
                resultsDetailVisible = false,
                resultsDebugVisible = true,
            )
        }
    }

    fun leaveResultsForPractice() {
        mutableUiState.value = mutableUiState.value.copy(
            destination = AppDestination.PRACTICE,
            resultsPresentation = ResultsPresentationState.None,
            resultsDetailVisible = false,
            resultsDebugVisible = false,
        )
    }

    fun timingCalibrationChanged(calibration: TimingCalibration?) {
        mutableUiState.value = mutableUiState.value.copy(activeTimingCalibration = calibration)
    }

    fun navigateBack() {
        mutableUiState.value = mutableUiState.value.navigateBack()
    }

    private fun showSavedRunLoadFailure(runId: String, message: String) {
        if (mutableUiState.value.resultsPresentation == ResultsPresentationState.Loading(runId)) {
            mutableUiState.value = mutableUiState.value.copy(
                resultsPresentation = ResultsPresentationState.LoadFailed(runId, message),
            )
        }
    }

    override fun onCleared() {
        savedRunLoadJob?.cancel()
        super.onCleared()
    }
}

private fun ExerciseRunPersistenceError.safeMessage(): String = when (this) {
    is ExerciseRunPersistenceError.MissingRun -> "The saved run was not found."
    is ExerciseRunPersistenceError.CorruptedPayload,
    is ExerciseRunPersistenceError.InvalidGraphPayload,
    -> "The saved run is damaged and cannot be displayed."
    is ExerciseRunPersistenceError.UnsupportedSchema ->
        "This saved run was created by a newer incompatible app version."
    else -> "The saved run could not be loaded."
}
