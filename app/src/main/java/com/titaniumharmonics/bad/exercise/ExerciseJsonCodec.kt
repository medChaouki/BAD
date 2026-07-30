package com.titaniumharmonics.bad.exercise

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

object ExerciseJsonCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
        isLenient = false
        prettyPrint = true
    }

    fun decode(jsonText: String): EditableExercise =
        json.decodeFromString<ExerciseDto>(jsonText)
            .toDomain()
            .also(::requireValid)

    fun encode(exercise: EditableExercise): String {
        requireValid(exercise)
        return json.encodeToString(ExerciseDto.fromDomain(exercise))
    }

    private fun requireValid(exercise: EditableExercise) {
        val validationErrors = ExerciseValidator.validate(exercise)
        if (validationErrors.isNotEmpty()) {
            throw InvalidExerciseException(validationErrors)
        }
    }
}

@Serializable
private data class ExerciseDto(
    val fileType: String? = null,
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
    val measureSubdivisions: List<MeasureSubdivisionDto>? = null,
) {
    fun toDomain(): EditableExercise {
        if (fileType != ExerciseFormat.FILE_TYPE) {
            throw InvalidExerciseFileException(
                "Not a B.A.D. exercise file: fileType must be " +
                    "\"${ExerciseFormat.FILE_TYPE}\".",
            )
        }
        return EditableExercise(
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
            measureSubdivisions = measureSubdivisions
                ?.map(MeasureSubdivisionDto::toDomain)
                ?: List(measureCount.coerceAtLeast(0)) {
                    MeasureSubdivision.QUARTER
                },
        )
    }

    companion object {
        fun fromDomain(exercise: EditableExercise): ExerciseDto = ExerciseDto(
            fileType = ExerciseFormat.FILE_TYPE,
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
            measureSubdivisions = exercise.measureSubdivisions.map(
                MeasureSubdivisionDto::fromDomain,
            ),
        )
    }
}

@Serializable
private enum class MeasureSubdivisionDto {
    @SerialName("quarter")
    QUARTER,

    @SerialName("eighth")
    EIGHTH,

    @SerialName("eighth_triplet")
    EIGHTH_TRIPLET,

    @SerialName("sixteenth")
    SIXTEENTH,
    ;

    fun toDomain(): MeasureSubdivision = when (this) {
        QUARTER -> MeasureSubdivision.QUARTER
        EIGHTH -> MeasureSubdivision.EIGHTH
        EIGHTH_TRIPLET -> MeasureSubdivision.EIGHTH_TRIPLET
        SIXTEENTH -> MeasureSubdivision.SIXTEENTH
    }

    companion object {
        fun fromDomain(subdivision: MeasureSubdivision): MeasureSubdivisionDto =
            when (subdivision) {
                MeasureSubdivision.QUARTER -> QUARTER
                MeasureSubdivision.EIGHTH -> EIGHTH
                MeasureSubdivision.EIGHTH_TRIPLET -> EIGHTH_TRIPLET
                MeasureSubdivision.SIXTEENTH -> SIXTEENTH
            }
    }
}

class InvalidExerciseFileException(
    message: String,
) : IllegalArgumentException(message)

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
