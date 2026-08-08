package com.titaniumharmonics.bad.audio.analysis

import com.titaniumharmonics.bad.audio.DebugRecordingPlaybackPhase
import com.titaniumharmonics.bad.audio.DebugRecordingPlaybackState
import com.titaniumharmonics.bad.audio.RecordedSession
import com.titaniumharmonics.bad.audio.detection.CandidateRejectionReason
import com.titaniumharmonics.bad.audio.detection.HitDetectionResult
import java.io.BufferedWriter
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.Collections
import kotlin.math.ceil

object DebugAudioAnalysisCsvExporter {
    const val HEADER =
        "exercise_time_ms,raw_sample,filtered_sample,frame_peak," +
            "frame_level,envelope,noise_floor,pre_notch_level,post_notch_level," +
            "pre_notch_envelope,post_notch_envelope,metronome_frequency_hz," +
            "notch_center_hz,notch_q,notch_enabled,adaptive_threshold,candidate," +
            "metronome_band_ratio,broadband_residual_energy,spectral_bandwidth," +
            "spectral_centroid,classification,rejected_as_metronome,raw_hit_sample," +
            "calibrated_hit_sample,confidence"

    fun write(
        analysis: AudioAnalysis,
        output: OutputStream,
        detection: HitDetectionResult? = null,
    ) {
        try {
            BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { writer ->
                writer.appendLine(HEADER)
                repeat(analysis.frameCount) { index ->
                    val frame = analysis.frame(index)
                    val candidate = detection?.candidates?.firstOrNull { it.onsetFrame == index }
                    writer.append(frame.exerciseTimeMillis.stableString())
                    writer.append(',').append(frame.rawSample.stableString())
                    writer.append(',').append(frame.filteredSample.stableString())
                    writer.append(',').append(frame.framePeak.stableString())
                    writer.append(',').append(frame.frameLevel.stableString())
                    writer.append(',').append(frame.envelope.stableString())
                    writer.append(',').append(frame.noiseFloor.stableString())
                    writer.append(',').append(frame.preNotchFrameLevel.stableString())
                    writer.append(',').append(frame.postNotchFrameLevel.stableString())
                    writer.append(',').append(frame.preNotchEnvelope.stableString())
                    writer.append(',').append(frame.postNotchEnvelope.stableString())
                    writer.append(',').append(
                        analysis.metronomeConfiguration.tone.frequencyHz.toString(),
                    )
                    writer.append(',').append(
                        analysis.metronomeConfiguration.notch.centerFrequencyHz.toString(),
                    )
                    writer.append(',').append(
                        analysis.metronomeConfiguration.notch.qFactor.stableString(),
                    )
                    writer.append(',').append(
                        analysis.metronomeConfiguration.notch.enabled.toString(),
                    )
                    writer.append(',').append(
                        detection?.adaptiveThreshold?.get(index)?.stableString() ?: "",
                    )
                    writer.append(',').append((candidate != null).toString())
                    writer.append(',').append(candidate?.metronomeBandRatio?.stableString() ?: "")
                    writer.append(',').append(
                        candidate?.broadbandResidualEnergy?.stableString() ?: "",
                    )
                    writer.append(',').append(candidate?.spectralBandwidthHz?.stableString() ?: "")
                    writer.append(',').append(candidate?.spectralCentroidHz?.stableString() ?: "")
                    writer.append(',').append(candidate?.classification?.name ?: "")
                    writer.append(',').append(
                        (candidate?.rejectionReason == CandidateRejectionReason.METRONOME_ONLY)
                            .toString(),
                    )
                    writer.append(',').append(candidate?.rawExerciseSample?.toString() ?: "")
                    writer.append(',').append(
                        candidate?.calibratedExerciseSample?.toString() ?: "",
                    )
                    writer.append(',').append(candidate?.confidence?.stableString() ?: "")
                    writer.newLine()
                }
            }
        } catch (exception: IOException) {
            throw AudioAnalysisException.CsvExportFailure(
                "Unable to export debug audio-analysis CSV.",
                exception,
            )
        }
    }

