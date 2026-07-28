package com.titaniumharmonics.bad.timing

class SessionElapsedClock(
    private val clock: MonotonicClock,
) {
    private var startedNanos: Long? = null
    private var pausedAtNanos: Long? = null
    private var accumulatedPausedNanos: Long = 0L

    val isPaused: Boolean
        get() = pausedAtNanos != null

    fun start(startedNanos: Long) {
        this.startedNanos = startedNanos
        pausedAtNanos = null
        accumulatedPausedNanos = 0L
    }

    fun pause(): Long? {
        if (startedNanos == null) return null
        if (pausedAtNanos == null) {
            pausedAtNanos = clock.nowNanos()
        }
        return elapsedNanos()
    }

    fun resume(): Long? {
        val pausedAt = pausedAtNanos ?: return elapsedNanos()
        accumulatedPausedNanos += (clock.nowNanos() - pausedAt).coerceAtLeast(0L)
        pausedAtNanos = null
        return elapsedNanos()
    }

    fun elapsedNanos(): Long? {
        val startedAt = startedNanos ?: return null
        val currentNanos = pausedAtNanos ?: clock.nowNanos()
        return (currentNanos - startedAt - accumulatedPausedNanos).coerceAtLeast(0L)
    }

    fun reset() {
        startedNanos = null
        pausedAtNanos = null
        accumulatedPausedNanos = 0L
    }
}
