package com.titaniumharmonics.bad.audio.detection

import com.titaniumharmonics.bad.audio.analysis.AudioAnalysis
import com.titaniumharmonics.bad.audio.analysis.ImmutableFloatSeries
import com.titaniumharmonics.bad.audio.analysis.ImmutableLongSeries
import com.titaniumharmonics.bad.audio.matching.RuntimeExerciseSampleTimeline
import com.titaniumharmonics.bad.exercise.RuntimeExercise
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

class OfflineDrumHitDetector {
    fun detect(
        analysis: AudioAnalysis,
        snapshot: SessionDetectionSnapshot,
        runtimeExercise: RuntimeExercise,
        cancellationCheck: () -> Unit = {},
    ): HitDetectionResult {
        val configuration = snapshot.configuration
        val thresholds = FloatArray(analysis.frameCount) { index ->
            max(
                configuration.minimumAbsoluteThreshold,
                analysis.noiseFloor[index] * configuration.noiseFloorMultiplier,
            ).toFloat()
        }
        val expectedExerciseSamples = expectedExerciseSamples(analysis, runtimeExercise)
        if (!configuration.enabled) {
            return result(
                analysis = analysis,
                snapshot = snapshot,
                thresholds = thresholds,
                expectedExerciseSamples = expectedExerciseSamples,
                workingCandidates = emptyList(),
            )
        }

        val candidates = ArrayList<WorkingCandidate>()
        var armed = true
        repeat(analysis.frameCount) { frameIndex ->
            cancellationCheck()
            val envelope = analysis.envelope[frameIndex].toDouble()
            val threshold = thresholds[frameIndex].toDouble()
            if (armed && envelope >= threshold) {
                val candidate = measureCandidate(
                    index = candidates.size,
                    crossingFrame = frameIndex,
                    analysis = analysis,
                    configuration = configuration,
                )
                candidates += classifyCandidate(
                    candidate = candidate,
                    analysis = analysis,
                    configuration = configuration,
                )
                armed = false
            } else if (!armed && envelope <= threshold * configuration.releaseHysteresisRatio) {
                armed = true
            }
        }
        suppressRetriggers(candidates, analysis.sampleRateHz, configuration)
        return result(
            analysis = analysis,
            snapshot = snapshot,
            thresholds = thresholds,
            expectedExerciseSamples = expectedExerciseSamples,
            workingCandidates = candidates,
        )
    }

    private fun measureCandidate(
        index: Int,
        crossingFrame: Int,
        analysis: AudioAnalysis,
        configuration: HitDetectionConfiguration,
    ): WorkingCandidate {
        val hopMillis = analysis.hopSizeSamples * 1_000.0 / analysis.sampleRateHz
        val lookBackFrames = ceil(configuration.onsetLookBackMillis / hopMillis).toInt()
        val searchStart = (crossingFrame - lookBackFrames).coerceAtLeast(0)
        var backgroundFrame = searchStart
        for (frame in searchStart..crossingFrame) {
            if (analysis.envelope[frame] < analysis.envelope[backgroundFrame]) {
                backgroundFrame = frame
            }
        }
        val background = analysis.envelope[backgroundFrame]
        var onsetFrame = crossingFrame
        for (frame in backgroundFrame..crossingFrame) {
            val previous = analysis.envelope[(frame - 1).coerceAtLeast(backgroundFrame)]
            if (
                analysis.envelope[frame] - background >= configuration.minimumAttackRise &&
                analysis.envelope[frame] >= previous
            ) {
                onsetFrame = frame
                break
            }
        }

        val peakFrames = ceil(configuration.peakSearchMillis / hopMillis).toInt().coerceAtLeast(1)
        val peakEnd = (onsetFrame + peakFrames).coerceAtMost(analysis.frameCount - 1)
        var peakFrame = onsetFrame
        for (frame in onsetFrame..peakEnd) {
            if (analysis.framePeaks[frame] > analysis.framePeaks[peakFrame]) peakFrame = frame
        }
        val noiseFloor = analysis.noiseFloor[onsetFrame].toDouble()
        val signalToNoise = analysis.envelope[peakFrame] / noiseFloor.coerceAtLeast(MINIMUM_LEVEL)
        val attackRise = (analysis.envelope[peakFrame] - background).coerceAtLeast(0.0f)
        val signalConfidence = (
            0.5 * (signalToNoise / (configuration.minimumSignalToNoiseRatio * 2.0)) +
                0.5 * (
                    attackRise / (configuration.minimumAttackRise.coerceAtLeast(MINIMUM_LEVEL) * 4.0)
                    )
            ).coerceIn(0.0, 1.0)
        return WorkingCandidate(
            index = index,
            onsetFrame = onsetFrame,
            peakFrame = peakFrame,
            onsetSample = analysis.frameCenterExerciseSamples[onsetFrame],
            peakSample = analysis.frameCenterExerciseSamples[peakFrame],
            peakAmplitude = analysis.framePeaks[peakFrame],
            frameLevel = analysis.frameLevels[peakFrame],
            envelope = analysis.envelope[peakFrame],
            noiseFloor = analysis.noiseFloor[onsetFrame],
            signalToNoiseRatio = signalToNoise,
            confidence = signalConfidence,
            attackRise = attackRise.toDouble(),
        )
    }

