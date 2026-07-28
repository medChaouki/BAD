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
)

data class TimeSignature(
    val numerator: Int,
    val denominator: Int,
)

data class ExpectedNote(
    val positionTicks: Long,
    val accent: Boolean = false,
    val targetIntensity: Double? = null,
)
