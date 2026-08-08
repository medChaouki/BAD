package com.titaniumharmonics.bad.history.persistence

import com.titaniumharmonics.bad.audio.matching.HitJudgement
import com.titaniumharmonics.bad.history.ExerciseRun
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseRunPayloadCodecTest {
    @Test
    fun completeRunRoundTripPreservesEveryCanonicalFieldDeterministically() {
        val original = exerciseRunFixture()

        val encoded = ExerciseRunPayloadCodec.encode(original)
        val loaded = ExerciseRunPayloadCodec.decode(encoded)

        assertEquals(encoded, ExerciseRunPayloadCodec.encode(loaded))
        assertEquals(original.runId, loaded.runId)
        assertEquals(original.runtimeExerciseSnapshot, loaded.runtimeExerciseSnapshot)
        assertEquals(original.practiceResult.judgedNotes, loaded.practiceResult.judgedNotes)
        assertEquals(original.practiceResult.extraHits, loaded.practiceResult.extraHits)
        assertEquals(original.practiceResult.accuracy, loaded.practiceResult.accuracy, 0.0)
        assertEquals(original.practiceResult.hitRate, loaded.practiceResult.hitRate, 0.0)
        assertEquals(
            original.practiceResult.meanAbsoluteTimingErrorMillis,
            loaded.practiceResult.meanAbsoluteTimingErrorMillis,
        )
        assertEquals(
            original.practiceResult.signedMeanTimingErrorMillis,
            loaded.practiceResult.signedMeanTimingErrorMillis,
        )
        assertEquals(original.detectionSnapshot, loaded.detectionSnapshot)
        assertEquals(original.metronomeSnapshot, loaded.metronomeSnapshot)
        assertEquals(original.judgementSnapshot, loaded.judgementSnapshot)
        assertEquals(original.calibrationMetadata, loaded.calibrationMetadata)
        assertEquals(original.schemaVersion, loaded.schemaVersion)
        assertGraphEquals(original, loaded)
    }

    @Test
    fun nullableTimingStatisticsAndAllMissedRunRoundTrip() {
        val nullable = nullableStatisticsRunFixture()
        val missed = allMissedRunFixture()

        val loadedNullable = ExerciseRunPayloadCodec.decode(
            ExerciseRunPayloadCodec.encode(nullable),
        )
        val loadedMissed = ExerciseRunPayloadCodec.decode(
            ExerciseRunPayloadCodec.encode(missed),
        )

        assertEquals(null, loadedNullable.practiceResult.meanAbsoluteTimingErrorMillis)
        assertEquals(null, loadedNullable.practiceResult.signedMeanTimingErrorMillis)
        assertEquals(missed.totalExpectedNotes, loadedMissed.practiceResult.missedCount)
        assertTrue(loadedMissed.productionGraph.envelopePoints.isEmpty())
        assertTrue(loadedMissed.productionGraph.matchedHits.isEmpty())
        assertEquals(missed.totalExpectedNotes, loadedMissed.productionGraph.missedNotes.size)
    }

    @Test
    fun zeroExpectedAllExtraRunRoundTripsWithoutWavOrExpectedMarkers() {
        val original = zeroExpectedAllExtraRunFixture()

        val loaded = ExerciseRunPayloadCodec.decode(ExerciseRunPayloadCodec.encode(original))

        assertEquals(0, loaded.totalExpectedNotes)
        assertEquals(1, loaded.practiceResult.extraCount)
        assertEquals(1.0, loaded.practiceResult.extraHitRate, 0.0)
        assertTrue(loaded.productionGraph.expectedNotes.isEmpty())
        assertEquals(1, loaded.productionGraph.extraHits.size)
    }

    @Test
    fun productionGraphPayloadContainsOnlyBoundedProductionData() {
        val payload = Json.parseToJsonElement(
            ExerciseRunPayloadCodec.encode(exerciseRunFixture()),
        ).jsonObject.getValue("productionGraph").toString()

        assertTrue("envelopePoints" in payload)
        assertTrue("timingConnectors" in payload)
        assertTrue("missedNotes" in payload)
        assertTrue("extraHits" in payload)
        assertFalse("wav" in payload.lowercase())
        assertFalse("rawExercise" in payload)
        assertFalse("preNotch" in payload)
        assertFalse("noiseFloor" in payload)
        assertFalse("adaptiveThreshold" in payload)
        assertFalse("rejectedMetronome" in payload)
        assertFalse("fft" in payload.lowercase())
    }

    @Test
    fun unsupportedFutureSchemaFailsWithoutReinterpretation() {
        val payload = ExerciseRunPayloadCodec.encode(exerciseRunFixture())
            .replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":99")

        val error = assertThrows(UnsupportedRunSchemaException::class.java) {
            ExerciseRunPayloadCodec.decode(payload)
        }

        assertEquals(99, error.schemaVersion)
    }

    @Test
    fun corruptedPayloadAndUnknownEnumFailSafely() {
        assertThrows(CorruptedRunPayloadException::class.java) {
            ExerciseRunPayloadCodec.decode("{not-json")
        }
        val unknownEnum = ExerciseRunPayloadCodec.encode(exerciseRunFixture())
            .replaceFirst("\"judgement\":\"EARLY\"", "\"judgement\":\"FUTURE\"")

        assertThrows(CorruptedRunPayloadException::class.java) {
            ExerciseRunPayloadCodec.decode(unknownEnum)
        }
    }

    @Test
    fun invalidGraphPayloadFailsAsGraphError() {
        val payload = ExerciseRunPayloadCodec.encode(exerciseRunFixture())
            .replaceFirst("\"maximumEnvelopeAmplitude\":1.0", "\"maximumEnvelopeAmplitude\":-1.0")

        assertThrows(InvalidProductionGraphPayloadException::class.java) {
            ExerciseRunPayloadCodec.decode(payload)
        }
    }

    @Test
    fun largeEnvelopeRunRemainsWithinDocumentedPointAndStorageBounds() {
        val run = exerciseRunFixture(frameCount = 5_000)
        val payload = ExerciseRunPayloadCodec.encode(run)

        assertTrue(run.productionGraph.envelopePoints.isNotEmpty())
        assertTrue(run.productionGraph.envelopePoints.size <= 1_500)
        assertTrue(
            ExerciseRunPayloadCodec.sizeBytes(payload) <=
                ExerciseRunPayloadCodec.MAXIMUM_PAYLOAD_BYTES,
        )
        assertGraphEquals(run, ExerciseRunPayloadCodec.decode(payload))
    }

    private fun assertGraphEquals(expected: ExerciseRun, actual: ExerciseRun) {
        val left = expected.productionGraph
        val right = actual.productionGraph
        assertEquals(left.exerciseDurationSamples, right.exerciseDurationSamples)
        assertEquals(left.exerciseDurationMillis, right.exerciseDurationMillis, 0.0)
        assertEquals(left.envelopePoints, right.envelopePoints)
        assertEquals(left.expectedNotes, right.expectedNotes)
        assertEquals(left.matchedHits, right.matchedHits)
        assertEquals(left.timingConnectors, right.timingConnectors)
        assertEquals(left.missedNotes, right.missedNotes)
        assertEquals(left.extraHits, right.extraHits)
        assertEquals(left.measureGuides, right.measureGuides)
        assertTrue(right.expectedNotes.any { it.judgement == HitJudgement.MISSED })
    }
}
