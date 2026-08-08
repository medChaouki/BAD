package com.titaniumharmonics.bad.ui.practice

import com.titaniumharmonics.bad.audio.DebugRecordingPlaybackState
import com.titaniumharmonics.bad.audio.RecordedSession
import com.titaniumharmonics.bad.audio.analysis.AudioAnalysisState
import com.titaniumharmonics.bad.audio.analysis.DebugCsvExportState
import com.titaniumharmonics.bad.audio.detection.HitDetectionState
import com.titaniumharmonics.bad.audio.result.PracticeResultState
import com.titaniumharmonics.bad.audio.result.PracticeVerdict
import com.titaniumharmonics.bad.exercise.ExercisePlaybackSettings
import com.titaniumharmonics.bad.exercise.RuntimeExercise
import com.titaniumharmonics.bad.history.ExerciseRunSaveState

enum class PracticePhase {
    UNLOADED,
    LOADING,
    READY,
    PREPARING,
    COUNTING_IN,
    RUNNING,
    PAUSED,
    RESUME_COUNT_IN,
    COMPLETED,
    PROCESSING,
    ERROR,
}

data class PracticeUiState(
    val exercise: RuntimeExercise? = null,
    val playbackSettings: ExercisePlaybackSettings? = null,
    val phase: PracticePhase = PracticePhase.UNLOADED,
    val exerciseElapsedNanos: Long = 0L,
    val countInBeatsRemaining: Int = 0,
    val recordedSession: RecordedSession? = null,
    val audioAnalysis: AudioAnalysisState = AudioAnalysisState.NotStarted,
    val hitDetection: HitDetectionState = HitDetectionState.NotStarted,
    val practiceResult: PracticeResultState = PracticeResultState.NotStarted,
    val practiceVerdict: PracticeVerdict? = null,
    val runSaveState: ExerciseRunSaveState = ExerciseRunSaveState.NotSaved,
    val debugCsvExport: DebugCsvExportState = DebugCsvExportState.NotStarted,
    val debugRecording: DebugRecordingPlaybackState = DebugRecordingPlaybackState(),
    val errorMessage: String? = null,
) {
    val playbackExercise: RuntimeExercise?
        get() {
            val loadedExercise = exercise ?: return null
            return playbackSettings?.applyTo(loadedExercise) ?: loadedExercise
        }
}
