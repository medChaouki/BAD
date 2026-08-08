package com.titaniumharmonics.bad.ui.results

import com.titaniumharmonics.bad.audio.result.ProductionGraphBuildResult
import com.titaniumharmonics.bad.audio.result.ProductionGraphModelBuilder
import com.titaniumharmonics.bad.audio.result.graphFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionGraphInteractionTest {
    private val graph = (ProductionGraphModelBuilder.build(
        graphFixture().result,
        graphFixture().analysis,
    ) as ProductionGraphBuildResult.Success).model

    @Test
    fun zoomPanAndResetKeepAValidMusicalViewport() {
        val full = ProductionGraphViewport.full(graph.exerciseDurationSamples)
        val zoomed = full.zoomIn(graph.exerciseDurationSamples)
        assertTrue(zoomed.spanSamples < full.spanSamples)
        val right = zoomed.pan(graph.exerciseDurationSamples, 1)
        assertTrue(right.startSample > zoomed.startSample)
        val left = right.pan(graph.exerciseDurationSamples, -1)
        assertTrue(left.startSample < right.startSample)
        assertEquals(full, ProductionGraphViewport.full(graph.exerciseDurationSamples))
    }

    @Test
    fun nearestSelectionSupportsMatchedMissedExtraAndEmptySpace() {
        assertEquals(
            ProductionGraphSelection.MatchedNote(0),
            ProductionGraphSelectionResolver.nearest(graph, 0L, 100L),
        )
        assertEquals(
            ProductionGraphSelection.MatchedNote(1),
            ProductionGraphSelectionResolver.nearest(graph, 24_000L, 100L),
        )
        assertEquals(
            ProductionGraphSelection.MissedNote(3),
            ProductionGraphSelectionResolver.nearest(graph, 72_000L, 100L),
        )
        assertEquals(
            ProductionGraphSelection.ExtraHit(7),
            ProductionGraphSelectionResolver.nearest(graph, 60_000L, 100L),
        )
        assertNull(ProductionGraphSelectionResolver.nearest(graph, 85_000L, 100L))
    }

    @Test
    fun overlappingSelectionIsStableAcrossViewportChanges() {
        val first = ProductionGraphSelectionResolver.nearest(graph, 24_000L, 1L)
        val second = ProductionGraphSelectionResolver.nearest(graph, 24_000L, 1L)
        assertEquals(first, second)
        val viewport = ProductionGraphViewport.full(graph.exerciseDurationSamples).zoomIn(
            graph.exerciseDurationSamples,
        )
        assertEquals(24_000L, viewport.sampleAtFraction(0.0f))
    }

    @Test
    fun accessibilityDescriptionIncludesShapesSemanticsWithoutRawTiming() {
        val description = productionGraphContentDescription(graph)
        assertTrue("expected note" in description.lowercase())
        assertTrue("calibrated actual hit" in description.lowercase())
        assertTrue("missed" in description.lowercase())
        assertTrue("calibrated extra hit" in description.lowercase())
        assertTrue("relative intensity" in description.lowercase())
        assertTrue("raw" !in description.lowercase())
    }
}