    private fun Double.stableString(): String {
        require(isFinite())
        return java.lang.Double.toString(this)
    }

    private fun Float.stableString(): String {
        require(isFinite())
        return java.lang.Float.toString(this)
    }
}

object DebugHitCandidateCsvExporter {
    const val HEADER =
        "candidate_index,accepted,raw_time_ms,calibrated_time_ms,peak_time_ms," +
            "peak_amplitude,signal_to_noise,metronome_band_ratio," +
            "broadband_residual_energy,spectral_bandwidth,classification,confidence," +
            "calibration_applied"

    fun write(result: HitDetectionResult, output: OutputStream) {
        try {
            BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { writer ->
                writer.appendLine(HEADER)
                result.candidates.forEach { candidate ->
                    writer.append(candidate.index.toString())
                    writer.append(',').append(candidate.accepted.toString())
                    writer.append(',').append(stable(candidate.rawTimeMillis))
                    writer.append(',').append(stable(candidate.calibratedTimeMillis))
                    writer.append(',').append(stable(candidate.peakTimeMillis))
                    writer.append(',').append(stable(candidate.peakAmplitude.toDouble()))
                    writer.append(',').append(stable(candidate.signalToNoiseRatio))
                    writer.append(',').append(stable(candidate.metronomeBandRatio))
                    writer.append(',').append(stable(candidate.broadbandResidualEnergy))
                    writer.append(',').append(stable(candidate.spectralBandwidthHz))
                    writer.append(',').append(candidate.classification.name)
                    writer.append(',').append(stable(candidate.confidence))
                    writer.append(',').append(candidate.calibrationApplied.toString())
                    writer.newLine()
                }
            }
        } catch (exception: IOException) {
            throw AudioAnalysisException.CsvExportFailure(
                "Unable to export debug hit-candidate CSV.",
                exception,
            )
        }
    }

    private fun stable(value: Double): String {
        require(value.isFinite())
        return java.lang.Double.toString(value)
    }
}

data class AnalysisGraphPoint(
    val frameIndex: Int,
    val exerciseSampleFrame: Long,
    val preNotchEnvelope: Float,
    val postNotchEnvelope: Float,
    val noiseFloor: Float,
)

data class AnalysisGraphData(
    val points: List<AnalysisGraphPoint>,
    val maximumValue: Float,
)

object PeakPreservingGraphDownsampler {
    fun downsample(analysis: AudioAnalysis, maximumPointCount: Int): AnalysisGraphData {
        require(maximumPointCount > 0)
        val indexes = when {
            analysis.frameCount <= maximumPointCount ->
                (0 until analysis.frameCount).toList()
            maximumPointCount == 1 -> listOf(maxEnvelopeIndex(analysis, 0, analysis.frameCount))
            else -> selectBucketExtrema(analysis, maximumPointCount)
        }
        val points = indexes.map { index ->
            AnalysisGraphPoint(
                frameIndex = index,
                exerciseSampleFrame = analysis.frameCenterExerciseSamples[index],
                preNotchEnvelope = analysis.preNotchEnvelope[index],
                postNotchEnvelope = analysis.envelope[index],
                noiseFloor = analysis.noiseFloor[index],
            )
        }
        return AnalysisGraphData(
            points = Collections.unmodifiableList(points),
            maximumValue = maxOf(
                analysis.maximumEnvelope,
                maximumPreNotchEnvelope(analysis),
                maximumNoiseFloor(analysis),
                MINIMUM_GRAPH_SCALE,
            ),
        )
    }

