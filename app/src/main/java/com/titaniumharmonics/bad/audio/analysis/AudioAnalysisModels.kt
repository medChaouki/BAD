package com.titaniumharmonics.bad.audio.analysis

import com.titaniumharmonics.bad.audio.PcmAudioFormat
import com.titaniumharmonics.bad.audio.RecordedSession
import com.titaniumharmonics.bad.audio.SampleFrameTiming
import com.titaniumharmonics.bad.audio.metronome.MetronomeConfiguration
import kotlin.math.roundToInt

data class AudioAnalysisConfig(
    val frameDurationMillis: Double = 5.0,
    val hopDurationMillis: Double = 2.0,
    val envelopeAttackMillis: Double = 2.0,
    val envelopeReleaseMillis: Double = 12.0,
    val highPassEnabled: Boolean = true,
    val highPassCutoffHz: Double = 80.0,
    val noiseFloorRiseMillis: Double = 1_000.0,
    val noiseFloorFallMillis: Double = 250.0,
    val sampleCountToleranceFrames: Long = 0L,
) {
    init {
        if (
            frameDurationMillis <= 0.0 || !frameDurationMillis.isFinite() ||
            hopDurationMillis <= 0.0 || !hopDurationMillis.isFinite() ||
            envelopeAttackMillis <= 0.0 || !envelopeAttackMillis.isFinite() ||
            envelopeReleaseMillis <= 0.0 || !envelopeReleaseMillis.isFinite() ||
            highPassCutoffHz <= 0.0 || !highPassCutoffHz.isFinite() ||
            noiseFloorRiseMillis <= 0.0 || !noiseFloorRiseMillis.isFinite() ||
            noiseFloorFallMillis <= 0.0 || !noiseFloorFallMillis.isFinite() ||
            sampleCountToleranceFrames < 0L
        ) {
            throw AudioAnalysisException.InvalidConfiguration(
                "Audio-analysis durations and cutoff must be finite and positive, and " +
                    "sample-count tolerance must not be negative.",
            )
        }
    }

    fun frameSizeSamples(sampleRateHz: Int): Int =
        millisToSamples(frameDurationMillis, sampleRateHz)

    fun hopSizeSamples(sampleRateHz: Int): Int =
        millisToSamples(hopDurationMillis, sampleRateHz)

    private fun millisToSamples(durationMillis: Double, sampleRateHz: Int): Int {
        require(sampleRateHz > 0)
        val samples = durationMillis * sampleRateHz / 1_000.0
        require(samples.isFinite() && samples <= Int.MAX_VALUE)
        return samples.roundToInt().coerceAtLeast(1)
    }
}

sealed class AudioAnalysisException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class MissingWav(path: String) :
        AudioAnalysisException("Recorded WAV file is missing: $path")

    class InvalidWav(message: String) : AudioAnalysisException(message)
    class UnsupportedWav(message: String) : AudioAnalysisException(message)
    class SessionMismatch(message: String) : AudioAnalysisException(message)
    class InvalidConfiguration(message: String) : AudioAnalysisException(message)
    class FileReadFailure(message: String, cause: Throwable) :
        AudioAnalysisException(message, cause)

    class CsvExportFailure(message: String, cause: Throwable) :
        AudioAnalysisException(message, cause)
}

class Pcm16AudioData private constructor(
    val format: PcmAudioFormat,
    private val pcmSamples: ShortArray,
) {
    val sampleFrameCount: Long
        get() = pcmSamples.size.toLong()

    fun sampleAt(sampleFrame: Long): Short {
        require(sampleFrame in 0 until sampleFrameCount)
        return pcmSamples[sampleFrame.toInt()]
    }

    internal fun copyRange(fromSampleFrame: Long, untilSampleFrame: Long): ShortArray {
        require(fromSampleFrame in 0..sampleFrameCount)
        require(untilSampleFrame in fromSampleFrame..sampleFrameCount)
        return pcmSamples.copyOfRange(fromSampleFrame.toInt(), untilSampleFrame.toInt())
    }

    internal companion object {
        fun fromOwnedSamples(format: PcmAudioFormat, samples: ShortArray) =
            Pcm16AudioData(format, samples)
    }
}

class ImmutableFloatSeries private constructor(
    private val values: FloatArray,
) {
    val size: Int
        get() = values.size

    operator fun get(index: Int): Float = values[index]
    fun toList(): List<Float> = values.asList()

    internal companion object {
        fun copyOf(values: FloatArray) = ImmutableFloatSeries(values.copyOf())
    }
}

