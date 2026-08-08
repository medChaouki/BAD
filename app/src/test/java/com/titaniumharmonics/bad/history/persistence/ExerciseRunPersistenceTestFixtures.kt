package com.titaniumharmonics.bad.history.persistence

import com.titaniumharmonics.bad.audio.calibration.CalibrationConfidence
import com.titaniumharmonics.bad.audio.calibration.TimingCalibration
import com.titaniumharmonics.bad.audio.detection.HitDetectionConfiguration
import com.titaniumharmonics.bad.audio.detection.SessionDetectionSnapshot
import com.titaniumharmonics.bad.audio.matching.JudgementConfiguration
import com.titaniumharmonics.bad.audio.matching.SessionJudgementSnapshot
import com.titaniumharmonics.bad.audio.metronome.MetronomeConfiguration
import com.titaniumharmonics.bad.audio.metronome.SessionMetronomeSnapshot
import com.titaniumharmonics.bad.audio.result.PracticeResult
import com.titaniumharmonics.bad.audio.result.JudgedNote
import com.titaniumharmonics.bad.audio.result.ProductionExpectedNoteMarker
import com.titaniumharmonics.bad.audio.result.ProductionExtraHitMarker
import com.titaniumharmonics.bad.audio.result.ProductionGraphBuildResult
import com.titaniumharmonics.bad.audio.result.ProductionGraphModel
import com.titaniumharmonics.bad.audio.result.ProductionGraphModelBuilder
import com.titaniumharmonics.bad.audio.result.ProductionMeasureGuide
import com.titaniumharmonics.bad.audio.result.ProductionMissedNoteMarker
import com.titaniumharmonics.bad.audio.result.graphFixture
import com.titaniumharmonics.bad.history.ExerciseRun
import com.titaniumharmonics.bad.exercise.RuntimeExercise
import com.titaniumharmonics.bad.exercise.RuntimeMeasure

internal fun exerciseRunFixture(
    runId: String = "11111111-2222-3333-4444-555555555555",
    exerciseId: String = "production-graph-test",
    completedAtEpochMillis: Long = 1_700_000_005_000L,
    frameCount: Int = 101,
): ExerciseRun {
    val graphFixture = graphFixture(frameCount = frameCount)
    val base = graphFixture.result
    val runtimeExercise = if (exerciseId == base.exerciseId) {
        base.runtimeExercise
    } else {
        com.titaniumharmonics.bad.exercise.RuntimeExercise(
            id = exerciseId,
            name = base.runtimeExercise.name,
            description = base.runtimeExercise.description,
            tempoBpm = base.runtimeExercise.tempoBpm,
            timeSignature = base.runtimeExercise.timeSignature,
            ticksPerQuarterNote = base.runtimeExercise.ticksPerQuarterNote,
            measures = base.runtimeExercise.measures,
        )
    }
    val calibration = TimingCalibration(
        offsetSamples = 300L,
        sampleRateHz = base.sampleRateHz,
        confidence = CalibrationConfidence.HIGH,
        expectedClickCount = 8,
        matchedClickCount = 8,
        offsetSpreadSamples = 4L,
        calibratedAtEpochMillis = 1_699_999_000_000L,
        algorithmVersion = 2,
    )
    val judgement = SessionJudgementSnapshot(
        JudgementConfiguration.DEFAULT.copy(
            onTimeBeforeMillis = 35.0,
            onTimeAfterMillis = 45.0,
            minimumHitRateForVerdict = 0.4,
            minimumExtraHitRateForCreativeVerdict = 0.2,
        ),
    )
    val detection = SessionDetectionSnapshot(
        HitDetectionConfiguration.DEFAULT.copy(
            minimumAbsoluteThreshold = 0.03,
            minimumHitSpacingMillis = 42.0,
        ),
        calibration,
    )
    val metronome = SessionMetronomeSnapshot(
        MetronomeConfiguration.DEFAULT.withToneFrequency(6_500),
        downbeatsOnly = true,
    )
    val result = PracticeResult(
        schemaVersion = base.schemaVersion,
        exerciseId = runtimeExercise.id,
        exerciseName = runtimeExercise.name,
        bpm = runtimeExercise.tempoBpm,
        sampleRateHz = base.sampleRateHz,
        runtimeExercise = runtimeExercise,
        judgedNotes = base.judgedNotes,
        extraHits = base.extraHits,
        totalExpectedNotes = base.totalExpectedNotes,
        earlyCount = base.earlyCount,
        onTimeCount = base.onTimeCount,
        lateCount = base.lateCount,
        missedCount = base.missedCount,
        extraCount = base.extraCount,
        accuracy = base.accuracy,
        hitRate = base.hitRate,
        meanAbsoluteTimingErrorMillis = base.meanAbsoluteTimingErrorMillis,
        signedMeanTimingErrorMillis = base.signedMeanTimingErrorMillis,
        medianAbsoluteTimingErrorMillis = base.medianAbsoluteTimingErrorMillis,
        timingErrorStandardDeviationMillis = base.timingErrorStandardDeviationMillis,
        missedRate = base.missedRate,
        extraHitRate = base.extraHitRate,
        meanRelativeIntensity = base.meanRelativeIntensity,
        minimumRelativeIntensity = base.minimumRelativeIntensity,
        maximumRelativeIntensity = base.maximumRelativeIntensity,
        judgementSnapshot = judgement,
        detectionSnapshot = detection,
        metronomeSnapshot = metronome,
        timingCalibration = calibration,
        calibrationOffsetSamples = calibration.offsetSamples,
        calibrationApplied = true,
    )
    val graph = checkNotNull(
        (ProductionGraphModelBuilder.build(result, graphFixture.analysis) as?
            ProductionGraphBuildResult.Success)?.model,
    )
    return ExerciseRun(
        runId = runId,
        startedAtEpochMillis = completedAtEpochMillis - 5_000L,
        completedAtEpochMillis = completedAtEpochMillis,
        practiceResult = result,
        productionGraph = graph,
        appVersion = "1.0-test",
    )
}

