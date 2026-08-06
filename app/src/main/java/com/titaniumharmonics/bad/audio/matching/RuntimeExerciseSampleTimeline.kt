package com.titaniumharmonics.bad.audio.matching

import com.titaniumharmonics.bad.exercise.RuntimeExercise
import com.titaniumharmonics.bad.exercise.RuntimeExpectedNote
import kotlin.math.roundToLong

data class ExpectedNoteTiming(
    val index: Int,
    val note: RuntimeExpectedNote,
    val exerciseSample: Long,
    val exerciseTimeMillis: Double,
)

object RuntimeExerciseSampleTimeline {
    fun expectedNotes(
        exercise: RuntimeExercise,
        sampleRateHz: Int,
    ): List<ExpectedNoteTiming> {
        require(sampleRateHz > 0) { "sampleRateHz must be positive." }
        require(exercise.tempoBpm.isFinite() && exercise.tempoBpm > 0.0)
        require(exercise.ticksPerQuarterNote > 0)

        return exercise.notes
            .mapIndexed { stableIndex, note ->
                stableIndex to note
            }
            .sortedWith(
                compareBy<Pair<Int, RuntimeExpectedNote>> { it.second.positionTicks }
                    .thenBy { it.first },
            )
            .mapIndexed { chronologicalIndex, (_, note) ->
                val sample = ticksToSamples(exercise, note.positionTicks, sampleRateHz)
                ExpectedNoteTiming(
                    index = chronologicalIndex,
                    note = note,
                    exerciseSample = sample,
                    exerciseTimeMillis = samplesToMillis(sample, sampleRateHz),
                )
            }
    }

    fun expectedSamples(exercise: RuntimeExercise, sampleRateHz: Int): LongArray =
        expectedNotes(exercise, sampleRateHz).map(ExpectedNoteTiming::exerciseSample).toLongArray()

    /** Converts ticks to the nearest sample frame, matching detector marker timing. */
    fun ticksToSamples(exercise: RuntimeExercise, ticks: Long, sampleRateHz: Int): Long {
        require(ticks >= 0L) { "ticks must not be negative." }
        require(sampleRateHz > 0) { "sampleRateHz must be positive." }
        val samples = ticks * 60.0 * sampleRateHz /
            (exercise.tempoBpm * exercise.ticksPerQuarterNote)
        require(samples.isFinite() && samples <= Long.MAX_VALUE.toDouble())
        return samples.roundToLong()
    }

    fun samplesToMillis(samples: Long, sampleRateHz: Int): Double {
        require(sampleRateHz > 0) { "sampleRateHz must be positive." }
        return samples * 1_000.0 / sampleRateHz
    }

    /** Converts milliseconds to the nearest sample frame. */
    fun millisecondsToSamples(milliseconds: Double, sampleRateHz: Int): Long {
        require(milliseconds.isFinite() && milliseconds >= 0.0)
        require(sampleRateHz > 0)
        return (milliseconds * sampleRateHz / 1_000.0).roundToLong()
    }
}
