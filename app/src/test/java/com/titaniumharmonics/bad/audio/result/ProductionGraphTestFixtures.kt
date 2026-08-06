package com.titaniumharmonics.bad.audio.result

import com.titaniumharmonics.bad.audio.analysis.AudioAnalysis
import com.titaniumharmonics.bad.audio.analysis.AudioAnalysisConfig
import com.titaniumharmonics.bad.audio.analysis.ImmutableFloatSeries
import com.titaniumharmonics.bad.audio.analysis.ImmutableLongSeries
import com.titaniumharmonics.bad.audio.detection.DetectedHit
import com.titaniumharmonics.bad.audio.detection.SessionDetectionSnapshot
import com.titaniumharmonics.bad.audio.matching.HitJudgement
import com.titaniumharmonics.bad.audio.matching.SessionJudgementSnapshot
import com.titaniumharmonics.bad.audio.metronome.SessionMetronomeSnapshot
import com.titaniumharmonics.bad.exercise.RuntimeExercise
import com.titaniumharmonics.bad.exercise.RuntimeExpectedNote
import com.titaniumharmonics.bad.exercise.RuntimeMeasure
import com.titaniumharmonics.bad.exercise.TimeSignature

internal data class GraphFixture(
    val result: PracticeResult,
    val analysis: AudioAnalysis,
)