internal fun nullableStatisticsRunFixture(): ExerciseRun {
    val run = exerciseRunFixture(runId = "nullable-statistics")
    val source = run.practiceResult
    val result = PracticeResult(
        source.schemaVersion, source.exerciseId, source.exerciseName, source.bpm,
        source.sampleRateHz, source.runtimeExercise, source.judgedNotes, source.extraHits,
        source.totalExpectedNotes, source.earlyCount, source.onTimeCount, source.lateCount,
        source.missedCount, source.extraCount, source.accuracy, source.hitRate,
        null, null, null, null, source.missedRate, source.extraHitRate,
        source.meanRelativeIntensity, source.minimumRelativeIntensity,
        source.maximumRelativeIntensity, source.judgementSnapshot, source.detectionSnapshot,
        source.metronomeSnapshot, source.timingCalibration, source.calibrationOffsetSamples,
        source.calibrationApplied,
    )
    return ExerciseRun(
        run.runId, run.startedAtEpochMillis, run.completedAtEpochMillis,
        result, run.productionGraph, run.appVersion, run.schemaVersion,
    )
}

internal fun allMissedRunFixture(): ExerciseRun {
    val run = exerciseRunFixture(runId = "all-missed")
    val source = run.practiceResult
    val missed = source.judgedNotes.map { note ->
        JudgedNote(
            note.expectedNoteIndex, note.expectedExerciseSample,
            note.expectedExerciseTimeMillis, note.measureIndex,
            note.positionInMeasureTicks, note.beatPosition, null, null, null, null, null,
            null, null, com.titaniumharmonics.bad.audio.matching.HitJudgement.MISSED,
            null, null, note.accent, note.targetIntensity,
        )
    }
    val result = PracticeResult(
        source.schemaVersion, source.exerciseId, source.exerciseName, source.bpm,
        source.sampleRateHz, source.runtimeExercise, missed, emptyList(), missed.size,
        0, 0, 0, missed.size, 0, 0.0, 0.0, null, null, null, null,
        1.0, 0.0, null, null, null, source.judgementSnapshot,
        source.detectionSnapshot, source.metronomeSnapshot, source.timingCalibration,
        source.calibrationOffsetSamples, source.calibrationApplied,
    )
    val graph = ProductionGraphModel(
        exerciseId = source.exerciseId,
        sampleRateHz = source.sampleRateHz,
        exerciseDurationSamples = run.productionGraph.exerciseDurationSamples,
        exerciseDurationMillis = run.productionGraph.exerciseDurationMillis,
        maximumEnvelopeAmplitude = 0.001f,
        envelopePoints = emptyList(),
        expectedNotes = missed.map { note ->
            ProductionExpectedNoteMarker(
                note.expectedNoteIndex, note.expectedExerciseSample,
                note.expectedExerciseTimeMillis, note.measureIndex, note.beatPosition,
                note.judgement,
            )
        },
        matchedHits = emptyList(),
        timingConnectors = emptyList(),
        missedNotes = missed.map { note ->
            ProductionMissedNoteMarker(
                note.expectedNoteIndex, note.expectedExerciseSample,
                note.expectedExerciseTimeMillis, note.measureIndex, note.beatPosition,
            )
        },
        extraHits = emptyList(),
        measureGuides = source.runtimeExercise.measures.map {
            ProductionMeasureGuide(it.index, it.startTick * source.sampleRateHz / 960L)
        },
    )
    return ExerciseRun(
        run.runId, run.startedAtEpochMillis, run.completedAtEpochMillis,
        result, graph, run.appVersion, run.schemaVersion,
    )
}

internal fun zeroExpectedAllExtraRunFixture(): ExerciseRun {
    val run = exerciseRunFixture(runId = "all-extra")
    val source = run.practiceResult
    val runtime = RuntimeExercise(
        source.runtimeExercise.id,
        source.runtimeExercise.name,
        source.runtimeExercise.description,
        source.runtimeExercise.tempoBpm,
        source.runtimeExercise.timeSignature,
        source.runtimeExercise.ticksPerQuarterNote,
        source.runtimeExercise.measures.map { measure ->
            RuntimeMeasure(measure.index, measure.startTick, measure.durationTicks, emptyList())
        },
    )
    val extras = source.extraHits
    val result = PracticeResult(
        source.schemaVersion, source.exerciseId, source.exerciseName, source.bpm,
        source.sampleRateHz, runtime, emptyList(), extras, 0, 0, 0, 0, 0,
        extras.size, 0.0, 0.0, null, null, null, null, 0.0, 1.0,
        1.0, 1.0, 1.0, source.judgementSnapshot, source.detectionSnapshot,
        source.metronomeSnapshot, source.timingCalibration, source.calibrationOffsetSamples,
        source.calibrationApplied,
    )
    val graph = ProductionGraphModel(
        source.exerciseId, source.sampleRateHz, run.exerciseDurationSamples,
        run.exerciseDurationMillis, 0.001f, emptyList(), emptyList(), emptyList(),
        emptyList(), emptyList(), extras.map { extra ->
            ProductionExtraHitMarker(
                extra.detectedHitIndex, extra.calibratedSample, extra.calibratedTimeMillis,
                extra.relativeIntensity, extra.confidence,
            )
        },
        runtime.measures.map { ProductionMeasureGuide(it.index, 0L) },
    )
    return ExerciseRun(
        run.runId, run.startedAtEpochMillis, run.completedAtEpochMillis,
        result, graph, run.appVersion, run.schemaVersion,
    )
}
