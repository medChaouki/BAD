package com.titaniumharmonics.bad.history.persistence

import com.titaniumharmonics.bad.audio.calibration.CalibrationConfidence
import com.titaniumharmonics.bad.audio.calibration.TimingCalibration
import com.titaniumharmonics.bad.audio.detection.DetectedHit
import com.titaniumharmonics.bad.audio.detection.HitDetectionConfiguration
import com.titaniumharmonics.bad.audio.detection.SessionDetectionSnapshot
import com.titaniumharmonics.bad.audio.matching.HitJudgement
import com.titaniumharmonics.bad.audio.matching.JudgementConfiguration
import com.titaniumharmonics.bad.audio.matching.SessionJudgementSnapshot
import com.titaniumharmonics.bad.audio.metronome.MetronomeConfiguration
import com.titaniumharmonics.bad.audio.metronome.SessionMetronomeSnapshot
import com.titaniumharmonics.bad.audio.result.ExtraHit
import com.titaniumharmonics.bad.audio.result.JudgedNote
import com.titaniumharmonics.bad.audio.result.PracticeResult
import com.titaniumharmonics.bad.audio.result.ProductionEnvelopePoint
import com.titaniumharmonics.bad.audio.result.ProductionExpectedNoteMarker
import com.titaniumharmonics.bad.audio.result.ProductionExtraHitMarker
import com.titaniumharmonics.bad.audio.result.ProductionGraphModel
import com.titaniumharmonics.bad.audio.result.ProductionMatchedHitMarker
import com.titaniumharmonics.bad.audio.result.ProductionMeasureGuide
import com.titaniumharmonics.bad.audio.result.ProductionMissedNoteMarker
import com.titaniumharmonics.bad.audio.result.ProductionTimingConnector
import com.titaniumharmonics.bad.exercise.RuntimeExercise
import com.titaniumharmonics.bad.exercise.RuntimeExpectedNote
import com.titaniumharmonics.bad.exercise.RuntimeMeasure
import com.titaniumharmonics.bad.exercise.TimeSignature
import com.titaniumharmonics.bad.history.ExerciseRun

