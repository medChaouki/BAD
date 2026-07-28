package com.titaniumharmonics.bad.timing

enum class PlaybackPhase {
    COUNTING_IN,
    RUNNING,
    COMPLETED,
}

data class SessionProgress(
    val phase: PlaybackPhase,
    val sessionElapsedNanos: Long,
    val exerciseElapsedNanos: Long,
)

class SessionProgressCalculator(
    private val timing: ExerciseTiming,
) {
    fun calculate(sessionElapsedNanos: Long): SessionProgress {
        val boundedSessionElapsedNanos = sessionElapsedNanos.coerceAtLeast(0L)
        val exerciseElapsedNanos =
            boundedSessionElapsedNanos - timing.countInDurationNanos

        val phase = when {
            exerciseElapsedNanos < 0L -> PlaybackPhase.COUNTING_IN
            exerciseElapsedNanos < timing.exerciseDurationNanos -> PlaybackPhase.RUNNING
            else -> PlaybackPhase.COMPLETED
        }

        return SessionProgress(
            phase = phase,
            sessionElapsedNanos = boundedSessionElapsedNanos,
            exerciseElapsedNanos = exerciseElapsedNanos.coerceAtMost(
                timing.exerciseDurationNanos,
            ),
        )
    }
}
