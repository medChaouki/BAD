package com.titaniumharmonics.bad.audio.detection

import com.titaniumharmonics.bad.audio.calibration.CalibrationConfidence
import com.titaniumharmonics.bad.audio.calibration.TimingCalibration
import com.titaniumharmonics.bad.audio.calibration.TimingCalibrationConfig
import com.titaniumharmonics.bad.audio.analysis.AudioAnalysis
import com.titaniumharmonics.bad.audio.analysis.AudioAnalysisConfig
import com.titaniumharmonics.bad.audio.analysis.ImmutableFloatSeries
import com.titaniumharmonics.bad.audio.analysis.ImmutableLongSeries
import com.titaniumharmonics.bad.audio.metronome.MetronomeConfiguration
import com.titaniumharmonics.bad.exercise.RuntimeExercise
import com.titaniumharmonics.bad.exercise.RuntimeExpectedNote
import com.titaniumharmonics.bad.exercise.RuntimeMeasure
import com.titaniumharmonics.bad.exercise.TimeSignature
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineDrumHitDetectorTest {
    @Test
    fun silenceHasNoHitsAndBoundaryTransientsAreSafe() {
        assertEquals(0, detect(emptyList()).totalCandidateCount)
        val boundary = detect(listOf(Event(0, Signal.DRUM), Event(FRAME_COUNT - 3, Signal.DRUM)))
        assertEquals(2, boundary.acceptedCount)
        assertTrue(boundary.hits.first().rawExerciseSample >= 0L)
    }

    @Test
    fun disabledDetectionAndNoHitsAreSuccessfulEmptyResults() {
        val disabled = detect(
            listOf(Event(100, Signal.DRUM)),
            configuration = baseConfiguration().copy(enabled = false),
        )
        assertEquals(0, disabled.totalCandidateCount)
        assertEquals(0, disabled.acceptedCount)
        assertEquals(FRAME_COUNT, disabled.adaptiveThreshold.size)
    }

    @Test
    fun oneLongDecayCreatesOneHitAndMultipleFastPatternsRemainOrdered() {
        val one = detect(listOf(Event(100, Signal.DRUM, decayFrames = 30)))
        assertEquals(1, one.acceptedCount)

        val tripletsAndSixteenths = detect(
            listOf(80, 105, 130, 213, 296).map { Event(it, Signal.DRUM) },
        )
        assertEquals(5, tripletsAndSixteenths.acceptedCount)
        assertEquals(
            tripletsAndSixteenths.hits.map { it.rawExerciseSample }.sorted(),
            tripletsAndSixteenths.hits.map { it.rawExerciseSample },
        )
    }

    @Test
    fun quietAndLoudHitsInNoiseAreDetectedWithDifferentPeakLevels() {
        val result = detect(
            listOf(Event(100, Signal.DRUM, amplitude = 0.25f), Event(250, Signal.DRUM, 0.9f)),
        )
        assertEquals(2, result.acceptedCount)
        assertTrue(result.hits[1].peakAmplitude > result.hits[0].peakAmplitude)
        assertTrue(result.hits.all { it.signalToNoiseRatio > 2.0 })
    }

    @Test
    fun metronomeOnlyIsRejectedButDrumOnMetronomeAndWrongFrequencyRemainDrum() {
        assertEquals(listOf("DRUM", "METRONOME"), CandidateClassification.entries.map { it.name })
        val metronome = detect(listOf(Event(249, Signal.METRONOME)))
        assertEquals(0, metronome.acceptedCount)
        assertEquals(1, metronome.metronomeRejectedCount)
        assertEquals(
            CandidateClassification.METRONOME,
            metronome.rejectedMetronomeCandidates.single().classification,
        )

        val combined = detect(listOf(Event(249, Signal.COMBINED)))
        assertEquals(1, combined.acceptedCount)
        assertEquals(CandidateClassification.DRUM, combined.candidates.single().classification)

        val wrongFrequency = detect(listOf(Event(249, Signal.WRONG_FREQUENCY)))
        assertEquals(1, wrongFrequency.acceptedCount)
        val farTone = detect(listOf(Event(100, Signal.METRONOME)))
        assertEquals(1, farTone.acceptedCount)
    }

    @Test
    fun retriggerConflictKeepsTheStrongerCandidateDeterministically() {
        val result = detect(
            listOf(Event(100, Signal.DRUM, 0.3f), Event(110, Signal.DRUM, 0.9f)),
            configuration = baseConfiguration().copy(peakSearchMillis = 10.0),
        )
        assertEquals(1, result.acceptedCount)
        assertEquals(1, result.otherRejectedCandidates.size)
        assertEquals(
            CandidateRejectionReason.RETRIGGER_SUPPRESSION,
            result.otherRejectedCandidates.single().rejectionReason,
        )
        assertEquals(result.candidates[1].rawExerciseSample, result.hits.single().rawExerciseSample)
    }

    @Test
    fun positiveCalibrationPreservesRawTimingMovesHitsEarlierAndCanBeDisabled() {
        val raw = detect(listOf(Event(100, Signal.DRUM)))
        val positive = detect(
            listOf(Event(100, Signal.DRUM)),
            calibration = calibration(offset = 480L, sampleRate = 48_000),
        )
        assertEquals(raw.hits.single().rawExerciseSample, positive.hits.single().rawExerciseSample)
        assertEquals(
            raw.hits.single().rawExerciseSample - 480L,
            positive.hits.single().calibratedExerciseSample,
        )
        assertTrue(positive.calibrationApplied)

        val converted = detect(
            listOf(Event(100, Signal.DRUM)),
            calibration = calibration(offset = 441L, sampleRate = 44_100),
        )
        assertEquals(
            converted.hits.single().rawExerciseSample - 480L,
            converted.hits.single().calibratedExerciseSample,
        )
        assertTrue(
            converted.hits.single().calibratedExerciseSample <
                converted.hits.single().rawExerciseSample,
        )
        val beforeZero = detect(
            listOf(Event(0, Signal.DRUM)),
            calibration = calibration(offset = 480L, sampleRate = 48_000),
        )
        assertTrue(beforeZero.hits.single().calibratedExerciseSample < 0L)
        assertTrue(beforeZero.hits.single().rawExerciseSample >= 0L)
        val disabled = detect(
            listOf(Event(100, Signal.DRUM)),
            calibration = calibration(480L, 48_000),
            configuration = baseConfiguration().copy(applyTimingCalibration = false),
        )
        assertFalse(disabled.calibrationApplied)
        assertEquals(
            disabled.hits.single().rawExerciseSample,
            disabled.hits.single().calibratedExerciseSample,
        )
    }

    @Test
    fun expectedMarkersUseExactRuntimeTripletAndExpandedPositions() {
        val result = detect(listOf(Event(100, Signal.DRUM)))
        assertEquals(
            listOf(0L, 8_000L, 16_000L, 24_000L, 96_000L),
            result.expectedExerciseSamples.toList(),
        )
    }

    private fun detect(
        events: List<Event>,
        calibration: TimingCalibration? = null,
        configuration: HitDetectionConfiguration = baseConfiguration(),
    ): HitDetectionResult = OfflineDrumHitDetector().detect(
        analysis = analysis(events),
        snapshot = SessionDetectionSnapshot(configuration, calibration),
        runtimeExercise = runtimeExercise(),
    )

    private fun analysis(events: List<Event>): AudioAnalysis {
        val centers = LongArray(FRAME_COUNT) { it * HOP_SIZE.toLong() + FRAME_SIZE / 2L }
        val envelope = FloatArray(FRAME_COUNT) { 0.001f }
        val peaks = FloatArray(FRAME_COUNT) { 0.001f }
        val levels = FloatArray(FRAME_COUNT) { 0.001f }
        val noise = FloatArray(FRAME_COUNT) { 0.001f }
        val pcm = FloatArray(SAMPLE_RATE * 3)
        events.forEach { event ->
            val start = event.frame.coerceIn(0, FRAME_COUNT - 1)
            val decay = event.decayFrames.coerceAtLeast(5)
            for (offset in 0..decay) {
                val frame = start + offset
                if (frame >= FRAME_COUNT) break
                val level = when (offset) {
                    0 -> 0.03f
                    1 -> event.amplitude * 0.55f
                    2 -> event.amplitude
                    else -> event.amplitude * (decay - offset).coerceAtLeast(0) / decay
                }.coerceAtLeast(0.001f)
                envelope[frame] = maxOf(envelope[frame], level)
                peaks[frame] = maxOf(peaks[frame], level)
                levels[frame] = maxOf(levels[frame], level * 0.7f)
            }
            val center = centers[(start + 2).coerceAtMost(FRAME_COUNT - 1)].toInt()
            writeSignal(pcm, center, event)
        }
        return AudioAnalysis(
            sampleRateHz = SAMPLE_RATE,
            gradedSampleFrameCount = pcm.size.toLong(),
            frameSizeSamples = FRAME_SIZE,
            hopSizeSamples = HOP_SIZE,
            frameCenterExerciseSamples = ImmutableLongSeries.copyOf(centers),
            representativeRawSamples = ImmutableFloatSeries.copyOf(FloatArray(FRAME_COUNT)),
            representativeFilteredSamples = ImmutableFloatSeries.copyOf(FloatArray(FRAME_COUNT)),
            preNotchFrameLevels = ImmutableFloatSeries.copyOf(levels),
            preNotchEnvelope = ImmutableFloatSeries.copyOf(envelope),
            framePeaks = ImmutableFloatSeries.copyOf(peaks),
            frameLevels = ImmutableFloatSeries.copyOf(levels),
            envelope = ImmutableFloatSeries.copyOf(envelope),
            noiseFloor = ImmutableFloatSeries.copyOf(noise),
            maximumNormalizedInputAmplitude = peaks.maxOrNull() ?: 0f,
            maximumFramePeak = peaks.maxOrNull() ?: 0f,
            maximumEnvelope = envelope.maxOrNull() ?: 0f,
            meanNoiseFloor = 0.001f,
            configuration = AudioAnalysisConfig(),
            metronomeConfiguration = MetronomeConfiguration.DEFAULT,
            expectedMetronomeExerciseSamples = ImmutableLongSeries.copyOf(longArrayOf(24_000L)),
            maximumMetronomeSuppression = 0f,
            postNotchPcm = ImmutableFloatSeries.copyOf(pcm),
        )
    }

    private fun writeSignal(pcm: FloatArray, center: Int, event: Event) {
        val length = 768
        val start = center - length / 2
        repeat(length) { offset ->
            val index = start + offset
            if (index !in pcm.indices) return@repeat
            val window = sin(PI * offset / (length - 1)).let { it * it }
            val toneFrequency = when (event.signal) {
                Signal.WRONG_FREQUENCY -> 3_500.0
                else -> 6_000.0
            }
            val tone = sin(2.0 * PI * toneFrequency * offset / SAMPLE_RATE) * window *
                event.amplitude
            val drum = if (offset in 360..410) {
                val pattern = ((offset * 37) % 29) / 14.0 - 1.0
                pattern * event.amplitude
            } else {
                0.0
            }
            pcm[index] = when (event.signal) {
                Signal.METRONOME, Signal.WRONG_FREQUENCY -> tone.toFloat()
                Signal.DRUM -> drum.toFloat()
                Signal.COMBINED -> (tone * 0.5 + drum).coerceIn(-1.0, 1.0).toFloat()
            }
        }
    }

    private fun baseConfiguration() = HitDetectionConfiguration.DEFAULT.copy(
        minimumConfidence = 0.1,
    )

    private fun calibration(offset: Long, sampleRate: Int) = TimingCalibration(
        offsetSamples = offset,
        sampleRateHz = sampleRate,
        confidence = CalibrationConfidence.HIGH,
        expectedClickCount = 8,
        matchedClickCount = 8,
        offsetSpreadSamples = 1,
        calibratedAtEpochMillis = 1,
        algorithmVersion = TimingCalibrationConfig.CURRENT_ALGORITHM_VERSION,
    )

    private fun runtimeExercise(): RuntimeExercise {
        val notes = listOf(0L, 160L, 320L, 480L).map { tick ->
            RuntimeExpectedNote(0, tick, tick)
        }
        return RuntimeExercise(
            id = "detector-test",
            name = "Detector test",
            description = "",
            tempoBpm = 120.0,
            timeSignature = TimeSignature(4, 4),
            ticksPerQuarterNote = 480,
            measures = listOf(
                RuntimeMeasure(0, 0L, 1_920L, notes),
                RuntimeMeasure(
                    1,
                    1_920L,
                    1_920L,
                    listOf(RuntimeExpectedNote(1, 0L, 1_920L)),
                ),
            ),
        )
    }

    private data class Event(
        val frame: Int,
        val signal: Signal,
        val amplitude: Float = 0.8f,
        val decayFrames: Int = 5,
    )

    private enum class Signal { DRUM, METRONOME, COMBINED, WRONG_FREQUENCY }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val FRAME_SIZE = 240
        const val HOP_SIZE = 96
        const val FRAME_COUNT = 1_500
    }
}
