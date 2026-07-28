package com.titaniumharmonics.bad.exercise

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object ExerciseJsonCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
        isLenient = false
        prettyPrint = true
    }

    fun decode(jsonText: String): Exercise =
        json.decodeFromString<ExerciseDto>(jsonText)
            .toDomain()
            .also(::requireValid)

    fun encode(exercise: Exercise): String {
        requireValid(exercise)
        return json.encodeToString(ExerciseDto.fromDomain(exercise))
    }

    private fun requireValid(exercise: Exercise) {
        val validationErrors = ExerciseValidator.validate(exercise)
        if (validationErrors.isNotEmpty()) {
            throw InvalidExerciseException(validationErrors)
        }
    }
}

@Serializable
private data class ExerciseDto(
    val formatVersion: Int,
    val id: String,
    val name: String,
    val description: String = "",
    val tempoBpm: Double,
    val timeSignature: TimeSignatureDto,
    val countInMeasures: Int,
    val measureCount: Int,
    val ticksPerQuarterNote: Int,
    val notes: List<ExpectedNoteDto>,
) {
    fun toDomain(): Exercise = Exercise(
        formatVersion = formatVersion,
        id = id,
        name = name,
        description = description,
        tempoBpm = tempoBpm,
        timeSignature = timeSignature.toDomain(),
        countInMeasures = countInMeasures,
        measureCount = measureCount,
        ticksPerQuarterNote = ticksPerQuarterNote,
        notes = notes.map(ExpectedNoteDto::toDomain),
    )

    companion object {
        fun fromDomain(exercise: Exercise): ExerciseDto = ExerciseDto(
            formatVersion = exercise.formatVersion,
            id = exercise.id,
            name = exercise.name,
            description = exercise.description,
            tempoBpm = exercise.tempoBpm,
            timeSignature = TimeSignatureDto.fromDomain(exercise.timeSignature),
            countInMeasures = exercise.countInMeasures,
            measureCount = exercise.measureCount,
            ticksPerQuarterNote = exercise.ticksPerQuarterNote,
            notes = exercise.notes.map(ExpectedNoteDto::fromDomain),
        )
    }
}

@Serializable
private data class TimeSignatureDto(
    val numerator: Int,
    val denominator: Int,
) {
    fun toDomain(): TimeSignature = TimeSignature(
        numerator = numerator,
        denominator = denominator,
    )

    companion object {
        fun fromDomain(timeSignature: TimeSignature): TimeSignatureDto = TimeSignatureDto(
            numerator = timeSignature.numerator,
            denominator = timeSignature.denominator,
        )
    }
}

@Serializable
private data class ExpectedNoteDto(
    val positionTicks: Long,
    val accent: Boolean = false,
    val targetIntensity: Double? = null,
) {
    fun toDomain(): ExpectedNote = ExpectedNote(
        positionTicks = positionTicks,
        accent = accent,
        targetIntensity = targetIntensity,
    )

    companion object {
        fun fromDomain(note: ExpectedNote): ExpectedNoteDto = ExpectedNoteDto(
            positionTicks = note.positionTicks,
            accent = note.accent,
            targetIntensity = note.targetIntensity,
        )
    }
}
