package com.titaniumharmonics.bad.history.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExerciseRunMapperTest {
    @Test
    fun entityRoundTripPreservesSummaryAndPayload() {
        val run = exerciseRunFixture()

        val entity = ExerciseRunMapper.toEntity(run)
        val loaded = ExerciseRunMapper.fromEntity(entity)
        val summary = ExerciseRunMapper.toSummary(entity)

        assertEquals(run.runId, loaded.runId)
        assertEquals(run.exerciseId, summary.exerciseId)
        assertEquals(run.exerciseNameSnapshot, summary.exerciseNameSnapshot)
        assertEquals(run.completedAtEpochMillis, summary.completedAtEpochMillis)
        assertEquals(run.bpm, summary.bpm, 0.0)
        assertEquals(run.practiceResult.accuracy, summary.accuracy, 0.0)
        assertEquals(run.practiceResult.hitRate, summary.hitRate, 0.0)
        assertEquals(run.practiceResult.meanAbsoluteTimingErrorMillis,
            summary.meanAbsoluteTimingErrorMillis)
        assertEquals(run.practiceResult.signedMeanTimingErrorMillis,
            summary.signedMeanTimingErrorMillis)
        assertEquals(run.schemaVersion, summary.schemaVersion)
        assertEquals(
            entity.payloadJson,
            ExerciseRunPayloadCodec.encode(loaded),
        )
    }

    @Test
    fun inconsistentQueryableSummaryIsTreatedAsCorruption() {
        val entity = ExerciseRunMapper.toEntity(exerciseRunFixture())

        assertThrows(CorruptedRunPayloadException::class.java) {
            ExerciseRunMapper.fromEntity(entity.copy(accuracy = 0.99))
        }
    }

    @Test
    fun entitySchemaVersionIsCheckedBeforePayloadDecode() {
        val entity = ExerciseRunMapper.toEntity(exerciseRunFixture())

        val error = assertThrows(UnsupportedRunSchemaException::class.java) {
            ExerciseRunMapper.fromEntity(entity.copy(runSchemaVersion = 22))
        }

        assertEquals(22, error.schemaVersion)
    }

    @Test
    fun oversizedPayloadIsRejectedBeforeDecode() {
        val entity = ExerciseRunMapper.toEntity(exerciseRunFixture())
        val oversized = "x".repeat(ExerciseRunPayloadCodec.MAXIMUM_PAYLOAD_BYTES + 1)

        val error = assertThrows(RunPayloadTooLargeException::class.java) {
            ExerciseRunMapper.fromEntity(entity.copy(payloadJson = oversized))
        }

        assertEquals(oversized.length, error.sizeBytes)
    }
}
