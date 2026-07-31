package com.titaniumharmonics.bad.exercise

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseJsonCodecTest {
    @Test
    fun decode_readsVersionOneExercise() {
        val exercise = ExerciseJsonCodec.decode(validExerciseJson)

        assertEquals(ExerciseFormat.CURRENT_VERSION, exercise.formatVersion)
        assertEquals("basic-quarter-notes", exercise.id)
        assertEquals(100.0, exercise.tempoBpm, 0.0)
        assertEquals(TimeSignature(4, 4), exercise.timeSignature)
        assertEquals(480, exercise.ticksPerQuarterNote)
        assertEquals(listOf(0L, 480L, 960L, 1440L), exercise.notes.map { it.positionTicks })
        assertTrue(exercise.notes.first().accent)
        assertEquals(
            listOf(MeasureSubdivision.QUARTER),
            exercise.measureSubdivisions,
        )
        assertEquals(listOf(1), exercise.measureMultipliers)
    }

    @Test
    fun decode_readsPersistedMeasureMultipliers() {
        val exercise = ExerciseJsonCodec.decode(
            validExerciseJson.replace(
                "\"ticksPerQuarterNote\": 480,",
                """
                    "ticksPerQuarterNote": 480,
                    "measureMultipliers": [4],
                """.trimIndent(),
            ),
        )

        assertEquals(listOf(4), exercise.measureMultipliers)
    }

    @Test
    fun decode_readsPersistedMeasureSubdivisions() {
        val exercise = ExerciseJsonCodec.decode(
            validExerciseJson.replace(
                "\"ticksPerQuarterNote\": 480,",
                """
                    "ticksPerQuarterNote": 480,
                    "measureSubdivisions": ["eighth_triplet"],
                """.trimIndent(),
            ),
        )

        assertEquals(
            listOf(MeasureSubdivision.EIGHTH_TRIPLET),
            exercise.measureSubdivisions,
        )
    }

    @Test
    fun decode_rejectsFileWithoutBadExerciseMarker() {
        val exception = assertThrows(InvalidExerciseFileException::class.java) {
            ExerciseJsonCodec.decode(
                validExerciseJson.replace("\"fileType\": \"bad-exercise\",\n", ""),
            )
        }

        assertTrue(exception.message.orEmpty().contains("fileType"))
    }

    @Test
    fun decode_rejectsIncorrectFileType() {
        val exception = assertThrows(InvalidExerciseFileException::class.java) {
            ExerciseJsonCodec.decode(
                validExerciseJson.replace("bad-exercise", "some-other-file"),
            )
        }

        assertTrue(exception.message.orEmpty().contains("Not a B.A.D. exercise file"))
    }

    @Test
    fun encodeThenDecode_preservesExercise() {
        val original = ExerciseJsonCodec.decode(validExerciseJson).copy(
            measureMultipliers = listOf(4),
        )

        val encoded = ExerciseJsonCodec.encode(original)
        val decoded = ExerciseJsonCodec.decode(encoded)

        assertTrue(encoded.contains("\"measureMultipliers\": ["))
        assertEquals(original, decoded)
    }

    @Test
    fun decode_acceptsExerciseWithEmptyMeasures() {
        val exercise = ExerciseJsonCodec.decode(
            validExerciseJson.replace(
                Regex("(?s)\"notes\": \\[.*]"),
                "\"notes\": []",
            ),
        )

        assertTrue(exercise.notes.isEmpty())
        assertEquals(1, exercise.measureCount)
    }

    @Test
    fun decode_rejectsUnsupportedFormatVersion() {
        val exception = assertThrows(InvalidExerciseException::class.java) {
            ExerciseJsonCodec.decode(validExerciseJson.replace("\"formatVersion\": 1", "\"formatVersion\": 2"))
        }

        assertTrue(exception.validationErrors.single().contains("Unsupported formatVersion"))
    }

    @Test
    fun decode_rejectsNoteAtExerciseEnd() {
        val exception = assertThrows(InvalidExerciseException::class.java) {
            ExerciseJsonCodec.decode(validExerciseJson.replace("\"positionTicks\": 1440", "\"positionTicks\": 1920"))
        }

        assertTrue(exception.validationErrors.any { it.contains("before exercise duration") })
    }

    @Test
    fun decode_rejectsUnorderedNotes() {
        val exception = assertThrows(InvalidExerciseException::class.java) {
            ExerciseJsonCodec.decode(validExerciseJson.replace("\"positionTicks\": 960", "\"positionTicks\": 240"))
        }

        assertTrue(exception.validationErrors.any { it.contains("ordered by positionTicks") })
    }

    @Test
    fun decode_rejectsTimeSignatureThatPpqnCannotRepresent() {
        val exception = assertThrows(InvalidExerciseException::class.java) {
            ExerciseJsonCodec.decode(validExerciseJson.replace("\"denominator\": 4", "\"denominator\": 2048"))
        }

        assertTrue(exception.validationErrors.any { it.contains("cannot represent") })
    }

    @Test
    fun encodeRejectsSubdivisionCountThatDoesNotMatchMeasures() {
        val exercise = ExerciseJsonCodec.decode(validExerciseJson).copy(
            measureSubdivisions = emptyList(),
        )

        val exception = assertThrows(InvalidExerciseException::class.java) {
            ExerciseJsonCodec.encode(exercise)
        }

        assertTrue(
            exception.validationErrors.any { it.contains("one entry per measure") },
        )
    }

    @Test
    fun decodeRejectsSubdivisionCountThatDoesNotMatchMeasures() {
        val exception = assertThrows(InvalidExerciseException::class.java) {
            ExerciseJsonCodec.decode(
                validExerciseJson.replace(
                    "\"ticksPerQuarterNote\": 480,",
                    """
                        "ticksPerQuarterNote": 480,
                        "measureSubdivisions": ["quarter", "eighth"],
                    """.trimIndent(),
                ),
            )
        }

        assertTrue(
            exception.validationErrors.any { it.contains("one entry per measure") },
        )
    }

    @Test
    fun decodeRejectsUnknownSubdivisionName() {
        assertThrows(SerializationException::class.java) {
            ExerciseJsonCodec.decode(
                validExerciseJson.replace(
                    "\"ticksPerQuarterNote\": 480,",
                    """
                        "ticksPerQuarterNote": 480,
                        "measureSubdivisions": ["thirty_second"],
                    """.trimIndent(),
                ),
            )
        }
    }

    @Test
    fun decodeRejectsNonPositiveMeasureMultiplier() {
        val exception = assertThrows(InvalidExerciseException::class.java) {
            ExerciseJsonCodec.decode(
                validExerciseJson.replace(
                    "\"ticksPerQuarterNote\": 480,",
                    """
                        "ticksPerQuarterNote": 480,
                        "measureMultipliers": [0],
                    """.trimIndent(),
                ),
            )
        }

        assertTrue(
            exception.validationErrors.any { it.contains("measureMultipliers[0]") },
        )
    }

    @Test
    fun encodeRejectsMultiplierCountThatDoesNotMatchPatterns() {
        val exercise = ExerciseJsonCodec.decode(validExerciseJson).copy(
            measureMultipliers = emptyList(),
        )

        val exception = assertThrows(InvalidExerciseException::class.java) {
            ExerciseJsonCodec.encode(exercise)
        }

        assertTrue(
            exception.validationErrors.any { it.contains("one entry per measure pattern") },
        )
    }

    private companion object {
        val validExerciseJson = """
            {
                "fileType": "bad-exercise",
                "formatVersion": 1,
                "id": "basic-quarter-notes",
                "name": "Quarter Note Inspection",
                "description": "One measure of quarter notes.",
                "tempoBpm": 100.0,
                "timeSignature": {
                    "numerator": 4,
                    "denominator": 4
                },
                "countInMeasures": 1,
                "measureCount": 1,
                "ticksPerQuarterNote": 480,
                "notes": [
                    { "positionTicks": 0, "accent": true },
                    { "positionTicks": 480 },
                    { "positionTicks": 960 },
                    { "positionTicks": 1440 }
                ]
            }
        """.trimIndent()
    }
}
