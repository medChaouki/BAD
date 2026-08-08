package com.titaniumharmonics.bad.ui

import com.titaniumharmonics.bad.audio.calibration.CalibrationConfidence
import com.titaniumharmonics.bad.audio.calibration.TimingCalibration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import com.titaniumharmonics.bad.history.persistence.exerciseRunFixture

class AppUiStateTest {
    @Test
    fun processingIsDedicatedDestinationAndBackIsDisabled() {
        val processing = AppUiState().openProcessing()

        assertEquals(AppDestination.PROCESSING, processing.destination)
        assertEquals(processing, processing.navigateBack())
    }

    @Test
    fun debugResultsAreHiddenByDefaultAndBackReturnsToResultsSummary() {
        val state = AppUiState(
            destination = AppDestination.RESULTS,
            resultsDebugVisible = true,
        )

        val results = state.navigateBack()

        assertEquals(AppDestination.RESULTS, results.destination)
        assertFalse(results.resultsDebugVisible)
        assertFalse(AppUiState(destination = AppDestination.RESULTS).resultsDebugVisible)
    }

    @Test
    fun resultsBackClosesDetailsBeforeReturningToPractice() {
        val details = AppUiState(
            destination = AppDestination.RESULTS,
            resultsDetailVisible = true,
        )

        val summary = details.navigateBack()
        assertEquals(AppDestination.RESULTS, summary.destination)
        assertFalse(summary.resultsDetailVisible)

        val practice = summary.navigateBack()
        assertEquals(AppDestination.PRACTICE, practice.destination)
        assertEquals(ResultsPresentationState.None, practice.resultsPresentation)
    }

    @Test
    fun settingsOpensFromPracticeAndKeepsCalibrationAvailable() {
        val calibration = TimingCalibration(
            offsetSamples = 2_400L,
            sampleRateHz = 48_000,
            confidence = CalibrationConfidence.MEDIUM,
            expectedClickCount = 8,
            matchedClickCount = 7,
            offsetSpreadSamples = 30L,
            calibratedAtEpochMillis = 1_000L,
            algorithmVersion = 1,
        )

        val settingsState = AppUiState(
            activeTimingCalibration = calibration,
        ).openSettings()

        assertEquals(AppDestination.SETTINGS, settingsState.destination)
        assertEquals(calibration, settingsState.activeTimingCalibration)
    }

    @Test
    fun missingCalibrationOpensCalibrationOnAppStartup() {
        val state = initialAppUiState(calibration = null)

        assertEquals(AppDestination.TIMING_CALIBRATION, state.destination)
        assertNull(state.activeTimingCalibration)
    }

    @Test
    fun savedCalibrationOpensPracticeAndRemainsAvailableForManualRecalibration() {
        val calibration = TimingCalibration(
            offsetSamples = 3_456L,
            sampleRateHz = 48_000,
            confidence = CalibrationConfidence.HIGH,
            expectedClickCount = 8,
            matchedClickCount = 8,
            offsetSpreadSamples = 20L,
            calibratedAtEpochMillis = 1_000L,
            algorithmVersion = 1,
        )

        val state = initialAppUiState(calibration)

        assertEquals(AppDestination.PRACTICE, state.destination)
        assertEquals(calibration, state.activeTimingCalibration)
        assertEquals(
            AppDestination.TIMING_CALIBRATION,
            state.copy(destination = AppDestination.TIMING_CALIBRATION).destination,
        )
    }

    @Test
    fun timingCalibrationIsDedicatedDestinationAndReturnsToPractice() {
        val calibrationState = AppUiState(destination = AppDestination.TIMING_CALIBRATION)
        assertEquals(AppDestination.TIMING_CALIBRATION, calibrationState.destination)
    }

    @Test
    fun currentAndHistoricalRunsShareOneResultsPresentationModel() {
        val run = exerciseRunFixture(runId = "presentation-run")
        val current = AppUiState().openResults(run.practiceResult, run.productionGraph)
        val currentModel = (current.resultsPresentation as ResultsPresentationState.Ready).model
        assertEquals(ResultsSource.CurrentRun, currentModel.source)
        assertTrue(currentModel.retryAvailable)

        val historicalModel = currentModel.copy(
            source = ResultsSource.SavedRun(run.runId),
            retryDocumentUri = null,
        )
        assertEquals(run.practiceResult, historicalModel.result)
        assertEquals(run.productionGraph, historicalModel.graphModel)
        assertFalse(historicalModel.retryAvailable)

        val loaded = run.toSavedResultsPresentation(
            retryDocumentUri = "content://exercise/current",
        )
        assertSame(run.practiceResult, loaded.result)
        assertSame(run.productionGraph, loaded.graphModel)
        assertEquals(ResultsSource.SavedRun(run.runId), loaded.source)
        assertTrue(loaded.retryAvailable)
    }

    @Test
    fun libraryHistoryAndSavedResultsPreserveBackNavigation() {
        val history = AppUiState(
            destination = AppDestination.EXERCISE_LIBRARY,
        ).openExerciseHistory("exercise-id")

        assertEquals(AppDestination.EXERCISE_HISTORY, history.destination)
        assertEquals("exercise-id", history.historyExerciseId)
        assertEquals(AppDestination.EXERCISE_LIBRARY, history.navigateBack().destination)

        val savedResults = history.copy(
            destination = AppDestination.RESULTS,
            resultsReturnDestination = AppDestination.EXERCISE_HISTORY,
        )
        assertEquals(AppDestination.EXERCISE_HISTORY, savedResults.navigateBack().destination)
    }

    @Test
    fun selectingExerciseForPractice_returnsToPracticeWithDocumentToLoad() {
        val libraryState = AppUiState().openExerciseLibrary(
            ExerciseLibraryPurpose.PRACTICE,
        )

        val selectedState = libraryState.openLibraryExercise(
            "content://exercise/practice",
        )

        assertEquals(AppDestination.PRACTICE, selectedState.destination)
        assertEquals(
            "content://exercise/practice",
            selectedState.practiceDocumentUriToLoad,
        )
        assertFalse(selectedState.startPracticeAfterLoad)
        assertNull(selectedState.editorDocumentUri)
    }

    @Test
    fun selectingExerciseForModify_opensEditorAndReturnsToLibrary() {
        val libraryState = AppUiState().openExerciseLibrary(
            ExerciseLibraryPurpose.MODIFY,
        )

        val selectedState = libraryState.openLibraryExercise(
            "content://exercise/modify",
        )

        assertEquals(AppDestination.EXERCISE_EDITOR, selectedState.destination)
        assertEquals(
            "content://exercise/modify",
            selectedState.editorDocumentUri,
        )
        assertEquals(
            AppDestination.EXERCISE_LIBRARY,
            selectedState.editorReturnDestination,
        )
        assertNull(selectedState.practiceDocumentUriToLoad)
    }

    @Test
    fun playingSavedEditorExercise_returnsToPracticeAndRequestsAutoStart() {
        val editorState = AppUiState(
            destination = AppDestination.EXERCISE_EDITOR,
            editorDocumentUri = "content://exercise/editor",
        )

        val practiceState = editorState.playEditorExercise(
            "content://exercise/saved",
        )

        assertEquals(AppDestination.PRACTICE, practiceState.destination)
        assertEquals(
            "content://exercise/saved",
            practiceState.practiceDocumentUriToLoad,
        )
        assertTrue(practiceState.startPracticeAfterLoad)
        assertNull(practiceState.editorDocumentUri)
    }
}
