package com.titaniumharmonics.bad.audio.analysis

import com.titaniumharmonics.bad.audio.RecordedSession
import java.io.File
import kotlin.math.abs

class OfflineAudioAnalyzer(
    private val wavReader: WavPcm16Reader = WavPcm16Reader(),
    private val configuration: AudioAnalysisConfig = AudioAnalysisConfig(),
) {
    fun analyze(
        session: RecordedSession,
        cancellationCheck: () -> Unit = {},
    ): AudioAnalysis {
        cancellationCheck()
        val wav = wavReader.read(File(session.wavFilePath))
        val graded = RecordedSessionAudioValidator(
            sampleCountToleranceFrames = configuration.sampleCountToleranceFrames,
        ).validateAndExtract(session, wav)
        cancellationCheck()

        val normalizedAndFiltered = Pcm16Normalizer.normalize(graded.gradedSamples)
        val maximumInputAmplitude = normalizedAndFiltered.maxOf { abs(it) }
        MeanDcOffsetRemover.removeInPlace(normalizedAndFiltered)
        if (configuration.highPassEnabled) {
            FirstOrderHighPassFilter.filterInPlace(
                signal = normalizedAndFiltered,
                sampleRateHz = wav.format.sampleRateHz,
                cutoffHz = configuration.highPassCutoffHz,
            )
        }
        cancellationCheck()

        val frameSize = configuration.frameSizeSamples(wav.format.sampleRateHz)
        val hopSize = configuration.hopSizeSamples(wav.format.sampleRateHz)
        val metrics = AnalysisFrameCalculator.calculate(
            rawPcm = graded.gradedSamples,
            filteredSignal = normalizedAndFiltered,
            frameSizeSamples = frameSize,
            hopSizeSamples = hopSize,
            cancellationCheck = cancellationCheck,
        )
        val hopDurationMillis = hopSize * 1_000.0 / wav.format.sampleRateHz
        val envelope = TransientEnvelopeCalculator.calculate(
            frameLevels = metrics.levels,
            framePeaks = metrics.peaks,
            hopDurationMillis = hopDurationMillis,
            attackMillis = configuration.envelopeAttackMillis,
            releaseMillis = configuration.envelopeReleaseMillis,
        )
        val noiseFloor = AdaptiveNoiseFloorEstimator.estimate(
            envelope = envelope,
            hopDurationMillis = hopDurationMillis,
            riseMillis = configuration.noiseFloorRiseMillis,
            fallMillis = configuration.noiseFloorFallMillis,
        )
        cancellationCheck()

        return AudioAnalysis(
            sampleRateHz = wav.format.sampleRateHz,
            gradedSampleFrameCount = graded.gradedSamples.size.toLong(),
            frameSizeSamples = frameSize,
            hopSizeSamples = hopSize,
            frameCenterExerciseSamples = ImmutableLongSeries.copyOf(metrics.centerSamples),
            representativeRawSamples = ImmutableFloatSeries.copyOf(metrics.representativeRaw),
            representativeFilteredSamples = ImmutableFloatSeries.copyOf(
                metrics.representativeFiltered,
            ),
            framePeaks = ImmutableFloatSeries.copyOf(metrics.peaks),
            frameLevels = ImmutableFloatSeries.copyOf(metrics.levels),
            envelope = ImmutableFloatSeries.copyOf(envelope),
            noiseFloor = ImmutableFloatSeries.copyOf(noiseFloor),
            maximumNormalizedInputAmplitude = maximumInputAmplitude,
            maximumFramePeak = metrics.peaks.maxOrNull() ?: 0.0f,
            maximumEnvelope = envelope.maxOrNull() ?: 0.0f,
            meanNoiseFloor = noiseFloor.average().toFloat(),
            configuration = configuration,
        )
    }
}
