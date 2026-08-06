package com.titaniumharmonics.bad.audio.matching

data class JudgementConfiguration(
    val onTimeBeforeMillis: Double = DEFAULT_ON_TIME_BEFORE_MILLIS,
    val onTimeAfterMillis: Double = DEFAULT_ON_TIME_AFTER_MILLIS,
    val maximumEarlyMillis: Double = DEFAULT_MAXIMUM_EARLY_MILLIS,
    val maximumLateMillis: Double = DEFAULT_MAXIMUM_LATE_MILLIS,
    val minimumDetectedHitConfidence: Double = DEFAULT_MINIMUM_CONFIDENCE,
    val extraHitHandlingEnabled: Boolean = true,
    val version: Int = CURRENT_VERSION,
) {
    init {
        require(onTimeBeforeMillis.isValidWindow())
        require(onTimeAfterMillis.isValidWindow())
        require(maximumEarlyMillis.isValidWindow())
        require(maximumLateMillis.isValidWindow())
        require(maximumEarlyMillis >= onTimeBeforeMillis)
        require(maximumLateMillis >= onTimeAfterMillis)
        require(
            minimumDetectedHitConfidence.isFinite() &&
                minimumDetectedHitConfidence in 0.0..1.0,
        )
        require(version > 0)
    }

    companion object {
        const val CURRENT_VERSION = 1
        const val DEFAULT_ON_TIME_BEFORE_MILLIS = 40.0
        const val DEFAULT_ON_TIME_AFTER_MILLIS = 40.0
        const val DEFAULT_MAXIMUM_EARLY_MILLIS = 120.0
        const val DEFAULT_MAXIMUM_LATE_MILLIS = 120.0
        const val DEFAULT_MINIMUM_CONFIDENCE = 0.30
        const val MAXIMUM_WINDOW_MILLIS = 1_000.0
        val DEFAULT = JudgementConfiguration()
    }
}

private fun Double.isValidWindow(): Boolean =
    isFinite() && this in 0.0..JudgementConfiguration.MAXIMUM_WINDOW_MILLIS
