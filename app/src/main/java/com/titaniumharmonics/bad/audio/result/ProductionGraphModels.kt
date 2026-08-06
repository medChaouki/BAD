package com.titaniumharmonics.bad.audio.result

import com.titaniumharmonics.bad.audio.matching.HitJudgement
import java.util.Collections

data class ProductionEnvelopePoint(
    val exerciseSample: Long,
    val exerciseTimeMillis: Double,
    val amplitude: Float,
)

data class ProductionExpectedNoteMarker(
    val expectedNoteIndex: Int,
    val exerciseSample: Long,
    val exerciseTimeMillis: Double,
    val measureIndex: Int,
    val beatPosition: Double,
    val judgement: HitJudgement,
)

data class ProductionMatchedHitMarker(
    val expectedNoteIndex: Int,
    val calibratedExerciseSample: Long,
    val calibratedExerciseTimeMillis: Double,
    val judgement: HitJudgement,
    val relativeIntensity: Double,
    val confidence: Double,
)

data class ProductionTimingConnector(
    val expectedNoteIndex: Int,
    val expectedExerciseSample: Long,
    val calibratedActualSample: Long,
    val timingErrorSamples: Long,
    val timingErrorMillis: Double,
)

data class ProductionMissedNoteMarker(
    val expectedNoteIndex: Int,
    val exerciseSample: Long,
    val exerciseTimeMillis: Double,
    val measureIndex: Int,
    val beatPosition: Double,
)

data class ProductionExtraHitMarker(
    val detectedHitIndex: Int,
    val calibratedExerciseSample: Long,
    val calibratedExerciseTimeMillis: Double,
    val relativeIntensity: Double,
    val confidence: Double,
)

data class ProductionMeasureGuide(
    val measureIndex: Int,
    val exerciseSample: Long,
)

/**
 * Bounded, persistence-ready production graph data. It intentionally contains no raw hit
 * timestamps, WAV reference, PCM, calibration internals, or debug DSP layers.
 */
class ProductionGraphModel(
    val exerciseId: String,
    val sampleRateHz: Int,
    val exerciseDurationSamples: Long,
    val exerciseDurationMillis: Double,
    val maximumEnvelopeAmplitude: Float,
    envelopePoints: List<ProductionEnvelopePoint>,
    expectedNotes: List<ProductionExpectedNoteMarker>,
    matchedHits: List<ProductionMatchedHitMarker>,
    timingConnectors: List<ProductionTimingConnector>,
    missedNotes: List<ProductionMissedNoteMarker>,
    extraHits: List<ProductionExtraHitMarker>,
    measureGuides: List<ProductionMeasureGuide>,
) {
    val envelopePoints = envelopePoints.immutableCopy()
    val expectedNotes = expectedNotes.immutableCopy()
    val matchedHits = matchedHits.immutableCopy()
    val timingConnectors = timingConnectors.immutableCopy()
    val missedNotes = missedNotes.immutableCopy()
    val extraHits = extraHits.immutableCopy()
    val measureGuides = measureGuides.immutableCopy()

    init {
        require(exerciseId.isNotBlank())
        require(sampleRateHz > 0)
        require(exerciseDurationSamples >= 0L)
        require(exerciseDurationMillis.isFinite() && exerciseDurationMillis >= 0.0)
        require(maximumEnvelopeAmplitude.isFinite() && maximumEnvelopeAmplitude >= 0.0f)
        require(this.envelopePoints.zipWithNext().all { it.first.exerciseSample <= it.second.exerciseSample })
        require(this.envelopePoints.size <= MAXIMUM_ENVELOPE_POINT_COUNT)
    }

    companion object {
        const val MAXIMUM_ENVELOPE_POINT_COUNT = 1_500
    }
}

sealed interface ProductionGraphBuildResult {
    data class Success(val model: ProductionGraphModel) : ProductionGraphBuildResult
    data class Failure(val message: String) : ProductionGraphBuildResult
}

private fun <T> List<T>.immutableCopy(): List<T> =
    Collections.unmodifiableList(ArrayList(this))