    private fun selectBucketExtrema(
        analysis: AudioAnalysis,
        maximumPointCount: Int,
    ): List<Int> {
        val bucketCount = (maximumPointCount / 4).coerceAtLeast(1)
        val bucketSize = ceil(analysis.frameCount.toDouble() / bucketCount).toInt()
        val selected = ArrayList<Int>(maximumPointCount)
        var start = 0
        while (start < analysis.frameCount && selected.size < maximumPointCount) {
            val end = minOf(analysis.frameCount, start + bucketSize)
            var minimumPreIndex = start
            var maximumPreIndex = start
            var minimumPostIndex = start
            var maximumPostIndex = start
            for (index in start + 1 until end) {
                if (analysis.preNotchEnvelope[index] < analysis.preNotchEnvelope[minimumPreIndex]) {
                    minimumPreIndex = index
                }
                if (analysis.preNotchEnvelope[index] > analysis.preNotchEnvelope[maximumPreIndex]) {
                    maximumPreIndex = index
                }
                if (analysis.envelope[index] < analysis.envelope[minimumPostIndex]) {
                    minimumPostIndex = index
                }
                if (analysis.envelope[index] > analysis.envelope[maximumPostIndex]) {
                    maximumPostIndex = index
                }
            }
            listOf(minimumPreIndex, maximumPreIndex, minimumPostIndex, maximumPostIndex)
                .distinct()
                .sorted()
                .forEach { index ->
                    if (selected.size < maximumPointCount) selected += index
                }
            start = end
        }
        return selected
    }

    private fun maxEnvelopeIndex(analysis: AudioAnalysis, start: Int, end: Int): Int {
        var result = start
        for (index in start + 1 until end) {
            if (analysis.envelope[index] > analysis.envelope[result]) result = index
        }
        return result
    }

    private fun maximumNoiseFloor(analysis: AudioAnalysis): Float {
        var maximum = 0.0f
        repeat(analysis.noiseFloor.size) { index ->
            maximum = maxOf(maximum, analysis.noiseFloor[index])
        }
        return maximum
    }

    private fun maximumPreNotchEnvelope(analysis: AudioAnalysis): Float {
        var maximum = 0.0f
        repeat(analysis.preNotchEnvelope.size) { index ->
            maximum = maxOf(maximum, analysis.preNotchEnvelope[index])
        }
        return maximum
    }

    private const val MINIMUM_GRAPH_SCALE = 0.001f
}

data class DebugGraphCursor(
    val wavPositionMillis: Long,
    val normalizedWavPosition: Float,
    val exerciseRelativeMillis: Double?,
    val isInCountIn: Boolean,
)

class DebugAnalysisTimeline(
    private val session: RecordedSession,
) {
    val countInDurationMillis: Double =
        session.exerciseStartSampleFrame * 1_000.0 / session.audioFormat.sampleRateHz
    val totalDurationMillis: Double =
        session.totalRecordedSampleFrames * 1_000.0 / session.audioFormat.sampleRateHz
    val exerciseStartNormalizedPosition: Float =
        session.exerciseStartSampleFrame.toFloat() / session.totalRecordedSampleFrames

    fun cursor(playbackState: DebugRecordingPlaybackState): DebugGraphCursor {
        val positionMillis = when (playbackState.phase) {
            DebugRecordingPlaybackPhase.COMPLETED -> playbackState.durationMillis
            DebugRecordingPlaybackPhase.UNAVAILABLE,
            DebugRecordingPlaybackPhase.ERROR,
            -> 0L
            else -> playbackState.positionMillis
        }.coerceIn(0L, session.recordingDurationMillis)
        val recordingSample = (positionMillis * session.audioFormat.sampleRateHz / 1_000L)
            .coerceIn(0L, session.totalRecordedSampleFrames)
        val exerciseSample = session.recordingSampleToExerciseRelativeSample(recordingSample)
        return DebugGraphCursor(
            wavPositionMillis = positionMillis,
            normalizedWavPosition = if (session.recordingDurationMillis > 0L) {
                positionMillis.toFloat() / session.recordingDurationMillis
            } else {
                0.0f
            }.coerceIn(0.0f, 1.0f),
            exerciseRelativeMillis = exerciseSample?.let {
                it * 1_000.0 / session.audioFormat.sampleRateHz
            },
            isInCountIn = exerciseSample == null,
        )
    }

    fun normalizedWavPositionForExerciseSample(exerciseSampleFrame: Long): Float {
        val wavSample = session.exerciseRelativeSampleToRecordingSample(exerciseSampleFrame)
        return (wavSample.toFloat() / session.totalRecordedSampleFrames).coerceIn(0.0f, 1.0f)
    }
}
