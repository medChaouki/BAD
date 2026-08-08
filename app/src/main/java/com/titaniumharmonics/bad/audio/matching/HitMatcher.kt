package com.titaniumharmonics.bad.audio.matching

import com.titaniumharmonics.bad.audio.detection.DetectedHit
import com.titaniumharmonics.bad.exercise.RuntimeExercise
import kotlin.math.abs

/** Deterministic dynamic-programming matcher over chronological expected notes and hits. */
object HitMatcher {
    fun match(
        runtimeExercise: RuntimeExercise,
        detectedHits: List<DetectedHit>,
        sampleRateHz: Int,
        configuration: JudgementConfiguration,
    ): HitMatchingResult {
        val expected = RuntimeExerciseSampleTimeline.expectedNotes(runtimeExercise, sampleRateHz)
        val indexedHits = detectedHits.mapIndexed(::IndexedHit)
        val rejected = indexedHits.filter {
            it.hit.confidence < configuration.minimumDetectedHitConfidence
        }
        val accepted = indexedHits.filter {
            it.hit.confidence >= configuration.minimumDetectedHitConfidence
        }.sortedWith(
            compareBy<IndexedHit> { it.hit.calibratedExerciseSample }
                .thenBy(IndexedHit::inputIndex),
        )

        val windows = SampleWindows.from(configuration, sampleRateHz)
        val decisions = buildDecisions(expected, accepted, windows, configuration)
        val judgements = ArrayList<ExpectedNoteJudgement>(expected.size)
        val extras = ArrayList<DetectedHit>()
        var expectedIndex = 0
        var hitIndex = 0
        while (expectedIndex < expected.size || hitIndex < accepted.size) {
            when (decisions[index(expectedIndex, hitIndex, accepted.size)]) {
                Operation.MATCH -> {
                    val expectedNote = expected[expectedIndex]
                    val hit = accepted[hitIndex].hit
                    val error = hit.calibratedExerciseSample - expectedNote.exerciseSample
                    judgements += ExpectedNoteJudgement(
                        expected = expectedNote,
                        detectedHit = hit,
                        timingErrorSamples = error,
                        timingErrorMillis = RuntimeExerciseSampleTimeline.samplesToMillis(
                            error,
                            sampleRateHz,
                        ),
                        judgement = windows.judgement(error),
                    )
                    expectedIndex++
                    hitIndex++
                }
                Operation.MISS -> {
                    judgements += ExpectedNoteJudgement(
                        expected = expected[expectedIndex++],
                        detectedHit = null,
                        timingErrorSamples = null,
                        timingErrorMillis = null,
                        judgement = HitJudgement.MISSED,
                    )
                }
                Operation.EXTRA -> extras += accepted[hitIndex++].hit
                Operation.END -> break
            }
        }

        return HitMatchingResult(
            expectedNoteJudgements = judgements,
            acceptedHits = accepted.map(IndexedHit::hit),
            extraDetectedHits = extras,
            rejectedLowConfidenceHits = rejected.map(IndexedHit::hit),
            configuration = configuration,
            sampleRateHz = sampleRateHz,
        )
    }

