package com.titaniumharmonics.bad.audio.matching

import com.titaniumharmonics.bad.audio.detection.DetectedHit
import java.util.Collections

enum class HitJudgement {
    EARLY,
    ON_TIME,
    LATE,
    MISSED,
}

data class ExpectedNoteJudgement(
    val expected: ExpectedNoteTiming,
    val detectedHit: DetectedHit?,
    val timingErrorSamples: Long?,
    val timingErrorMillis: Double?,
    val judgement: HitJudgement,
)

class HitMatchingResult(
    expectedNoteJudgements: List<ExpectedNoteJudgement>,
    acceptedHits: List<DetectedHit>,
    extraDetectedHits: List<DetectedHit>,
    rejectedLowConfidenceHits: List<DetectedHit>,
    val configuration: JudgementConfiguration,
    val sampleRateHz: Int,
) {
    val expectedNoteJudgements: List<ExpectedNoteJudgement> =
        expectedNoteJudgements.immutableCopy()
    val matchedExpectedNotes: List<ExpectedNoteJudgement> =
        this.expectedNoteJudgements.filter { it.detectedHit != null }.immutableCopy()
    val unmatchedExpectedNotes: List<ExpectedNoteJudgement> =
        this.expectedNoteJudgements.filter { it.detectedHit == null }.immutableCopy()
    val acceptedHits: List<DetectedHit> = acceptedHits.immutableCopy()
    val extraDetectedHits: List<DetectedHit> = extraDetectedHits.immutableCopy()
    val rejectedLowConfidenceHits: List<DetectedHit> = rejectedLowConfidenceHits.immutableCopy()
}

private fun <T> List<T>.immutableCopy(): List<T> =
    Collections.unmodifiableList(ArrayList(this))
