package com.titaniumharmonics.bad.history

/** Temporary audio is eligible for deletion only after durable persistence succeeds. */
class ExerciseRunWavPolicy(
    private val debugRetentionEnabled: Boolean,
) {
    fun shouldDelete(persistenceSucceeded: Boolean): Boolean =
        persistenceSucceeded && !debugRetentionEnabled
}
