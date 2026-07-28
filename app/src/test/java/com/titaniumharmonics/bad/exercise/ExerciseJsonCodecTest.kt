package com.titaniumharmonics.bad.exercise

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
    }

    @Test
    fun encodeThenDecode_preservesExercise() {
        val original = ExerciseJsonCodec.decode(validExerciseJson)

        val decoded = ExerciseJsonCodec.decode(ExerciseJsonCodec.encode(original))

        assertEquals(original, decoded)
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

    private companion object {
        val validExerciseJson = """
            {
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
