package com.titaniumharmonics.bad.exercise

data class Exercise(
    val formatVersion: Int,
    val id: String,
    val name: String,
    val description: String,
    val tempoBpm: Double,
    val timeSignature: TimeSignature,
    val countInMeasures: Int,
    val measureCount: Int,
    val ticksPerQuarterNote: Int,
    val notes: List<ExpectedNote>,
    val measureSubdivisions: List<MeasureSubdivision> =
        defaultMeasureSubdivisions(measureCount),
)

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
