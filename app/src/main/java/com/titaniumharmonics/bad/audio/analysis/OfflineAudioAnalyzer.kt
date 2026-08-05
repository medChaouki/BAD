package com.titaniumharmonics.bad.audio.analysis

import com.titaniumharmonics.bad.audio.RecordedSession
import java.io.File
import kotlin.math.abs
import com.titaniumharmonics.bad.audio.SampleFrameTiming
import com.titaniumharmonics.bad.timing.ExerciseTiming

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
        val metronomeConfiguration = session.metronomeSnapshot.configuration
        val configurationErrors = metronomeConfiguration.validationErrors(wav.format.sampleRateHz)
        if (configurationErrors.isNotEmpty()) {
            throw AudioAnalysisException.InvalidConfiguration(
                configurationErrors.joinToString(" "),
            )
        }

        val preNotchSignal = Pcm16Normalizer.normalize(graded.gradedSamples)
        val maximumInputAmplitude = preNotchSignal.maxOf { abs(it) }
        MeanDcOffsetRemover.removeInPlace(preNotchSignal)
        if (configuration.highPassEnabled) {
            FirstOrderHighPassFilter.filterInPlace(
                signal = preNotchSignal,
                sampleRateHz = wav.format.sampleRateHz,
                cutoffHz = configuration.highPassCutoffHz,
            )
        }
        val postNotchSignal = BiquadNotchFilter.filter(
            signal = preNotchSignal,
            sampleRateHz = wav.format.sampleRateHz,
            centerFrequencyHz = metronomeConfiguration.notch.centerFrequencyHz.toDouble(),
            qFactor = metronomeConfiguration.notch.qFactor,
            enabled = metronomeConfiguration.notch.enabled,
        )
        cancellationCheck()

        val frameSize = configuration.frameSizeSamples(wav.format.sampleRateHz)
        val hopSize = configuration.hopSizeSamples(wav.format.sampleRateHz)
        val preNotchMetrics = AnalysisFrameCalculator.calculate(
            rawPcm = graded.gradedSamples,
            filteredSignal = preNotchSignal,
            frameSizeSamples = frameSize,
            hopSizeSamples = hopSize,
            cancellationCheck = cancellationCheck,
        )
        val postNotchMetrics = AnalysisFrameCalculator.calculate(
            rawPcm = graded.gradedSamples,
            filteredSignal = postNotchSignal,
            frameSizeSamples = frameSize,
            hopSizeSamples = hopSize,
            cancellationCheck = cancellationCheck,
        )
        val hopDurationMillis = hopSize * 1_000.0 / wav.format.sampleRateHz
        val preNotchEnvelope = TransientEnvelopeCalculator.calculate(
            frameLevels = preNotchMetrics.levels,
            framePeaks = preNotchMetrics.peaks,
            hopDurationMillis = hopDurationMillis,
            attackMillis = configuration.envelopeAttackMillis,
            releaseMillis = configuration.envelopeReleaseMillis,
        )
        val postNotchEnvelope = TransientEnvelopeCalculator.calculate(
            frameLevels = postNotchMetrics.levels,
            framePeaks = postNotchMetrics.peaks,
            hopDurationMillis = hopDurationMillis,
            attackMillis = configuration.envelopeAttackMillis,
            releaseMillis = configuration.envelopeReleaseMillis,
        )
        val noiseFloor = AdaptiveNoiseFloorEstimator.estimate(
            envelope = postNotchEnvelope,
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
            frameCenterExerciseSamples = ImmutableLongSeries.copyOf(postNotchMetrics.centerSamples),
            representativeRawSamples = ImmutableFloatSeries.copyOf(postNotchMetrics.representativeRaw),
            representativeFilteredSamples = ImmutableFloatSeries.copyOf(
                postNotchMetrics.representativeFiltered,
            ),
            preNotchFrameLevels = ImmutableFloatSeries.copyOf(preNotchMetrics.levels),
            preNotchEnvelope = ImmutableFloatSeries.copyOf(preNotchEnvelope),
            framePeaks = ImmutableFloatSeries.copyOf(postNotchMetrics.peaks),
            frameLevels = ImmutableFloatSeries.copyOf(postNotchMetrics.levels),
            envelope = ImmutableFloatSeries.copyOf(postNotchEnvelope),
            noiseFloor = ImmutableFloatSeries.copyOf(noiseFloor),
            maximumNormalizedInputAmplitude = maximumInputAmplitude,
            maximumFramePeak = postNotchMetrics.peaks.maxOrNull() ?: 0.0f,
            maximumEnvelope = postNotchEnvelope.maxOrNull() ?: 0.0f,
            meanNoiseFloor = noiseFloor.average().toFloat(),
            configuration = configuration,
            metronomeConfiguration = metronomeConfiguration,
            expectedMetronomeExerciseSamples = ImmutableLongSeries.copyOf(
                expectedMetronomeExerciseSamples(session),
            ),
            maximumMetronomeSuppression = preNotchEnvelope.indices.maxOf { index ->
                (preNotchEnvelope[index] - postNotchEnvelope[index]).coerceAtLeast(0.0f)
            },
            postNotchPcm = ImmutableFloatSeries.fromOwned(postNotchSignal),
        )
    }

    private fun expectedMetronomeExerciseSamples(session: RecordedSession): LongArray {
        val exercise = session.runtimeExercise
        val timing = ExerciseTiming(exercise)
        return exercise.notes.asSequence()
            .filter { note ->
                !session.metronomeSnapshot.downbeatsOnly ||
                    note.positionTicks % timing.measureDurationTicks == 0L
            }
            .map { note ->
                SampleFrameTiming.durationNanosToSampleFrames(
                    durationNanos = timing.ticksToNanos(note.positionTicks),
                    sampleRateHz = session.audioFormat.sampleRateHz,
                )
            }
            .toList()
            .toLongArray()
    }
}
