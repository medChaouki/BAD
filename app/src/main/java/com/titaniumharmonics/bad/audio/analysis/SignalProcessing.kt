package com.titaniumharmonics.bad.audio.analysis

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

object Pcm16Normalizer {
    fun normalize(sample: Short): Float = when {
        sample >= 0 -> sample.toFloat() / Short.MAX_VALUE
        else -> sample.toFloat() / -Short.MIN_VALUE.toFloat()
    }.coerceIn(-1.0f, 1.0f)

    fun normalize(samples: ShortArray): FloatArray = FloatArray(samples.size) {
        normalize(samples[it])
    }
}

object MeanDcOffsetRemover {
    fun removeInPlace(signal: FloatArray): Double {
        if (signal.isEmpty()) return 0.0
        var sum = 0.0
        signal.forEach { value ->
            require(value.isFinite()) { "DC-removal input must be finite." }
            sum += value
        }
        val mean = sum / signal.size
        signal.indices.forEach { index ->
            signal[index] = (signal[index] - mean.toFloat()).coerceIn(-2.0f, 2.0f)
        }
        return mean
    }

    fun remove(signal: FloatArray): FloatArray = signal.copyOf().also(::removeInPlace)
}

object FirstOrderHighPassFilter {
    fun filterInPlace(signal: FloatArray, sampleRateHz: Int, cutoffHz: Double) {
        require(sampleRateHz > 0)
        require(cutoffHz > 0.0 && cutoffHz.isFinite())
        if (signal.isEmpty()) return
        val dt = 1.0 / sampleRateHz
        val rc = 1.0 / (2.0 * PI * cutoffHz)
        val alpha = rc / (rc + dt)
        var previousInput = 0.0
        var previousOutput = 0.0
        for (index in signal.indices) {
            val input = signal[index].toDouble()
            val output = alpha * (previousOutput + input - previousInput)
            require(output.isFinite()) { "High-pass filter became numerically unstable." }
            signal[index] = output.coerceIn(-2.0, 2.0).toFloat()
            previousInput = input
            previousOutput = output
        }
    }

    fun filter(
        signal: FloatArray,
        sampleRateHz: Int,
        cutoffHz: Double,
        enabled: Boolean,
    ): FloatArray = signal.copyOf().also { output ->
        if (enabled) filterInPlace(output, sampleRateHz, cutoffHz)
    }
}

data class FrameMetrics(
    val centerSamples: LongArray,
    val representativeRaw: FloatArray,
    val representativeFiltered: FloatArray,
    val peaks: FloatArray,
    val levels: FloatArray,
)

object AnalysisFrameCalculator {
    fun calculate(
        rawPcm: ShortArray,
        filteredSignal: FloatArray,
        frameSizeSamples: Int,
        hopSizeSamples: Int,
        cancellationCheck: () -> Unit = {},
    ): FrameMetrics {
        require(rawPcm.size == filteredSignal.size)
        require(rawPcm.isNotEmpty())
        require(frameSizeSamples > 0 && hopSizeSamples > 0)
        val frameCount = ((rawPcm.size - 1) / hopSizeSamples) + 1
        val centers = LongArray(frameCount)
        val raw = FloatArray(frameCount)
        val filtered = FloatArray(frameCount)
        val peaks = FloatArray(frameCount)
        val levels = FloatArray(frameCount)

        repeat(frameCount) { frameIndex ->
            if (frameIndex % CANCELLATION_CHECK_INTERVAL == 0) cancellationCheck()
            val start = frameIndex * hopSizeSamples
            val endExclusive = minOf(rawPcm.size, start + frameSizeSamples)
            val actualFrameSize = endExclusive - start
            val center = start + (actualFrameSize - 1) / 2
            centers[frameIndex] = center.toLong()
            raw[frameIndex] = Pcm16Normalizer.normalize(rawPcm[center])
            filtered[frameIndex] = filteredSignal[center]

            var peak = 0.0f
            var sumSquares = 0.0
            for (sampleIndex in start until endExclusive) {
                val value = filteredSignal[sampleIndex]
                require(value.isFinite()) { "Frame input must be finite." }
                peak = max(peak, abs(value))
                sumSquares += value * value
            }
            peaks[frameIndex] = peak
            levels[frameIndex] = sqrt(sumSquares / actualFrameSize).toFloat()
        }
        return FrameMetrics(centers, raw, filtered, peaks, levels)
    }

    private const val CANCELLATION_CHECK_INTERVAL = 256
}

object TransientEnvelopeCalculator {
    fun calculate(
        frameLevels: FloatArray,
        framePeaks: FloatArray,
        hopDurationMillis: Double,
        attackMillis: Double,
        releaseMillis: Double,
    ): FloatArray {
        require(frameLevels.size == framePeaks.size)
        require(hopDurationMillis > 0.0 && attackMillis > 0.0 && releaseMillis > 0.0)
        val attackAlpha = smoothingAlpha(hopDurationMillis, attackMillis)
        val releaseAlpha = smoothingAlpha(hopDurationMillis, releaseMillis)
        val envelope = FloatArray(frameLevels.size)
        var previous = 0.0
        frameLevels.indices.forEach { index ->
            val input = max(
                frameLevels[index].toDouble(),
                framePeaks[index].toDouble() * PEAK_CONTRIBUTION,
            )
            val alpha = if (input > previous) attackAlpha else releaseAlpha
            previous += alpha * (input - previous)
            require(previous.isFinite()) { "Envelope became numerically unstable." }
            envelope[index] = previous.coerceAtLeast(0.0).toFloat()
        }
        return envelope
    }

    private fun smoothingAlpha(stepMillis: Double, timeConstantMillis: Double): Double =
        1.0 - exp(-stepMillis / timeConstantMillis)

    private const val PEAK_CONTRIBUTION = 0.5
}

object AdaptiveNoiseFloorEstimator {
    fun estimate(
        envelope: FloatArray,
        hopDurationMillis: Double,
        riseMillis: Double,
        fallMillis: Double,
    ): FloatArray {
        require(hopDurationMillis > 0.0 && riseMillis > 0.0 && fallMillis > 0.0)
        val riseAlpha = 1.0 - exp(-hopDurationMillis / riseMillis)
        val fallAlpha = 1.0 - exp(-hopDurationMillis / fallMillis)
        var baseline = 0.0
        return FloatArray(envelope.size) { index ->
            val input = envelope[index].toDouble()
            require(input.isFinite() && input >= 0.0)
            val alpha = if (input > baseline) riseAlpha else fallAlpha
            baseline += alpha * (input - baseline)
            require(baseline.isFinite()) { "Noise floor became numerically unstable." }
            baseline.coerceAtLeast(0.0).toFloat()
        }
    }
}
