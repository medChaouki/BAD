package com.titaniumharmonics.bad.audio.detection

import com.titaniumharmonics.bad.audio.analysis.ImmutableFloatSeries
import com.titaniumharmonics.bad.audio.analysis.ImmutableLongSeries
import java.util.Collections

enum class CandidateClassification {
    DRUM,
    METRONOME,
}

enum class CandidateRejectionReason {
    METRONOME_ONLY,
    BELOW_MINIMUM_CONFIDENCE,
    RETRIGGER_SUPPRESSION,
}

data class DetectedHit(
    val rawExerciseSample: Long,
    val rawExerciseTimeMillis: Double,
    val calibratedExerciseSample: Long,
    val calibratedExerciseTimeMillis: Double,
    val onsetFrame: Int,
    val peakFrame: Int,
    val peakExerciseSample: Long,
    val peakTimeMillis: Double,
    val peakAmplitude: Float,
    val frameLevel: Float,
    val envelope: Float,
    val noiseFloor: Float,
    val signalToNoiseRatio: Double,
    val confidence: Double,
    val metronomeBandRatio: Double,
    val broadbandResidualEnergy: Double,
    val spectralBandwidthHz: Double,
    val spectralCentroidHz: Double,
    val calibrationApplied: Boolean,
)

data class DetectedCandidate(
    val index: Int,
    val accepted: Boolean,
    val classification: CandidateClassification,
    val rejectionReason: CandidateRejectionReason?,
    val rawExerciseSample: Long,
    val rawTimeMillis: Double,
    val calibratedExerciseSample: Long,
    val calibratedTimeMillis: Double,
    val onsetFrame: Int,
    val peakFrame: Int,
    val peakExerciseSample: Long,
    val peakTimeMillis: Double,
    val peakAmplitude: Float,
    val frameLevel: Float,
    val envelope: Float,
    val noiseFloor: Float,
    val signalToNoiseRatio: Double,
    val metronomeBandRatio: Double,
    val broadbandResidualEnergy: Double,
    val spectralBandwidthHz: Double,
    val spectralCentroidHz: Double,
    val confidence: Double,
    val calibrationApplied: Boolean,
)

class HitDetectionResult(
    hits: List<DetectedHit>,
    rejectedMetronomeCandidates: List<DetectedCandidate>,
    otherRejectedCandidates: List<DetectedCandidate>,
    candidates: List<DetectedCandidate>,
    val adaptiveThreshold: ImmutableFloatSeries,
    val expectedExerciseSamples: ImmutableLongSeries,
    val configuration: HitDetectionConfiguration,
    val calibrationOffsetSamples: Long,
    val calibrationApplied: Boolean,
) {
    val hits: List<DetectedHit> = Collections.unmodifiableList(hits.toList())
    val rejectedMetronomeCandidates: List<DetectedCandidate> =
        Collections.unmodifiableList(rejectedMetronomeCandidates.toList())
    val otherRejectedCandidates: List<DetectedCandidate> =
        Collections.unmodifiableList(otherRejectedCandidates.toList())
    val candidates: List<DetectedCandidate> = Collections.unmodifiableList(candidates.toList())
    val totalCandidateCount: Int get() = candidates.size
    val acceptedCount: Int get() = hits.size
    val metronomeRejectedCount: Int get() = rejectedMetronomeCandidates.size
}

sealed interface HitDetectionState {
    data object NotStarted : HitDetectionState
    data object Detecting : HitDetectionState
    data class Ready(val result: HitDetectionResult) : HitDetectionState
    data class Failed(val message: String) : HitDetectionState
}
