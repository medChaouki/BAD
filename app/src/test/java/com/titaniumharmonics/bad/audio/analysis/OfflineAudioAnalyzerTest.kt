package com.titaniumharmonics.bad.audio.analysis

import com.titaniumharmonics.bad.audio.metronome.MetronomeConfiguration
import com.titaniumharmonics.bad.audio.metronome.SessionMetronomeSnapshot
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAudioAnalyzerTest {
    @Test
    fun completedSessionProducesAnalysisAndExcludesInitialCountIn() {
        val countIn = ShortArray(1_000) { Short.MAX_VALUE }
        val graded = ShortArray(4_800).also {
            it[1_200] = 8_000
            it[3_600] = 24_000
        }
        val wav = writeTestWav(countIn + graded, sampleRateHz = 48_000)
        val session = recordedSessionForWav(wav, 48_000, 5_800L, 1_000L)

        val analysis = OfflineAudioAnalyzer().analyze(session)

        assertEquals(48_000, analysis.sampleRateHz)
        assertEquals(4_800L, analysis.gradedSampleFrameCount)
        assertEquals(240, analysis.frameSizeSamples)
        assertEquals(96, analysis.hopSizeSamples)
        assertEquals(119L, analysis.frameCenterExerciseSamples[0])
        assertEquals(119L, analysis.exerciseSampleForFrame(0))
        assertTrue(analysis.frame(0).exerciseTimeMillis > 0.0)
        assertTrue(analysis.maximumNormalizedInputAmplitude < 0.8f)
        assertTrue(analysis.maximumEnvelope > 0.0f)
        assertEquals(analysis.frameCount, analysis.noiseFloor.size)
    }

    @Test
    fun supports44100HzAndUsesActualDurationConversions() {
        val samples = ShortArray(4_410).also { it[2_000] = Short.MAX_VALUE }
        val wav = writeTestWav(samples, sampleRateHz = 44_100)
        val session = recordedSessionForWav(wav, 44_100, 4_410L, 0L)

        val analysis = OfflineAudioAnalyzer().analyze(session)

        assertEquals(44_100, analysis.sampleRateHz)
        assertEquals(221, analysis.frameSizeSamples)
        assertEquals(88, analysis.hopSizeSamples)
        assertTrue(analysis.frame(0).exerciseTimeMillis in 2.4..2.6)
    }

    @Test
    fun silenceProducesFiniteNearZeroAnalysis() {
        val wav = writeTestWav(ShortArray(2_000))
        val session = recordedSessionForWav(wav, 48_000, 2_000L, 0L)

        val analysis = OfflineAudioAnalyzer().analyze(session)

        assertEquals(0.0f, analysis.maximumNormalizedInputAmplitude, 0.0f)
        assertEquals(0.0f, analysis.maximumFramePeak, 0.0f)
        assertEquals(0.0f, analysis.maximumEnvelope, 0.0f)
        repeat(analysis.frameCount) { index ->
            val frame = analysis.frame(index)
            assertTrue(
                listOf(
                    frame.rawSample,
                    frame.filteredSample,
                    frame.framePeak,
                    frame.frameLevel,
                    frame.envelope,
                    frame.noiseFloor,
                ).all(Float::isFinite),
            )
        }
    }

    @Test
    fun mismatchedSessionFailsWithoutDeletingWav() {
        val wav = writeTestWav(shortArrayOf(1, 2, 3, 4))
        val session = recordedSessionForWav(wav, 44_100, 4L, 1L)

        assertThrows(AudioAnalysisException.SessionMismatch::class.java) {
            OfflineAudioAnalyzer().analyze(session)
        }

        assertTrue(wav.isFile)
    }

    @Test
    fun cancellationCheckIsObservedDuringProcessing() {
        val wav = writeTestWav(ShortArray(10_000))
        val session = recordedSessionForWav(wav, 48_000, 10_000L, 0L)
        var checks = 0

        val exception = assertThrows(StopAnalysis::class.java) {
            OfflineAudioAnalyzer().analyze(session) {
                checks += 1
                if (checks >= 3) throw StopAnalysis()
            }
        }

        assertTrue(exception is StopAnalysis)
    }

    @Test
    fun sessionNotchSnapshotDrivesAlignedPreAndPostAnalysisAndPreservesTransient() {
        val sampleRate = 48_000
        val samples = ShortArray(sampleRate) { index ->
            val tone = sin(2.0 * PI * 6_000.0 * index / sampleRate) * 0.35
            val transient = if (index == sampleRate / 2) 0.6 else 0.0
            ((tone + transient).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        val wav = writeTestWav(samples, sampleRate)
        val baseSession = recordedSessionForWav(wav, sampleRate, sampleRate.toLong(), 0L)
        val session = baseSession.copy(
            metronomeSnapshot = SessionMetronomeSnapshot(MetronomeConfiguration.DEFAULT),
        )

        val analysis = OfflineAudioAnalyzer().analyze(session)

        assertEquals(analysis.frameCount, analysis.preNotchFrameLevels.size)
        assertEquals(analysis.frameCount, analysis.preNotchEnvelope.size)
        assertTrue(analysis.maximumMetronomeSuppression > 0.05f)
        assertTrue(
            analysis.preNotchFrameLevels.toList().average() >
                analysis.frameLevels.toList().average() * 3.0,
        )
        assertTrue(analysis.maximumFramePeak > 0.45f)
        assertEquals(analysis.frame(10).postNotchEnvelope, analysis.frame(10).envelope, 0.0f)
    }

    @Test
    fun disabledNotchPreservesPreNotchMetricsAndSessionSnapshotBeatsGlobalChanges() {
        val sampleRate = 44_100
        val samples = ShortArray(sampleRate) { index ->
            (sin(2.0 * PI * 5_000.0 * index / sampleRate) * 12_000).toInt().toShort()
        }
        val wav = writeTestWav(samples, sampleRate)
        val baseSession = recordedSessionForWav(wav, sampleRate, sampleRate.toLong(), 0L)
        val disabled = MetronomeConfiguration.DEFAULT
            .withToneFrequency(5_000)
            .copy(notch = MetronomeConfiguration.DEFAULT.notch.copy(
                enabled = false,
                centerFrequencyHz = 5_000,
            ))
        val analysis = OfflineAudioAnalyzer().analyze(
            baseSession.copy(metronomeSnapshot = SessionMetronomeSnapshot(disabled)),
        )

        assertEquals(5_000, analysis.metronomeConfiguration.tone.frequencyHz)
        assertEquals(
            analysis.preNotchFrameLevels.toList(),
            analysis.frameLevels.toList(),
        )
        assertEquals(
            analysis.preNotchEnvelope.toList(),
            analysis.envelope.toList(),
        )
        assertEquals(0.0f, analysis.maximumMetronomeSuppression, 0.0f)
    }

    private class StopAnalysis : RuntimeException()
}
