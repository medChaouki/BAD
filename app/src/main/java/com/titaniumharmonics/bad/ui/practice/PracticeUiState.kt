package com.titaniumharmonics.bad.ui.practice

import com.titaniumharmonics.bad.exercise.Exercise

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
    val phase: PracticePhase = PracticePhase.UNLOADED,
    val exerciseElapsedNanos: Long = 0L,
    val countInBeatsRemaining: Int = 0,
    val errorMessage: String? = null,
)
