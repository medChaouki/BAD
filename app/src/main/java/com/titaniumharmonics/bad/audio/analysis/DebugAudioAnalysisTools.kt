package com.titaniumharmonics.bad.audio.analysis

import com.titaniumharmonics.bad.audio.DebugRecordingPlaybackPhase
import com.titaniumharmonics.bad.audio.DebugRecordingPlaybackState
import com.titaniumharmonics.bad.audio.RecordedSession
import java.io.BufferedWriter
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.Collections
import kotlin.math.ceil

object DebugAudioAnalysisCsvExporter {
    const val HEADER =
        "exercise_time_ms,raw_sample,filtered_sample,frame_peak," +
            "frame_level,envelope,noise_floor"

    fun write(analysis: AudioAnalysis, output: OutputStream) {
        try {
            BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { writer ->
                writer.appendLine(HEADER)
                repeat(analysis.frameCount) { index ->
                    val frame = analysis.frame(index)
                    writer.append(frame.exerciseTimeMillis.stableString())
                    writer.append(',').append(frame.rawSample.stableString())
                    writer.append(',').append(frame.filteredSample.stableString())
                    writer.append(',').append(frame.framePeak.stableString())
                    writer.append(',').append(frame.frameLevel.stableString())
                    writer.append(',').append(frame.envelope.stableString())
                    writer.append(',').append(frame.noiseFloor.stableString())
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

data class AnalysisGraphPoint(
    val exerciseSampleFrame: Long,
    val envelope: Float,
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
                exerciseSampleFrame = analysis.frameCenterExerciseSamples[index],
                envelope = analysis.envelope[index],
                noiseFloor = analysis.noiseFloor[index],
            )
        }
        return AnalysisGraphData(
            points = Collections.unmodifiableList(points),
            maximumValue = maxOf(
                analysis.maximumEnvelope,
                maximumNoiseFloor(analysis),
                MINIMUM_GRAPH_SCALE,
            ),
        )
    }

    private fun selectBucketExtrema(
        analysis: AudioAnalysis,
        maximumPointCount: Int,
    ): List<Int> {
        val bucketCount = maximumPointCount / 2
        val bucketSize = ceil(analysis.frameCount.toDouble() / bucketCount).toInt()
        val selected = ArrayList<Int>(maximumPointCount)
        var start = 0
        while (start < analysis.frameCount && selected.size < maximumPointCount) {
            val end = minOf(analysis.frameCount, start + bucketSize)
            var minimumIndex = start
            var maximumIndex = start
            for (index in start + 1 until end) {
                if (analysis.envelope[index] < analysis.envelope[minimumIndex]) {
                    minimumIndex = index
                }
                if (analysis.envelope[index] > analysis.envelope[maximumIndex]) {
                    maximumIndex = index
                }
            }
            if (minimumIndex == maximumIndex) {
                selected += minimumIndex
            } else if (minimumIndex < maximumIndex) {
                selected += minimumIndex
                if (selected.size < maximumPointCount) selected += maximumIndex
            } else {
                selected += maximumIndex
                if (selected.size < maximumPointCount) selected += minimumIndex
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
