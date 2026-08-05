package com.titaniumharmonics.bad.audio.analysis

import com.titaniumharmonics.bad.audio.DebugRecordingPlaybackPhase
import com.titaniumharmonics.bad.audio.DebugRecordingPlaybackState
import com.titaniumharmonics.bad.audio.metronome.MetronomeConfiguration
import com.titaniumharmonics.bad.audio.detection.CandidateClassification
import com.titaniumharmonics.bad.audio.detection.CandidateRejectionReason
import com.titaniumharmonics.bad.audio.detection.DetectedCandidate
import com.titaniumharmonics.bad.audio.detection.HitDetectionConfiguration
import com.titaniumharmonics.bad.audio.detection.HitDetectionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Locale

class DebugAudioAnalysisToolsTest {
    @Test
    fun csvHasStableHeaderRowsFrameCenterValuesAndLocaleIndependentDecimals() {
        val analysis = analysisFixture(
            centers = longArrayOf(120L, 216L),
            raw = floatArrayOf(0.25f, -0.5f),
            filtered = floatArrayOf(0.2f, -0.4f),
            peaks = floatArrayOf(0.3f, 0.6f),
            levels = floatArrayOf(0.1f, 0.2f),
            envelope = floatArrayOf(0.15f, 0.35f),
            noise = floatArrayOf(0.01f, 0.02f),
        )
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            val output = ByteArrayOutputStream()
            DebugAudioAnalysisCsvExporter.write(analysis, output)
            val lines = output.toString(Charsets.UTF_8).trim().lines()

            assertEquals(DebugAudioAnalysisCsvExporter.HEADER, lines[0])
            assertEquals(3, lines.size)
            assertEquals(
                "2.5,0.25,0.2,0.3,0.1,0.15,0.01,0.1,0.1,0.15,0.15," +
                    "6000,6000,10.0,true,,false,,,,,,false,,,",
                lines[1],
            )
            assertEquals(
                "4.5,-0.5,-0.4,0.6,0.2,0.35,0.02,0.2,0.2,0.35,0.35," +
                    "6000,6000,10.0,true,,false,,,,,,false,,,",
                lines[2],
            )
            assertFalse(lines.any { "NaN" in it || "Infinity" in it })
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun csvFailureReturnsDomainErrorWithoutChangingAnalysis() {
        val analysis = analysisFixture()

        assertThrows(AudioAnalysisException.CsvExportFailure::class.java) {
            DebugAudioAnalysisCsvExporter.write(
                analysis,
                object : OutputStream() {
                    override fun write(value: Int) = throw IOException("disk full")
                },
            )
        }

        assertEquals(analysis.frameCount, analysis.envelope.size)
    }

    @Test
    fun hitCandidateCsvHasStableHeaderRowsAndFiniteLocaleIndependentValues() {
        val candidate = DetectedCandidate(
            index = 0,
            accepted = false,
            classification = CandidateClassification.METRONOME,
            rejectionReason = CandidateRejectionReason.METRONOME_ONLY,
            rawExerciseSample = 480L,
            rawTimeMillis = 10.0,
            calibratedExerciseSample = 432L,
            calibratedTimeMillis = 9.0,
            onsetFrame = 5,
            peakFrame = 7,
            peakExerciseSample = 672L,
            peakTimeMillis = 14.0,
            peakAmplitude = 0.8f,
            frameLevel = 0.4f,
            envelope = 0.6f,
            noiseFloor = 0.02f,
            signalToNoiseRatio = 30.0,
            metronomeBandRatio = 0.9,
            broadbandResidualEnergy = 0.005,
            spectralBandwidthHz = 180.0,
            spectralCentroidHz = 6_000.0,
            confidence = 0.88,
            calibrationApplied = true,
        )
        val result = HitDetectionResult(
            hits = emptyList(),
            rejectedMetronomeCandidates = listOf(candidate),
            otherRejectedCandidates = emptyList(),
            candidates = listOf(candidate),
            adaptiveThreshold = ImmutableFloatSeries.copyOf(floatArrayOf(0.02f)),
            expectedExerciseSamples = ImmutableLongSeries.copyOf(longArrayOf()),
            configuration = HitDetectionConfiguration.DEFAULT,
            calibrationOffsetSamples = 48L,
            calibrationApplied = true,
        )
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            val output = ByteArrayOutputStream()
            DebugHitCandidateCsvExporter.write(result, output)
            val lines = output.toString(Charsets.UTF_8).trim().lines()
            assertEquals(DebugHitCandidateCsvExporter.HEADER, lines.first())
            assertEquals(2, lines.size)
            assertEquals(
                "0,false,10.0,9.0,14.0,0.800000011920929,30.0,0.9,0.005," +
                    "180.0,METRONOME,0.88,true",
                lines.last(),
            )
            assertFalse(lines.any { "NaN" in it || "Infinity" in it || ',' !in it })
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun timelineMapsCountInBoundaryExerciseAndCursorStates() {
        val wav = writeTestWav(ShortArray(144_000))
        val session = recordedSessionForWav(wav, 48_000, 144_000L, 48_000L)
        val timeline = DebugAnalysisTimeline(session)

        val countInCursor = timeline.cursor(playbackState(500L, DebugRecordingPlaybackPhase.PLAYING))
        val boundaryCursor = timeline.cursor(playbackState(1_000L, DebugRecordingPlaybackPhase.PAUSED))
        val exerciseCursor = timeline.cursor(playbackState(1_500L, DebugRecordingPlaybackPhase.PLAYING))

        assertTrue(countInCursor.isInCountIn)
        assertEquals(null, countInCursor.exerciseRelativeMillis)
        assertFalse(boundaryCursor.isInCountIn)
        assertEquals(0.0, boundaryCursor.exerciseRelativeMillis!!, 0.001)
        assertEquals(500.0, exerciseCursor.exerciseRelativeMillis!!, 0.001)
        assertEquals(1.0 / 3.0, timeline.exerciseStartNormalizedPosition.toDouble(), 0.001)
        assertEquals(1_000.0, timeline.countInDurationMillis, 0.001)

        val completed = timeline.cursor(
            playbackState(0L, DebugRecordingPlaybackPhase.COMPLETED),
        )
        val stopped = timeline.cursor(playbackState(0L, DebugRecordingPlaybackPhase.READY))
        assertEquals(1.0f, completed.normalizedWavPosition, 0.0f)
        assertEquals(0.0f, stopped.normalizedWavPosition, 0.0f)
    }

    @Test
    fun downsamplingPreservesIsolatedPeakAndLocalExtremaWithinLimit() {
        val envelope = FloatArray(10_000) { 0.1f }
        envelope[1_234] = 1.0f
        envelope[7_000] = 0.0f
        val preEnvelope = FloatArray(10_000) { 0.2f }
        preEnvelope[8_000] = 1.4f
        val analysis = analysisFixture(
            centers = LongArray(envelope.size) { it.toLong() },
            raw = FloatArray(envelope.size),
            filtered = FloatArray(envelope.size),
            peaks = envelope.copyOf(),
            levels = envelope.copyOf(),
            envelope = envelope,
            preEnvelope = preEnvelope,
            noise = FloatArray(envelope.size) { 0.02f },
        )

        val graph = PeakPreservingGraphDownsampler.downsample(analysis, 1_500)

        assertTrue(graph.points.size <= 1_500)
        assertTrue(
            graph.points.any {
                it.exerciseSampleFrame == 1_234L && it.postNotchEnvelope == 1.0f
            },
        )
        assertTrue(
            graph.points.any {
                it.exerciseSampleFrame == 7_000L && it.postNotchEnvelope == 0.0f
            },
        )
        assertTrue(graph.points.all { it.noiseFloor == 0.02f })
        assertTrue(
            graph.points.any {
                it.exerciseSampleFrame == 8_000L && it.preNotchEnvelope == 1.4f
            },
        )
    }

    @Test
    fun graphAvailabilityTracksAnalysisState() {
        fun isAvailable(state: AudioAnalysisState): Boolean = state is AudioAnalysisState.Ready

        assertFalse(isAvailable(AudioAnalysisState.NotStarted))
        assertFalse(isAvailable(AudioAnalysisState.Processing))
        assertFalse(isAvailable(AudioAnalysisState.Failed("bad WAV")))
        assertTrue(isAvailable(AudioAnalysisState.Ready(analysisFixture())))
    }

    private fun playbackState(
        positionMillis: Long,
        phase: DebugRecordingPlaybackPhase,
    ) = DebugRecordingPlaybackState(
        phase = phase,
        filePath = "recording.wav",
        positionMillis = positionMillis,
        durationMillis = 3_000L,
    )

    private fun analysisFixture(
        centers: LongArray = longArrayOf(120L),
        raw: FloatArray = floatArrayOf(0.1f),
        filtered: FloatArray = floatArrayOf(0.08f),
        peaks: FloatArray = floatArrayOf(0.2f),
        levels: FloatArray = floatArrayOf(0.1f),
        envelope: FloatArray = floatArrayOf(0.15f),
        preEnvelope: FloatArray = envelope,
        noise: FloatArray = floatArrayOf(0.01f),
    ): AudioAnalysis = AudioAnalysis(
        sampleRateHz = 48_000,
        gradedSampleFrameCount = maxOf(centers.last() + 1L, 1L),
        frameSizeSamples = 240,
        hopSizeSamples = 96,
        frameCenterExerciseSamples = ImmutableLongSeries.copyOf(centers),
        representativeRawSamples = ImmutableFloatSeries.copyOf(raw),
        representativeFilteredSamples = ImmutableFloatSeries.copyOf(filtered),
        preNotchFrameLevels = ImmutableFloatSeries.copyOf(levels),
        preNotchEnvelope = ImmutableFloatSeries.copyOf(preEnvelope),
        framePeaks = ImmutableFloatSeries.copyOf(peaks),
        frameLevels = ImmutableFloatSeries.copyOf(levels),
        envelope = ImmutableFloatSeries.copyOf(envelope),
        noiseFloor = ImmutableFloatSeries.copyOf(noise),
        maximumNormalizedInputAmplitude = raw.maxOf { kotlin.math.abs(it) },
        maximumFramePeak = peaks.maxOrNull() ?: 0.0f,
        maximumEnvelope = envelope.maxOrNull() ?: 0.0f,
        meanNoiseFloor = noise.average().toFloat(),
        configuration = AudioAnalysisConfig(),
        metronomeConfiguration = MetronomeConfiguration.DEFAULT,
        expectedMetronomeExerciseSamples = ImmutableLongSeries.copyOf(longArrayOf()),
        maximumMetronomeSuppression = 0.0f,
        postNotchPcm = ImmutableFloatSeries.copyOf(
            FloatArray(maxOf(centers.last() + 1L, 1L).toInt()),
        ),
    )
}
