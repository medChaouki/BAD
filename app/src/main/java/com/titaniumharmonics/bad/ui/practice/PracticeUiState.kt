package com.titaniumharmonics.bad.ui.practice

import com.titaniumharmonics.bad.exercise.Exercise
import com.titaniumharmonics.bad.exercise.ExercisePlaybackSettings

enum class PracticePhase {
    UNLOADED,
    LOADING,
    READY,
    PREPARING,
    COUNTING_IN,
    RUNNING,
    COMPLETED,
    ERROR,
}

data class PracticeUiState(
    val exercise: Exercise? = null,
    val playbackSettings: ExercisePlaybackSettings? = null,
    val phase: PracticePhase = PracticePhase.UNLOADED,
    val exerciseElapsedNanos: Long = 0L,
    val countInBeatsRemaining: Int = 0,
    val errorMessage: String? = null,
) {
    val playbackExercise: Exercise?
        get() {
            val loadedExercise = exercise ?: return null
            return playbackSettings?.applyTo(loadedExercise) ?: loadedExercise
        }
}