    private fun buildDecisions(
        expected: List<ExpectedNoteTiming>,
        hits: List<IndexedHit>,
        windows: SampleWindows,
        configuration: JudgementConfiguration,
    ): Array<Operation> {
        val columns = hits.size + 1
        val scores = arrayOfNulls<Score>((expected.size + 1) * columns)
        val decisions = Array(scores.size) { Operation.END }
        scores[index(expected.size, hits.size, hits.size)] = Score(0L, 0L)

        for (expectedIndex in expected.size downTo 0) {
            for (hitIndex in hits.size downTo 0) {
                if (expectedIndex == expected.size && hitIndex == hits.size) continue
                val candidates = ArrayList<Candidate>(3)
                if (expectedIndex < expected.size && hitIndex < hits.size) {
                    val error = hits[hitIndex].hit.calibratedExerciseSample -
                        expected[expectedIndex].exerciseSample
                    if (error in -windows.maximumEarly..windows.maximumLate) {
                        val next = scores[index(expectedIndex + 1, hitIndex + 1, hits.size)]!!
                        candidates += Candidate(
                            Operation.MATCH,
                            next.plus(absSafely(error), absSafely(error)),
                            chronologicalRank = 0,
                        )
                    }
                }
                if (expectedIndex < expected.size) {
                    val next = scores[index(expectedIndex + 1, hitIndex, hits.size)]!!
                    candidates += Candidate(
                        Operation.MISS,
                        next.plus(windows.maximumLate, 0L),
                        chronologicalRank = if (
                            hitIndex == hits.size ||
                            expected[expectedIndex].exerciseSample <=
                            hits[hitIndex].hit.calibratedExerciseSample
                        ) 1 else 2,
                    )
                }
                if (hitIndex < hits.size) {
                    val next = scores[index(expectedIndex, hitIndex + 1, hits.size)]!!
                    candidates += Candidate(
                        Operation.EXTRA,
                        next.plus(
                            if (configuration.extraHitHandlingEnabled) {
                                windows.maximumEarly
                            } else {
                                0L
                            },
                            0L,
                        ),
                        chronologicalRank = if (
                            expectedIndex == expected.size ||
                            hits[hitIndex].hit.calibratedExerciseSample <
                            expected[expectedIndex].exerciseSample
                        ) 1 else 2,
                    )
                }
                val best = candidates.minWithOrNull(Candidate.COMPARATOR)!!
                scores[index(expectedIndex, hitIndex, hits.size)] = best.score
                decisions[index(expectedIndex, hitIndex, hits.size)] = best.operation
            }
        }
        return decisions
    }

    private fun index(expectedIndex: Int, hitIndex: Int, hitCount: Int): Int =
        expectedIndex * (hitCount + 1) + hitIndex

    private fun absSafely(value: Long): Long = if (value == Long.MIN_VALUE) Long.MAX_VALUE else abs(value)

    private data class IndexedHit(val inputIndex: Int, val hit: DetectedHit)

    private data class Score(val totalCost: Long, val matchedAbsoluteError: Long) {
        fun plus(cost: Long, error: Long): Score = Score(
            totalCost = saturatedAdd(totalCost, cost),
            matchedAbsoluteError = saturatedAdd(matchedAbsoluteError, error),
        )
    }

    private data class Candidate(
        val operation: Operation,
        val score: Score,
        val chronologicalRank: Int,
    ) {
        companion object {
            val COMPARATOR = compareBy<Candidate> { it.score.totalCost }
                .thenBy { it.score.matchedAbsoluteError }
                .thenBy(Candidate::chronologicalRank)
                .thenBy { it.operation.stableRank }
        }
    }

    private enum class Operation(val stableRank: Int) {
        MATCH(0),
        MISS(1),
        EXTRA(2),
        END(3),
    }

    private data class SampleWindows(
        val onTimeBefore: Long,
        val onTimeAfter: Long,
        val maximumEarly: Long,
        val maximumLate: Long,
    ) {
        fun judgement(error: Long): HitJudgement = when {
            error < -onTimeBefore -> HitJudgement.EARLY
            error <= onTimeAfter -> HitJudgement.ON_TIME
            else -> HitJudgement.LATE
        }

        companion object {
            fun from(configuration: JudgementConfiguration, sampleRateHz: Int) = SampleWindows(
                onTimeBefore = RuntimeExerciseSampleTimeline.millisecondsToSamples(
                    configuration.onTimeBeforeMillis,
                    sampleRateHz,
                ),
                onTimeAfter = RuntimeExerciseSampleTimeline.millisecondsToSamples(
                    configuration.onTimeAfterMillis,
                    sampleRateHz,
                ),
                maximumEarly = RuntimeExerciseSampleTimeline.millisecondsToSamples(
                    configuration.maximumEarlyMillis,
                    sampleRateHz,
                ),
                maximumLate = RuntimeExerciseSampleTimeline.millisecondsToSamples(
                    configuration.maximumLateMillis,
                    sampleRateHz,
                ),
            )
        }
    }

    private fun saturatedAdd(first: Long, second: Long): Long =
        if (first > Long.MAX_VALUE - second) Long.MAX_VALUE else first + second
}