internal fun androidExerciseRunFixture(
    runId: String,
    exerciseId: String,
    completedAt: Long,
): ExerciseRun {
    val exercise = RuntimeExercise(
        id = exerciseId,
        name = "Persisted $exerciseId",
        description = "Historical runtime snapshot",
        tempoBpm = 120.0,
        timeSignature = TimeSignature(4, 4),
        ticksPerQuarterNote = 480,
        measures = listOf(
            RuntimeMeasure(
                index = 0,
                startTick = 0L,
                durationTicks = 1_920L,
                notes = listOf(
                    RuntimeExpectedNote(0, 0L, 0L, accent = true),
                    RuntimeExpectedNote(0, 480L, 480L),
                ),
            ),
        ),
    )
    val calibration = TimingCalibration(
        offsetSamples = 240L,
        sampleRateHz = 48_000,
        confidence = CalibrationConfidence.HIGH,
        expectedClickCount = 8,
        matchedClickCount = 8,
        offsetSpreadSamples = 3L,
        calibratedAtEpochMillis = (completedAt - 100_000L).coerceAtLeast(0L),
        algorithmVersion = 2,
    )
    val hit = detectedHit(240L, 0L)
    val extraDetected = detectedHit(48_240L, 48_000L)
    val judgedNotes = listOf(
        JudgedNote(
            expectedNoteIndex = 0,
            expectedExerciseSample = 0L,
            expectedExerciseTimeMillis = 0.0,
            measureIndex = 0,
            positionInMeasureTicks = 0L,
            beatPosition = 0.0,
            matchedHit = hit,
            rawDetectedSample = hit.rawExerciseSample,
            rawDetectedTimeMillis = hit.rawExerciseTimeMillis,
            calibratedDetectedSample = hit.calibratedExerciseSample,
            calibratedDetectedTimeMillis = hit.calibratedExerciseTimeMillis,
            timingErrorSamples = 0L,
            timingErrorMillis = 0.0,
            judgement = HitJudgement.ON_TIME,
            detectionConfidence = hit.confidence,
            relativeIntensity = 0.4,
            accent = true,
            targetIntensity = null,
        ),
        JudgedNote(
            expectedNoteIndex = 1,
            expectedExerciseSample = 24_000L,
            expectedExerciseTimeMillis = 500.0,
            measureIndex = 0,
            positionInMeasureTicks = 480L,
            beatPosition = 1.0,
            matchedHit = null,
            rawDetectedSample = null,
            rawDetectedTimeMillis = null,
            calibratedDetectedSample = null,
            calibratedDetectedTimeMillis = null,
            timingErrorSamples = null,
            timingErrorMillis = null,
            judgement = HitJudgement.MISSED,
            detectionConfidence = null,
            relativeIntensity = null,
            accent = false,
            targetIntensity = null,
        ),
    )
    val extra = ExtraHit(
        detectedHitIndex = 3,
        detectedHit = extraDetected,
        rawSample = extraDetected.rawExerciseSample,
        rawTimeMillis = extraDetected.rawExerciseTimeMillis,
        calibratedSample = extraDetected.calibratedExerciseSample,
        calibratedTimeMillis = extraDetected.calibratedExerciseTimeMillis,
        peakAmplitude = extraDetected.peakAmplitude,
        relativeIntensity = 1.0,
        confidence = extraDetected.confidence,
    )
    val judgement = SessionJudgementSnapshot(
        JudgementConfiguration.DEFAULT.copy(onTimeBeforeMillis = 30.0),
    )
    val detection = SessionDetectionSnapshot(
        HitDetectionConfiguration.DEFAULT.copy(minimumHitSpacingMillis = 50.0),
        calibration,
    )
    val metronome = SessionMetronomeSnapshot(
        MetronomeConfiguration.DEFAULT.withToneFrequency(6_500),
        downbeatsOnly = true,
    )
    val result = PracticeResult(
        schemaVersion = PracticeResult.CURRENT_SCHEMA_VERSION,
        exerciseId = exercise.id,
        exerciseName = exercise.name,
        bpm = exercise.tempoBpm,
        sampleRateHz = 48_000,
        runtimeExercise = exercise,
        judgedNotes = judgedNotes,
        extraHits = listOf(extra),
        totalExpectedNotes = 2,
        earlyCount = 0,
        onTimeCount = 1,
        lateCount = 0,
        missedCount = 1,
        extraCount = 1,
        accuracy = 0.5,
        hitRate = 0.5,
        meanAbsoluteTimingErrorMillis = 0.0,
        signedMeanTimingErrorMillis = 0.0,
        medianAbsoluteTimingErrorMillis = 0.0,
        timingErrorStandardDeviationMillis = 0.0,
        missedRate = 0.5,
        extraHitRate = 0.5,
        meanRelativeIntensity = 0.7,
        minimumRelativeIntensity = 0.4,
        maximumRelativeIntensity = 1.0,
        judgementSnapshot = judgement,
        detectionSnapshot = detection,
        metronomeSnapshot = metronome,
        timingCalibration = calibration,
        calibrationOffsetSamples = 240L,
        calibrationApplied = true,
    )
    val graph = ProductionGraphModel(
        exerciseId = exercise.id,
        sampleRateHz = 48_000,
        exerciseDurationSamples = 96_000L,
        exerciseDurationMillis = 2_000.0,
        maximumEnvelopeAmplitude = 0.8f,
        envelopePoints = listOf(
            ProductionEnvelopePoint(0L, 0.0, 0.0f),
            ProductionEnvelopePoint(24_000L, 500.0, 0.8f),
            ProductionEnvelopePoint(96_000L, 2_000.0, 0.0f),
        ),
        expectedNotes = listOf(
            ProductionExpectedNoteMarker(0, 0L, 0.0, 0, 0.0, HitJudgement.ON_TIME),
            ProductionExpectedNoteMarker(1, 24_000L, 500.0, 0, 1.0, HitJudgement.MISSED),
        ),
        matchedHits = listOf(
            ProductionMatchedHitMarker(0, 0L, 0.0, HitJudgement.ON_TIME, 0.4, 0.9),
        ),
        timingConnectors = listOf(
            ProductionTimingConnector(0, 0L, 0L, 0L, 0.0),
        ),
        missedNotes = listOf(
            ProductionMissedNoteMarker(1, 24_000L, 500.0, 0, 1.0),
        ),
        extraHits = listOf(
            ProductionExtraHitMarker(3, 48_000L, 1_000.0, 1.0, 0.9),
        ),
        measureGuides = listOf(ProductionMeasureGuide(0, 0L)),
    )
    return ExerciseRun(
        runId = runId,
        startedAtEpochMillis = (completedAt - 5_000L).coerceAtLeast(0L),
        completedAtEpochMillis = completedAt,
        practiceResult = result,
        productionGraph = graph,
        appVersion = "1.0-android-test",
    )
}

private fun detectedHit(raw: Long, calibrated: Long) = DetectedHit(
    rawExerciseSample = raw,
    rawExerciseTimeMillis = raw * 1_000.0 / 48_000,
    calibratedExerciseSample = calibrated,
    calibratedExerciseTimeMillis = calibrated * 1_000.0 / 48_000,
    onsetFrame = 1,
    peakFrame = 2,
    peakExerciseSample = raw + 2L,
    peakTimeMillis = (raw + 2L) * 1_000.0 / 48_000,
    peakAmplitude = 0.7f,
    frameLevel = 0.6f,
    envelope = 0.5f,
    noiseFloor = 0.01f,
    signalToNoiseRatio = 12.0,
    confidence = 0.9,
    metronomeBandRatio = 0.1,
    broadbandResidualEnergy = 0.8,
    spectralBandwidthHz = 1_200.0,
    spectralCentroidHz = 2_100.0,
    calibrationApplied = true,
)