internal fun graphFixture(
    inconsistentError: Boolean = false,
    frameCount: Int = 101,
): GraphFixture {
    val sampleRate = 48_000
    val ticks = listOf(0L, 480L, 960L, 1_440L)
    val exercise = RuntimeExercise(
        id = "production-graph-test",
        name = "Production graph test",
        description = "",
        tempoBpm = 120.0,
        timeSignature = TimeSignature(4, 4),
        ticksPerQuarterNote = 480,
        measures = listOf(
            RuntimeMeasure(
                index = 0,
                startTick = 0L,
                durationTicks = 1_920L,
                notes = ticks.map { RuntimeExpectedNote(0, it, it) },
            ),
        ),
    )
    val expectedSamples = listOf(0L, 24_000L, 48_000L, 72_000L)
    val judgements = listOf(
        judgedNote(0, expectedSamples[0], -1_440L, HitJudgement.EARLY, inconsistentError),
        judgedNote(1, expectedSamples[1], 0L, HitJudgement.ON_TIME, inconsistentError),
        judgedNote(2, expectedSamples[2], 2_400L, HitJudgement.LATE, inconsistentError),
        JudgedNote(
            expectedNoteIndex = 3,
            expectedExerciseSample = expectedSamples[3],
            expectedExerciseTimeMillis = 1_500.0,
            measureIndex = 0,
            positionInMeasureTicks = 1_440L,
            beatPosition = 3.0,
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
    val extraDetected = detectedHit(raw = 60_300L, calibrated = 60_000L, amplitude = 0.6f)
    val extra = ExtraHit(
        detectedHitIndex = 7,
        detectedHit = extraDetected,
        rawSample = extraDetected.rawExerciseSample,
        rawTimeMillis = extraDetected.rawExerciseTimeMillis,
        calibratedSample = extraDetected.calibratedExerciseSample,
        calibratedTimeMillis = extraDetected.calibratedExerciseTimeMillis,
        peakAmplitude = extraDetected.peakAmplitude,
        relativeIntensity = 0.5,
        confidence = extraDetected.confidence,
    )
    val result = PracticeResult(
        schemaVersion = 1,
        exerciseId = exercise.id,
        exerciseName = exercise.name,
        bpm = exercise.tempoBpm,
        sampleRateHz = sampleRate,
        runtimeExercise = exercise,
        judgedNotes = judgements,
        extraHits = listOf(extra),
        totalExpectedNotes = 4,
        earlyCount = 1,
        onTimeCount = 1,
        lateCount = 1,
        missedCount = 1,
        extraCount = 1,
        accuracy = 0.25,
        hitRate = 0.75,
        meanAbsoluteTimingErrorMillis = 26.666666,
        signedMeanTimingErrorMillis = 6.666666,
        medianAbsoluteTimingErrorMillis = 30.0,
        timingErrorStandardDeviationMillis = 32.998,
        missedRate = 0.25,
        extraHitRate = 0.25,
        meanRelativeIntensity = 0.625,
        minimumRelativeIntensity = 0.25,
        maximumRelativeIntensity = 1.0,
        judgementSnapshot = SessionJudgementSnapshot.COMPATIBILITY_FALLBACK,
        detectionSnapshot = SessionDetectionSnapshot.COMPATIBILITY_FALLBACK,
        metronomeSnapshot = SessionMetronomeSnapshot.COMPATIBILITY_FALLBACK,
        timingCalibration = null,
        calibrationOffsetSamples = 300L,
        calibrationApplied = true,
    )
    return GraphFixture(result, analysis(frameCount, sampleRate))
}

private fun judgedNote(
    index: Int,
    expected: Long,
    error: Long,
    judgement: HitJudgement,
    inconsistentError: Boolean,
): JudgedNote {
    val calibrated = expected + error
    val raw = calibrated + 300L
    val hit = detectedHit(raw, calibrated, 0.25f + index * 0.25f)
    val expectedMillis = expected * 1_000.0 / 48_000
    val errorMillis = error * 1_000.0 / 48_000
    return JudgedNote(
        expectedNoteIndex = index,
        expectedExerciseSample = expected,
        expectedExerciseTimeMillis = expectedMillis,
        measureIndex = 0,
        positionInMeasureTicks = index * 480L,
        beatPosition = index.toDouble(),
        matchedHit = hit,
        rawDetectedSample = raw,
        rawDetectedTimeMillis = raw * 1_000.0 / 48_000,
        calibratedDetectedSample = calibrated,
        calibratedDetectedTimeMillis = calibrated * 1_000.0 / 48_000,
        timingErrorSamples = if (inconsistentError && index == 0) error + 1L else error,
        timingErrorMillis = errorMillis,
        judgement = judgement,
        detectionConfidence = hit.confidence,
        relativeIntensity = when (index) { 0 -> 0.25; 1 -> 0.75; else -> 1.0 },
        accent = false,
        targetIntensity = null,
    )
}

private fun detectedHit(raw: Long, calibrated: Long, amplitude: Float) = DetectedHit(
    rawExerciseSample = raw,
    rawExerciseTimeMillis = raw * 1_000.0 / 48_000,
    calibratedExerciseSample = calibrated,
    calibratedExerciseTimeMillis = calibrated * 1_000.0 / 48_000,
    onsetFrame = 0,
    peakFrame = 0,
    peakExerciseSample = raw,
    peakTimeMillis = raw * 1_000.0 / 48_000,
    peakAmplitude = amplitude,
    frameLevel = amplitude,
    envelope = amplitude,
    noiseFloor = 0.01f,
    signalToNoiseRatio = 10.0,
    confidence = 0.9,
    metronomeBandRatio = 0.0,
    broadbandResidualEnergy = 1.0,
    spectralBandwidthHz = 1_000.0,
    spectralCentroidHz = 2_000.0,
    calibrationApplied = true,
)

private fun analysis(frameCount: Int, sampleRate: Int): AudioAnalysis {
    val durationSamples = 96_000
    val centers = LongArray(frameCount) { index ->
        if (frameCount == 1) 0L else index.toLong() * (durationSamples - 1) / (frameCount - 1)
    }
    val envelope = FloatArray(frameCount) { 0.05f }
    if (frameCount > 2) envelope[frameCount / 2] = 1.0f
    val values = { ImmutableFloatSeries.copyOf(envelope) }
    return AudioAnalysis(
        sampleRateHz = sampleRate,
        gradedSampleFrameCount = durationSamples.toLong(),
        frameSizeSamples = 240,
        hopSizeSamples = 96,
        frameCenterExerciseSamples = ImmutableLongSeries.copyOf(centers),
        representativeRawSamples = values(),
        representativeFilteredSamples = values(),
        preNotchFrameLevels = values(),
        preNotchEnvelope = values(),
        framePeaks = values(),
        frameLevels = values(),
        envelope = values(),
        noiseFloor = ImmutableFloatSeries.copyOf(FloatArray(frameCount)),
        maximumNormalizedInputAmplitude = 1f,
        maximumFramePeak = 1f,
        maximumEnvelope = 1f,
        meanNoiseFloor = 0f,
        configuration = AudioAnalysisConfig(),
        metronomeConfiguration = SessionMetronomeSnapshot.COMPATIBILITY_FALLBACK.configuration,
        expectedMetronomeExerciseSamples = ImmutableLongSeries.copyOf(longArrayOf()),
        maximumMetronomeSuppression = 0f,
        postNotchPcm = ImmutableFloatSeries.copyOf(FloatArray(durationSamples)),
    )
}
