package com.titaniumharmonics.bad.audio.result

import com.titaniumharmonics.bad.audio.analysis.AudioAnalysis
import kotlin.math.ceil

/** Peak-preserving min/max bucket sampling with stable first and last points. */
object ProductionEnvelopeDownsampler {
    fun downsample(
        analysis: AudioAnalysis,
        maximumExerciseSample: Long,
        maximumPointCount: Int = ProductionGraphModel.MAXIMUM_ENVELOPE_POINT_COUNT,
    ): List<ProductionEnvelopePoint> {
        require(maximumExerciseSample >= 0L)
        require(maximumPointCount >= 2)
        val eligible = (0 until analysis.frameCount).filter {
            analysis.frameCenterExerciseSamples[it] <= maximumExerciseSample
        }
        if (eligible.isEmpty()) return emptyList()
        val selected = if (eligible.size <= maximumPointCount) {
            eligible
        } else {
            selectExtrema(analysis, eligible, maximumPointCount)
        }
        return selected.map { index ->
            val sample = analysis.frameCenterExerciseSamples[index]
            ProductionEnvelopePoint(
                exerciseSample = sample,
                exerciseTimeMillis = sample * 1_000.0 / analysis.sampleRateHz,
                amplitude = analysis.envelope[index].finiteNonNegative(),
            )
        }
    }

    private fun selectExtrema(
        analysis: AudioAnalysis,
        eligible: List<Int>,
        maximumPointCount: Int,
    ): List<Int> {
        val selected = linkedSetOf(eligible.first())
        val interior = eligible.subList(1, eligible.lastIndex)
        val extremaBudget = maximumPointCount - 2
        val bucketCount = (extremaBudget / 2).coerceAtLeast(1)
        val bucketSize = ceil(interior.size.toDouble() / bucketCount).toInt().coerceAtLeast(1)
        var start = 0
        while (start < interior.size && selected.size < maximumPointCount - 1) {
            val end = minOf(interior.size, start + bucketSize)
            var minimum = interior[start]
            var maximum = interior[start]
            for (position in start + 1 until end) {
                val index = interior[position]
                if (analysis.envelope[index] < analysis.envelope[minimum]) minimum = index
                if (analysis.envelope[index] > analysis.envelope[maximum]) maximum = index
            }
            listOf(minimum, maximum).sorted().forEach { index ->
                if (selected.size < maximumPointCount - 1) selected += index
            }
            start = end
        }
        selected += eligible.last()
        return selected.sorted()
    }

    private fun Float.finiteNonNegative(): Float =
        if (isFinite()) coerceAtLeast(0.0f) else 0.0f
}

