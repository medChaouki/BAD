package com.titaniumharmonics.bad.audio.result

import com.titaniumharmonics.bad.audio.matching.HitJudgement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionGraphModelBuilderTest {
    @Test
    fun buildsCalibratedProductionTimelineWithoutRawMarkers() {
        val fixture = graphFixture()
        val graph = success(fixture)

        assertEquals(48_000, graph.sampleRateHz)
        assertEquals(96_000L, graph.exerciseDurationSamples)
        assertEquals(2_000.0, graph.exerciseDurationMillis, 0.0)
        assertEquals(listOf(0L, 24_000L, 48_000L, 72_000L), graph.expectedNotes.map { it.exerciseSample })
        assertEquals(listOf(-1_440L, 24_000L, 50_400L), graph.matchedHits.map { it.calibratedExerciseSample })
        assertEquals(60_000L, graph.extraHits.single().calibratedExerciseSample)
        assertFalse(graph.matchedHits.map { it.calibratedExerciseSample }.contains(-1_140L))
        assertFalse(graph.extraHits.map { it.calibratedExerciseSample }.contains(60_300L))
    }

    @Test
    fun mapsJudgementsMissesIntensityAndConnectorDirectionExactly() {
        val graph = success(graphFixture())

        assertEquals(
            listOf(HitJudgement.EARLY, HitJudgement.ON_TIME, HitJudgement.LATE, HitJudgement.MISSED),
            graph.expectedNotes.map { it.judgement },
        )
        assertEquals(listOf(-1_440L, 0L, 2_400L), graph.timingConnectors.map { it.timingErrorSamples })
        assertTrue(graph.timingConnectors[0].calibratedActualSample < graph.timingConnectors[0].expectedExerciseSample)
        assertTrue(graph.timingConnectors[2].calibratedActualSample > graph.timingConnectors[2].expectedExerciseSample)
        assertEquals(3, graph.missedNotes.single().expectedNoteIndex)
        assertEquals(listOf(0.25, 0.75, 1.0), graph.matchedHits.map { it.relativeIntensity })
        assertEquals(0.5, graph.extraHits.single().relativeIntensity, 0.0)
    }

    @Test
    fun envelopeKeepsOriginalSampleTimelineAndMayFollowCalibratedMarker() {
        val graph = success(graphFixture())

        assertEquals(0L, graph.envelopePoints.first().exerciseSample)
        assertEquals(95_999L, graph.envelopePoints.last().exerciseSample)
        assertTrue(graph.envelopePoints.any { it.exerciseSample > graph.matchedHits[2].calibratedExerciseSample })
        assertEquals(1.0f, graph.maximumEnvelopeAmplitude, 0f)
    }

    @Test
    fun inconsistentCalibratedTimingFailsInsteadOfRejudging() {
        val built = ProductionGraphModelBuilder.build(
            graphFixture(inconsistentError = true).result,
            graphFixture().analysis,
        )
        assertTrue(built is ProductionGraphBuildResult.Failure)
    }

    @Test
    fun modelHasNoRawOrDebugLayerProperties() {
        val propertyNames = ProductionGraphModel::class.java.methods.map { it.name.lowercase() }
        listOf("raw", "noise", "threshold", "fft", "wav", "metronome").forEach { forbidden ->
            assertTrue(propertyNames.none { forbidden in it })
        }
    }

    private fun success(fixture: GraphFixture): ProductionGraphModel =
        (ProductionGraphModelBuilder.build(
            fixture.result,
            fixture.analysis,
        ) as ProductionGraphBuildResult.Success).model
}

