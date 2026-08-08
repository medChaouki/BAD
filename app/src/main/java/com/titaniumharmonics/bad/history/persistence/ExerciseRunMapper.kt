package com.titaniumharmonics.bad.history.persistence

import com.titaniumharmonics.bad.history.ExerciseRun
import com.titaniumharmonics.bad.history.ExerciseRunSummary

internal object ExerciseRunMapper {
    fun toEntity(run: ExerciseRun): ExerciseRunEntity {
        val payload = ExerciseRunPayloadCodec.encode(run)
        val payloadSize = ExerciseRunPayloadCodec.sizeBytes(payload)
        if (payloadSize > ExerciseRunPayloadCodec.MAXIMUM_PAYLOAD_BYTES) {
            throw RunPayloadTooLargeException(payloadSize)
        }
        return ExerciseRunEntity(
            runId = run.runId,
            exerciseId = run.exerciseId,
            exerciseNameSnapshot = run.exerciseNameSnapshot,
            startedAtEpochMillis = run.startedAtEpochMillis,
            completedAtEpochMillis = run.completedAtEpochMillis,
            bpm = run.bpm,
            exerciseDurationMillis = run.exerciseDurationMillis,
            expandedMeasureCount = run.expandedMeasureCount,
            totalExpectedNotes = run.totalExpectedNotes,
            accuracy = run.practiceResult.accuracy,
            hitRate = run.practiceResult.hitRate,
            meanAbsoluteTimingErrorMillis =
                run.practiceResult.meanAbsoluteTimingErrorMillis,
            signedMeanTimingErrorMillis = run.practiceResult.signedMeanTimingErrorMillis,
            missedCount = run.practiceResult.missedCount,
            extraCount = run.practiceResult.extraCount,
            runSchemaVersion = run.schemaVersion,
            appVersion = run.appVersion,
            payloadJson = payload,
        )
    }

    fun fromEntity(entity: ExerciseRunEntity): ExerciseRun {
        val payloadSize = ExerciseRunPayloadCodec.sizeBytes(entity.payloadJson)
        if (payloadSize > ExerciseRunPayloadCodec.MAXIMUM_PAYLOAD_BYTES) {
            throw RunPayloadTooLargeException(payloadSize)
        }
        if (entity.runSchemaVersion != ExerciseRun.CURRENT_SCHEMA_VERSION) {
            throw UnsupportedRunSchemaException(entity.runSchemaVersion)
        }
        val run = ExerciseRunPayloadCodec.decode(entity.payloadJson)
        try {
            require(run.runId == entity.runId)
            require(run.exerciseId == entity.exerciseId)
            require(run.exerciseNameSnapshot == entity.exerciseNameSnapshot)
            require(run.startedAtEpochMillis == entity.startedAtEpochMillis)
            require(run.completedAtEpochMillis == entity.completedAtEpochMillis)
            require(run.bpm.sameValue(entity.bpm))
            require(run.exerciseDurationMillis.sameValue(entity.exerciseDurationMillis))
            require(run.expandedMeasureCount == entity.expandedMeasureCount)
            require(run.totalExpectedNotes == entity.totalExpectedNotes)
            require(run.practiceResult.accuracy.sameValue(entity.accuracy))
            require(run.practiceResult.hitRate.sameValue(entity.hitRate))
            require(run.practiceResult.meanAbsoluteTimingErrorMillis.sameNullableValue(
                entity.meanAbsoluteTimingErrorMillis,
            ))
            require(run.practiceResult.signedMeanTimingErrorMillis.sameNullableValue(
                entity.signedMeanTimingErrorMillis,
            ))
            require(run.practiceResult.missedCount == entity.missedCount)
            require(run.practiceResult.extraCount == entity.extraCount)
            require(run.schemaVersion == entity.runSchemaVersion)
            require(run.appVersion == entity.appVersion)
        } catch (exception: IllegalArgumentException) {
            throw CorruptedRunPayloadException(exception)
        }
        return run
    }

    fun toSummary(entity: ExerciseRunEntity): ExerciseRunSummary = entity.summary()

    fun toSummary(row: ExerciseRunSummaryRow): ExerciseRunSummary = ExerciseRunSummary(
        runId = row.runId,
        exerciseId = row.exerciseId,
        exerciseNameSnapshot = row.exerciseNameSnapshot,
        startedAtEpochMillis = row.startedAtEpochMillis,
        completedAtEpochMillis = row.completedAtEpochMillis,
        bpm = row.bpm,
        exerciseDurationMillis = row.exerciseDurationMillis,
        expandedMeasureCount = row.expandedMeasureCount,
        totalExpectedNotes = row.totalExpectedNotes,
        accuracy = row.accuracy,
        hitRate = row.hitRate,
        meanAbsoluteTimingErrorMillis = row.meanAbsoluteTimingErrorMillis,
        signedMeanTimingErrorMillis = row.signedMeanTimingErrorMillis,
        missedCount = row.missedCount,
        extraCount = row.extraCount,
        schemaVersion = row.runSchemaVersion,
        appVersion = row.appVersion,
    )

    private fun ExerciseRunEntity.summary() = ExerciseRunSummary(
        runId, exerciseId, exerciseNameSnapshot, startedAtEpochMillis,
        completedAtEpochMillis, bpm, exerciseDurationMillis, expandedMeasureCount,
        totalExpectedNotes, accuracy, hitRate, meanAbsoluteTimingErrorMillis,
        signedMeanTimingErrorMillis, missedCount, extraCount, runSchemaVersion, appVersion,
    )

    private fun Double.sameValue(other: Double): Boolean = toBits() == other.toBits()
    private fun Double?.sameNullableValue(other: Double?): Boolean = when {
        this == null -> other == null
        other == null -> false
        else -> sameValue(other)
    }
}

internal class RunPayloadTooLargeException(val sizeBytes: Int) :
    IllegalArgumentException("Run payload exceeds the supported storage bound.")