class ImmutableLongSeries private constructor(
    private val values: LongArray,
) {
    val size: Int
        get() = values.size

    operator fun get(index: Int): Long = values[index]
    fun toList(): List<Long> = values.asList()

    internal companion object {
        fun copyOf(values: LongArray) = ImmutableLongSeries(values.copyOf())
    }
}

data class AudioAnalysisFrame(
    val index: Int,
    val startExerciseSampleFrame: Long,
    val centerExerciseSampleFrame: Long,
    val exerciseTimeMillis: Double,
    val rawSample: Float,
    val filteredSample: Float,
    val preNotchFrameLevel: Float,
    val preNotchEnvelope: Float,
    val postNotchFrameLevel: Float,
    val postNotchEnvelope: Float,
    val framePeak: Float,
    val frameLevel: Float,
    val envelope: Float,
    val noiseFloor: Float,
)

class AudioAnalysis(
    val sampleRateHz: Int,
    val gradedSampleFrameCount: Long,
    val frameSizeSamples: Int,
    val hopSizeSamples: Int,
    val frameCenterExerciseSamples: ImmutableLongSeries,
    val representativeRawSamples: ImmutableFloatSeries,
    val representativeFilteredSamples: ImmutableFloatSeries,
    val preNotchFrameLevels: ImmutableFloatSeries,
    val preNotchEnvelope: ImmutableFloatSeries,
    val framePeaks: ImmutableFloatSeries,
    val frameLevels: ImmutableFloatSeries,
    val envelope: ImmutableFloatSeries,
    val noiseFloor: ImmutableFloatSeries,
    val maximumNormalizedInputAmplitude: Float,
    val maximumFramePeak: Float,
    val maximumEnvelope: Float,
    val meanNoiseFloor: Float,
    val configuration: AudioAnalysisConfig,
    val metronomeConfiguration: MetronomeConfiguration,
    val expectedMetronomeExerciseSamples: ImmutableLongSeries,
    val maximumMetronomeSuppression: Float,
) {
    init {
        require(sampleRateHz > 0)
        require(gradedSampleFrameCount > 0L)
        require(frameSizeSamples > 0 && hopSizeSamples > 0)
        val count = frameCenterExerciseSamples.size
        require(count > 0)
        require(
            listOf(
                representativeRawSamples.size,
                representativeFilteredSamples.size,
                preNotchFrameLevels.size,
                preNotchEnvelope.size,
                framePeaks.size,
                frameLevels.size,
                envelope.size,
                noiseFloor.size,
            ).all { it == count },
        ) { "All frame-level analysis series must have equal length." }
    }

    val frameCount: Int
        get() = frameCenterExerciseSamples.size

    fun frame(index: Int): AudioAnalysisFrame {
        val centerSample = frameCenterExerciseSamples[index]
        return AudioAnalysisFrame(
            index = index,
            startExerciseSampleFrame = index.toLong() * hopSizeSamples,
            centerExerciseSampleFrame = centerSample,
            exerciseTimeMillis = centerSample * 1_000.0 / sampleRateHz,
            rawSample = representativeRawSamples[index],
            filteredSample = representativeFilteredSamples[index],
            preNotchFrameLevel = preNotchFrameLevels[index],
            preNotchEnvelope = preNotchEnvelope[index],
            postNotchFrameLevel = frameLevels[index],
            postNotchEnvelope = envelope[index],
            framePeak = framePeaks[index],
            frameLevel = frameLevels[index],
            envelope = envelope[index],
            noiseFloor = noiseFloor[index],
        )
    }

    fun exerciseSampleForFrame(index: Int): Long = frameCenterExerciseSamples[index]

    fun exerciseDurationNanosForFrame(index: Int): Long =
        SampleFrameTiming.sampleFramesToDurationNanos(
            sampleFrames = exerciseSampleForFrame(index),
            sampleRateHz = sampleRateHz,
        )
}

sealed interface AudioAnalysisState {
    data object NotStarted : AudioAnalysisState
    data object Processing : AudioAnalysisState
    data class Ready(val analysis: AudioAnalysis) : AudioAnalysisState
    data class Failed(val message: String) : AudioAnalysisState
}

sealed interface DebugCsvExportState {
    data object NotStarted : DebugCsvExportState
    data object Exporting : DebugCsvExportState
    data class Exported(val message: String) : DebugCsvExportState
    data class Failed(val message: String) : DebugCsvExportState
}

internal data class ValidatedGradedAudio(
    val session: RecordedSession,
    val gradedSamples: ShortArray,
)
