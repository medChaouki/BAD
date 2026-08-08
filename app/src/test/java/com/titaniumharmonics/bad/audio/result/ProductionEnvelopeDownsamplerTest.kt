package com.titaniumharmonics.bad.audio.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionEnvelopeDownsamplerTest {
    @Test
    fun longEnvelopeIsBoundedAndPreservesEndpointsAndPeak() {
        val analysis = graphFixture(frameCount = 5_001).analysis

        val points = ProductionEnvelopeDownsampler.downsample(
            analysis,
            maximumExerciseSample = 96_000L,
            maximumPointCount = 100,
        )

        assertTrue(points.size <= 100)
        assertEquals(0L, points.first().exerciseSample)
        assertEquals(95_999L, points.last().exerciseSample)
        assertEquals(1.0f, points.maxOf { it.amplitude }, 0f)
        assertTrue(points.zipWithNext().all { it.first.exerciseSample < it.second.exerciseSample })
    }

    @Test
    fun emptyOnePointAndFlatInputsAreStableAndDeterministic() {
        val oneFrame = graphFixture(frameCount = 1).analysis
        assertTrue(
            ProductionEnvelopeDownsampler.downsample(
                oneFrame,
                maximumExerciseSample = 0L,
            ).size == 1,
        )
        assertTrue(
            ProductionEnvelopeDownsampler.downsample(
                oneFrame,
                maximumExerciseSample = 0L,
            ).all { it.amplitude.isFinite() },
        )
        val analysis = graphFixture(frameCount = 201).analysis
        val first = ProductionEnvelopeDownsampler.downsample(analysis, 96_000L, 20)
        val second = ProductionEnvelopeDownsampler.downsample(analysis, 96_000L, 20)
        assertEquals(first, second)
    }
}