    private fun classifyCandidate(
        candidate: WorkingCandidate,
        analysis: AudioAnalysis,
        configuration: HitDetectionConfiguration,
    ): WorkingCandidate {
        val rejection = configuration.metronomeRejection
        val spectrum = ShortWindowSpectrum.analyze(
            pcm = analysis.postNotchPcm,
            centerSample = candidate.peakSample,
            sampleRateHz = analysis.sampleRateHz,
            metronomeFrequencyHz = analysis.metronomeConfiguration.tone.frequencyHz,
            configuration = rejection,
        )
        candidate.spectrum = spectrum
        if (!rejection.enabled) {
            rejectWeakCandidate(candidate, configuration)
            return candidate
        }

        val maximumDistanceSamples = millisecondsToSamples(
            rejection.maximumScheduledDistanceMillis,
            analysis.sampleRateHz,
        )
        val nearScheduledClick = analysis.expectedMetronomeExerciseSamples.toList().any {
            abs(it - candidate.onsetSample) <= maximumDistanceSamples
        }
        val narrowness = (
            1.0 - spectrum.spectralBandwidthHz /
                (rejection.metronomeBandWidthHz * 1.5)
            ).coerceIn(0.0, 1.0)
        val residualWeakness = (
            1.0 - spectrum.broadbandResidualEnergy /
                rejection.minimumBroadbandResidualEnergy.coerceAtLeast(MINIMUM_LEVEL)
            ).coerceIn(0.0, 1.0)
        val spectralConfidence = (
            spectrum.metronomeBandRatio * 0.65 + narrowness * 0.35
            ) * residualWeakness
        val clearMetronomeOnly = nearScheduledClick &&
            spectrum.metronomeBandRatio >= rejection.minimumMetronomeBandEnergyRatio &&
            spectrum.broadbandResidualEnergy < rejection.minimumBroadbandResidualEnergy &&
            spectrum.spectralBandwidthHz <= rejection.metronomeBandWidthHz * 1.5 &&
            spectralConfidence >= rejection.spectralConfidenceThreshold
        val uncertainMetronome = nearScheduledClick &&
            spectrum.metronomeBandRatio >= rejection.minimumMetronomeBandEnergyRatio &&
            spectralConfidence >= rejection.spectralConfidenceThreshold * 0.8

        if (
            clearMetronomeOnly ||
            uncertainMetronome &&
            rejection.uncertainCandidateBehaviour == UncertainCandidateBehaviour.REJECT_AS_METRONOME
        ) {
            candidate.classification = CandidateClassification.METRONOME
            candidate.rejectionReason = CandidateRejectionReason.METRONOME_ONLY
            candidate.confidence = spectralConfidence.coerceIn(0.0, 1.0)
        } else if (uncertainMetronome) {
            candidate.confidence *= 0.75
        }
        rejectWeakCandidate(candidate, configuration)
        return candidate
    }

    private fun rejectWeakCandidate(
        candidate: WorkingCandidate,
        configuration: HitDetectionConfiguration,
    ) {
        if (
            candidate.classification == CandidateClassification.DRUM &&
            (
                candidate.signalToNoiseRatio < configuration.minimumSignalToNoiseRatio ||
                    candidate.attackRise < configuration.minimumAttackRise ||
                    candidate.confidence < configuration.minimumConfidence
                )
        ) {
            candidate.rejectionReason = CandidateRejectionReason.BELOW_MINIMUM_CONFIDENCE
        }
    }

    private fun suppressRetriggers(
        candidates: MutableList<WorkingCandidate>,
        sampleRateHz: Int,
        configuration: HitDetectionConfiguration,
    ) {
        val minimumSpacing = millisecondsToSamples(
            configuration.minimumHitSpacingMillis,
            sampleRateHz,
        )
        var retained: WorkingCandidate? = null
        candidates.filter {
            it.classification == CandidateClassification.DRUM && it.rejectionReason == null
        }.forEach { candidate ->
            val previous = retained
            if (previous == null || candidate.onsetSample - previous.onsetSample >= minimumSpacing) {
                retained = candidate
            } else if (candidate.peakAmplitude > previous.peakAmplitude) {
                previous.rejectionReason = CandidateRejectionReason.RETRIGGER_SUPPRESSION
                retained = candidate
            } else {
                candidate.rejectionReason = CandidateRejectionReason.RETRIGGER_SUPPRESSION
            }
        }
    }

