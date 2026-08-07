package com.titaniumharmonics.bad.audio.result

import com.titaniumharmonics.bad.audio.matching.HitJudgement
import com.titaniumharmonics.bad.audio.matching.JudgementConfiguration
import com.titaniumharmonics.bad.audio.matching.SessionJudgementSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class PracticeVerdictCalculatorTest {
    private val base = graphFixture().result

    @Test
    fun classifiesEarlyLateAndOnTimeFromSignedMeanBias() {
        assertEquals(PracticeVerdict.EARLY, calculate(result(bias = -40.001)))
        assertEquals(PracticeVerdict.LATE, calculate(result(bias = 40.001)))
        assertEquals(PracticeVerdict.ON_TIME, calculate(result(bias = 0.0)))
    }

    @Test
    fun exactHitRateThresholdIsEligibleAndJustBelowIsMissing() {
        val threshold = base.hitRate
        assertEquals(
            PracticeVerdict.ON_TIME,
            calculate(result(bias = 0.0), minimumHitRate = threshold),
        )
        assertEquals(
            PracticeVerdict.MISSING,
            calculate(result(bias = 500.0), minimumHitRate = threshold + 0.000001),
        )
    }

    @Test
    fun exactTimingBoundariesRemainOnTime() {
        assertEquals(PracticeVerdict.ON_TIME, calculate(result(bias = -40.0)))
        assertEquals(PracticeVerdict.ON_TIME, calculate(result(bias = 40.0)))
    }

    @Test
    fun zeroExpectedAndZeroMatchedAreMissing() {
        assertEquals(PracticeVerdict.MISSING, calculate(result(bias = null, empty = true)))
        assertEquals(PracticeVerdict.MISSING, calculate(result(bias = null, missedOnly = true)))
    }

    @Test
    fun missingIgnoresExtremeTimingBias() {
        val lowHitResult = result(bias = -900.0)
        assertEquals(PracticeVerdict.MISSING, calculate(lowHitResult, minimumHitRate = 0.8))
    }

    private fun calculate(
        result: PracticeResult,
        minimumHitRate: Double = 0.30,
    ): PracticeVerdict = PracticeVerdictCalculator.calculate(
        result,
        SessionJudgementSnapshot(
            JudgementConfiguration.DEFAULT.copy(
                minimumHitRateForVerdict = minimumHitRate,
            ),
        ),
    )

    private fun result(
        bias: Double?,
        empty: Boolean = false,
        missedOnly: Boolean = false,
    ): PracticeResult {
        val notes = when {
            empty -> emptyList()
            missedOnly -> listOf(base.judgedNotes.single { it.judgement == HitJudgement.MISSED })
            else -> base.judgedNotes
        }
        val total = notes.size
        val early = notes.count { it.judgement == HitJudgement.EARLY }
        val onTime = notes.count { it.judgement == HitJudgement.ON_TIME }
        val late = notes.count { it.judgement == HitJudgement.LATE }
        val missed = notes.count { it.judgement == HitJudgement.MISSED }
        val hitRate = if (total == 0) 0.0 else (total - missed).toDouble() / total
        return PracticeResult(
            schemaVersion = base.schemaVersion,
            exerciseId = base.exerciseId,
            exerciseName = base.exerciseName,
            bpm = base.bpm,
            sampleRateHz = base.sampleRateHz,
            runtimeExercise = base.runtimeExercise,
            judgedNotes = notes,
            extraHits = emptyList(),
            totalExpectedNotes = total,
            earlyCount = early,
            onTimeCount = onTime,
            lateCount = late,
            missedCount = missed,
            extraCount = 0,
            accuracy = if (total == 0) 0.0 else onTime.toDouble() / total,
            hitRate = hitRate,
            meanAbsoluteTimingErrorMillis = bias?.let(::abs),
            signedMeanTimingErrorMillis = bias,
            medianAbsoluteTimingErrorMillis = bias?.let(::abs),
            timingErrorStandardDeviationMillis = bias?.let { 0.0 },
            missedRate = if (total == 0) 0.0 else missed.toDouble() / total,
            extraHitRate = 0.0,
            meanRelativeIntensity = null,
            minimumRelativeIntensity = null,
            maximumRelativeIntensity = null,
            judgementSnapshot = base.judgementSnapshot,
            detectionSnapshot = base.detectionSnapshot,
            metronomeSnapshot = base.metronomeSnapshot,
            timingCalibration = base.timingCalibration,
            calibrationOffsetSamples = base.calibrationOffsetSamples,
            calibrationApplied = base.calibrationApplied,
        )
    }
}
