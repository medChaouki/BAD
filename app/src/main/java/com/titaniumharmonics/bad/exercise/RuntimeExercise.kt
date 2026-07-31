package com.titaniumharmonics.bad.exercise

import java.util.Collections

class RuntimeExercise(
    val id: String,
    val name: String,
    val description: String,
    val tempoBpm: Double,
    val timeSignature: TimeSignature,
    val countInMeasures: Int,
    val ticksPerQuarterNote: Int,
    measures: List<RuntimeMeasure>,
) {
    val measures: List<RuntimeMeasure> = measures.immutableCopy()
    val notes: List<RuntimeExpectedNote> =
        this.measures.flatMap(RuntimeMeasure::notes).immutableCopy()
    val measureCount: Int
        get() = measures.size
    val totalTicks: Long
        get() = measures.lastOrNull()?.let { measure ->
            Math.addExact(measure.startTick, measure.durationTicks)
        } ?: 0L

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is RuntimeExercise &&
            id == other.id &&
            name == other.name &&
            description == other.description &&
            tempoBpm == other.tempoBpm &&
            timeSignature == other.timeSignature &&
            countInMeasures == other.countInMeasures &&
            ticksPerQuarterNote == other.ticksPerQuarterNote &&
            measures == other.measures

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + tempoBpm.hashCode()
        result = 31 * result + timeSignature.hashCode()
        result = 31 * result + countInMeasures
        result = 31 * result + ticksPerQuarterNote
        result = 31 * result + measures.hashCode()
        return result
    }
}

class RuntimeMeasure(
    val index: Int,
    val startTick: Long,
    val durationTicks: Long,
    notes: List<RuntimeExpectedNote>,
) {
    val notes: List<RuntimeExpectedNote> = notes.immutableCopy()

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is RuntimeMeasure &&
            index == other.index &&
            startTick == other.startTick &&
            durationTicks == other.durationTicks &&
            notes == other.notes

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + startTick.hashCode()
        result = 31 * result + durationTicks.hashCode()
        result = 31 * result + notes.hashCode()
        return result
    }
}

data class RuntimeExpectedNote(
    val measureIndex: Int,
    val positionInMeasureTicks: Long,
    val positionTicks: Long,
    val accent: Boolean = false,
    val targetIntensity: Double? = null,
)

private fun <T> List<T>.immutableCopy(): List<T> =
    Collections.unmodifiableList(ArrayList(this))