    private fun result(
        analysis: AudioAnalysis,
        snapshot: SessionDetectionSnapshot,
        thresholds: FloatArray,
        expectedExerciseSamples: LongArray,
        workingCandidates: List<WorkingCandidate>,
    ): HitDetectionResult {
        val calibration = snapshot.timingCalibration.takeIf {
            snapshot.configuration.applyTimingCalibration
        }
        val calibrationOffset = calibration?.offsetSamplesAt(analysis.sampleRateHz) ?: 0L
        val calibrationApplied = calibration != null
        val candidates = workingCandidates.map { candidate ->
            val calibratedSample = Math.subtractExact(candidate.onsetSample, calibrationOffset)
            val spectrum = candidate.spectrum ?: CandidateSpectrum(0.0, 0.0, 0.0, 0.0)
            DetectedCandidate(
                index = candidate.index,
                accepted = candidate.classification == CandidateClassification.DRUM &&
                    candidate.rejectionReason == null,
                classification = candidate.classification,
                rejectionReason = candidate.rejectionReason,
                rawExerciseSample = candidate.onsetSample,
                rawTimeMillis = sampleToMillis(candidate.onsetSample, analysis.sampleRateHz),
                calibratedExerciseSample = calibratedSample,
                calibratedTimeMillis = sampleToMillis(calibratedSample, analysis.sampleRateHz),
                onsetFrame = candidate.onsetFrame,
                peakFrame = candidate.peakFrame,
                peakExerciseSample = candidate.peakSample,
                peakTimeMillis = sampleToMillis(candidate.peakSample, analysis.sampleRateHz),
                peakAmplitude = candidate.peakAmplitude,
                frameLevel = candidate.frameLevel,
                envelope = candidate.envelope,
                noiseFloor = candidate.noiseFloor,
                signalToNoiseRatio = candidate.signalToNoiseRatio,
                metronomeBandRatio = spectrum.metronomeBandRatio,
                broadbandResidualEnergy = spectrum.broadbandResidualEnergy,
                spectralBandwidthHz = spectrum.spectralBandwidthHz,
                spectralCentroidHz = spectrum.spectralCentroidHz,
                confidence = candidate.confidence,
                calibrationApplied = calibrationApplied,
            )
        }
        val hits = candidates.filter(DetectedCandidate::accepted).map { candidate ->
            DetectedHit(
                rawExerciseSample = candidate.rawExerciseSample,
                rawExerciseTimeMillis = candidate.rawTimeMillis,
                calibratedExerciseSample = candidate.calibratedExerciseSample,
                calibratedExerciseTimeMillis = candidate.calibratedTimeMillis,
                onsetFrame = candidate.onsetFrame,
                peakFrame = candidate.peakFrame,
                peakExerciseSample = candidate.peakExerciseSample,
                peakTimeMillis = candidate.peakTimeMillis,
                peakAmplitude = candidate.peakAmplitude,
                frameLevel = candidate.frameLevel,
                envelope = candidate.envelope,
                noiseFloor = candidate.noiseFloor,
                signalToNoiseRatio = candidate.signalToNoiseRatio,
                confidence = candidate.confidence,
                metronomeBandRatio = candidate.metronomeBandRatio,
                broadbandResidualEnergy = candidate.broadbandResidualEnergy,
                spectralBandwidthHz = candidate.spectralBandwidthHz,
                spectralCentroidHz = candidate.spectralCentroidHz,
                calibrationApplied = candidate.calibrationApplied,
            )
        }
        return HitDetectionResult(
            hits = hits,
            rejectedMetronomeCandidates = candidates.filter {
                it.rejectionReason == CandidateRejectionReason.METRONOME_ONLY
            },
            otherRejectedCandidates = candidates.filter {
                it.rejectionReason != null &&
                    it.rejectionReason != CandidateRejectionReason.METRONOME_ONLY
            },
            candidates = candidates,
            adaptiveThreshold = ImmutableFloatSeries.fromOwned(thresholds),
            expectedExerciseSamples = ImmutableLongSeries.copyOf(expectedExerciseSamples),
            configuration = snapshot.configuration,
            calibrationOffsetSamples = calibrationOffset,
            calibrationApplied = calibrationApplied,
        )
    }

    private fun expectedExerciseSamples(
        analysis: AudioAnalysis,
        runtimeExercise: RuntimeExercise,
    ): LongArray = RuntimeExerciseSampleTimeline.expectedSamples(
        runtimeExercise,
        analysis.sampleRateHz,
    )

    private data class WorkingCandidate(
        val index: Int,
        val onsetFrame: Int,
        val peakFrame: Int,
        val onsetSample: Long,
        val peakSample: Long,
        val peakAmplitude: Float,
        val frameLevel: Float,
        val envelope: Float,
        val noiseFloor: Float,
        val signalToNoiseRatio: Double,
        var confidence: Double,
        val attackRise: Double,
        var spectrum: CandidateSpectrum? = null,
        var classification: CandidateClassification = CandidateClassification.DRUM,
        var rejectionReason: CandidateRejectionReason? = null,
    )

    private companion object {
        const val MINIMUM_LEVEL = 1e-9

        fun millisecondsToSamples(milliseconds: Double, sampleRateHz: Int): Long =
            (milliseconds * sampleRateHz / 1_000.0).toLong()

        fun sampleToMillis(sample: Long, sampleRateHz: Int): Double =
            sample * 1_000.0 / sampleRateHz
    }
}
