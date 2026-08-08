package com.titaniumharmonics.bad.audio.result

import com.titaniumharmonics.bad.audio.matching.SessionJudgementSnapshot

enum class PracticeVerdict {
    EARLY,
    ON_TIME,
    LATE,
    MISSING,
    CREATIVE,
}

/** Pure high-level summary of a completed run using only its frozen judgement settings. */
object PracticeVerdictCalculator {
    fun calculate(
        result: PracticeResult,
        judgementSnapshot: SessionJudgementSnapshot,
    ): PracticeVerdict {
        val configuration = judgementSnapshot.configuration
        if (
            result.totalExpectedNotes == 0 ||
            result.hitRate < configuration.minimumHitRateForVerdict
        ) {
            return PracticeVerdict.MISSING
        }

        val bias = result.signedMeanTimingErrorMillis ?: return PracticeVerdict.MISSING
        if (result.extraHitRate > configuration.minimumExtraHitRateForCreativeVerdict) {
            return PracticeVerdict.CREATIVE
        }
        return when {
            bias < -configuration.onTimeBeforeMillis -> PracticeVerdict.EARLY
            bias > configuration.onTimeAfterMillis -> PracticeVerdict.LATE
            else -> PracticeVerdict.ON_TIME
        }
    }

    fun calculate(result: PracticeResult): PracticeVerdict =
        calculate(result, result.judgementSnapshot)
}
