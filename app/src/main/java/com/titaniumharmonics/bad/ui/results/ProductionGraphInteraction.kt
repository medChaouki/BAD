package com.titaniumharmonics.bad.ui.results

import com.titaniumharmonics.bad.audio.result.ProductionGraphModel
import kotlin.math.abs
import kotlin.math.roundToLong

data class ProductionGraphViewport(
    val startSample: Long,
    val endSample: Long,
) {
    init {
        require(startSample >= 0L)
        require(endSample > startSample)
    }

    val spanSamples: Long get() = endSample - startSample

    fun zoomIn(totalDurationSamples: Long): ProductionGraphViewport =
        zoom(totalDurationSamples, factor = 0.5)

    fun zoomOut(totalDurationSamples: Long): ProductionGraphViewport =
        zoom(totalDurationSamples, factor = 2.0)

    fun pan(totalDurationSamples: Long, direction: Int): ProductionGraphViewport {
        if (direction == 0) return this
        val duration = totalDurationSamples.coerceAtLeast(1L)
        val shift = (spanSamples * PAN_FRACTION).roundToLong().coerceAtLeast(1L) * direction
        val newStart = (startSample + shift).coerceIn(0L, (duration - spanSamples).coerceAtLeast(0L))
        return ProductionGraphViewport(newStart, (newStart + spanSamples).coerceAtMost(duration))
    }

    fun sampleAtFraction(fraction: Float): Long =
        (startSample + spanSamples * fraction.coerceIn(0.0f, 1.0f)).roundToLong()

    private fun zoom(totalDurationSamples: Long, factor: Double): ProductionGraphViewport {
        val duration = totalDurationSamples.coerceAtLeast(1L)
        val minimumSpan = (duration / MAXIMUM_ZOOM).coerceAtLeast(1L)
        val newSpan = (spanSamples * factor).roundToLong().coerceIn(minimumSpan, duration)
        val center = startSample + spanSamples / 2L
        val newStart = (center - newSpan / 2L).coerceIn(0L, (duration - newSpan).coerceAtLeast(0L))
        return ProductionGraphViewport(newStart, newStart + newSpan)
    }

    companion object {
        fun full(durationSamples: Long): ProductionGraphViewport =
            ProductionGraphViewport(0L, durationSamples.coerceAtLeast(1L))

        private const val MAXIMUM_ZOOM = 32L
        private const val PAN_FRACTION = 0.25
    }
}

sealed interface ProductionGraphSelection {
    data class MatchedNote(val expectedNoteIndex: Int) : ProductionGraphSelection
    data class MissedNote(val expectedNoteIndex: Int) : ProductionGraphSelection
    data class ExtraHit(val detectedHitIndex: Int) : ProductionGraphSelection
}

object ProductionGraphSelectionResolver {
    fun nearest(
        model: ProductionGraphModel,
        tappedSample: Long,
        toleranceSamples: Long,
    ): ProductionGraphSelection? {
        require(toleranceSamples >= 0L)
        val candidates = buildList {
            model.matchedHits.forEach { hit ->
                add(Candidate(hit.calibratedExerciseSample.coerceInGraph(model), 0, hit.expectedNoteIndex, ProductionGraphSelection.MatchedNote(hit.expectedNoteIndex)))
            }
            model.missedNotes.forEach { note ->
                add(Candidate(note.exerciseSample, 1, note.expectedNoteIndex, ProductionGraphSelection.MissedNote(note.expectedNoteIndex)))
            }
            model.extraHits.forEach { hit ->
                add(Candidate(hit.calibratedExerciseSample.coerceInGraph(model), 2, hit.detectedHitIndex, ProductionGraphSelection.ExtraHit(hit.detectedHitIndex)))
            }
            model.expectedNotes.filter { expected ->
                model.matchedHits.any { it.expectedNoteIndex == expected.expectedNoteIndex }
            }.forEach { note ->
                add(Candidate(note.exerciseSample, 3, note.expectedNoteIndex, ProductionGraphSelection.MatchedNote(note.expectedNoteIndex)))
            }
        }
        return candidates
            .map { it to abs(it.sample - tappedSample) }
            .filter { it.second <= toleranceSamples }
            .minWithOrNull(
                compareBy<Pair<Candidate, Long>> { it.second }
                    .thenBy { it.first.priority }
                    .thenBy { it.first.stableIndex },
            )?.first?.selection
    }

    private data class Candidate(
        val sample: Long,
        val priority: Int,
        val stableIndex: Int,
        val selection: ProductionGraphSelection,
    )

    private fun Long.coerceInGraph(model: ProductionGraphModel): Long =
        coerceIn(0L, model.exerciseDurationSamples)
}

internal fun productionGraphContentDescription(model: ProductionGraphModel): String = buildString {
    append("Exercise performance graph. ")
    model.expectedNotes.forEach { expected ->
        append("Expected note ${expected.expectedNoteIndex + 1}, measure ${expected.measureIndex + 1}, ")
        val actual = model.matchedHits.firstOrNull {
            it.expectedNoteIndex == expected.expectedNoteIndex
        }
        val connector = model.timingConnectors.firstOrNull {
            it.expectedNoteIndex == expected.expectedNoteIndex
        }
        if (actual == null) {
            append("missed. ")
        } else {
            append("${actual.judgement.name.lowercase().replace('_', ' ')}, calibrated actual hit, ")
            append("timing error ${connector?.timingErrorMillis ?: 0.0} milliseconds, ")
            append("relative intensity ${(actual.relativeIntensity * 100).toInt()} percent. ")
        }
    }
    model.extraHits.forEach { extra ->
        append("Calibrated extra hit ${extra.detectedHitIndex}, relative intensity ")
        append("${(extra.relativeIntensity * 100).toInt()} percent. ")
    }
}
