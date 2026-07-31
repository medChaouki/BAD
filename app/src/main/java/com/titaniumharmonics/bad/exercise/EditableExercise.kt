package com.titaniumharmonics.bad.exercise

data class EditableExercise(
    val formatVersion: Int,
    val id: String,
    val name: String,
    val description: String,
    val tempoBpm: Double,
    val timeSignature: TimeSignature,
    val measureCount: Int,
    val ticksPerQuarterNote: Int,
    val notes: List<ExpectedNote>,
    val measureSubdivisions: List<MeasureSubdivision> =
        defaultMeasureSubdivisions(measureCount),
    val measureMultipliers: List<Int> =
        defaultMeasureMultipliers(measureCount),
) {
    val expandedMeasureCount: Int
        get() {
            var expandedCount = 0
            measureMultipliers.forEach { multiplier ->
                expandedCount = Math.addExact(expandedCount, multiplier)
            }
            return expandedCount
        }
}

object MeasurePatternConstraints {
    const val DEFAULT_MULTIPLIER = 1
    const val MIN_MULTIPLIER = 1
    const val MAX_MULTIPLIER = 99
}

enum class MeasureSubdivision {
    QUARTER,
    EIGHTH,
    EIGHTH_TRIPLET,
    SIXTEENTH,
}

data class TimeSignature(
    val numerator: Int,
    val denominator: Int,
)

data class ExpectedNote(
    val positionTicks: Long,
    val accent: Boolean = false,
    val targetIntensity: Double? = null,
)

private fun defaultMeasureSubdivisions(
    measureCount: Int,
): List<MeasureSubdivision> = List(measureCount.coerceAtLeast(0)) {
    MeasureSubdivision.QUARTER
}

private fun defaultMeasureMultipliers(
    measureCount: Int,
): List<Int> = List(measureCount.coerceAtLeast(0)) {
    MeasurePatternConstraints.DEFAULT_MULTIPLIER
}
