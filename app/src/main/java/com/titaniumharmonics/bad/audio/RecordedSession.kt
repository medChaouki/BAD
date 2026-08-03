package com.titaniumharmonics.bad.audio

import com.titaniumharmonics.bad.exercise.RuntimeExercise

private const val NANOS_PER_MILLISECOND = 1_000_000L

enum class PcmEncoding(
    val bitsPerSample: Int,
) {
    SIGNED_16_BIT_LITTLE_ENDIAN(bitsPerSample = 16),
}

data class PcmAudioFormat(
    val sampleRateHz: Int,
    val channelCount: Int,
    val encoding: PcmEncoding,
) {
    init {
        require(sampleRateHz > 0) { "sampleRateHz must be positive." }
        require(channelCount > 0) { "channelCount must be positive." }
        require(encoding.bitsPerSample % Byte.SIZE_BITS == 0) {
            "PCM sample size must contain complete bytes."
        }
    }

    val bytesPerFrame: Int = Math.multiplyExact(
        channelCount,
        encoding.bitsPerSample / Byte.SIZE_BITS,
    )
}

data class FinalizedRecording(
    val filePath: String,
    val format: PcmAudioFormat,
    val totalSampleFrames: Long,
) {
    init {
        require(filePath.isNotBlank()) { "filePath must not be blank." }
        require(totalSampleFrames > 0L) {
            "A finalized recording must contain at least one sample frame."
        }
    }

    val durationNanos: Long = SampleFrameTiming.sampleFramesToDurationNanos(
        sampleFrames = totalSampleFrames,
        sampleRateHz = format.sampleRateHz,
    )

    val durationMillis: Long
        get() = durationNanos / NANOS_PER_MILLISECOND
}

data class RecordedSession(
    val wavFilePath: String,
    val audioFormat: PcmAudioFormat,
    val totalRecordedSampleFrames: Long,
    val exerciseStartSampleFrame: Long,
    val runtimeExercise: RuntimeExercise,
) {
    init {
        require(wavFilePath.isNotBlank()) { "wavFilePath must not be blank." }
        require(totalRecordedSampleFrames > 0L) {
            "A completed session must contain recorded sample frames."
        }
        require(exerciseStartSampleFrame in 0 until totalRecordedSampleFrames) {
            "exerciseStartSampleFrame must precede the end of the recording."
        }
    }

    val gradedExerciseSampleFrames: Long =
        totalRecordedSampleFrames - exerciseStartSampleFrame

    val initialCountInSampleFrames: Long
        get() = exerciseStartSampleFrame

    val recordingDurationNanos: Long = SampleFrameTiming.sampleFramesToDurationNanos(
        sampleFrames = totalRecordedSampleFrames,
        sampleRateHz = audioFormat.sampleRateHz,
    )

    val gradedExerciseDurationNanos: Long = SampleFrameTiming.sampleFramesToDurationNanos(
        sampleFrames = gradedExerciseSampleFrames,
        sampleRateHz = audioFormat.sampleRateHz,
    )

    val recordingDurationMillis: Long
        get() = recordingDurationNanos / NANOS_PER_MILLISECOND

    val gradedExerciseDurationMillis: Long
        get() = gradedExerciseDurationNanos / NANOS_PER_MILLISECOND

    fun recordingSampleToExerciseRelativeSample(recordingSampleFrame: Long): Long? {
        require(recordingSampleFrame in 0..totalRecordedSampleFrames) {
            "recordingSampleFrame is outside the recording."
        }
        if (recordingSampleFrame < exerciseStartSampleFrame) return null
        return recordingSampleFrame - exerciseStartSampleFrame
    }

    fun recordingSampleToExerciseRelativeDurationNanos(
        recordingSampleFrame: Long,
    ): Long? = recordingSampleToExerciseRelativeSample(recordingSampleFrame)?.let {
        SampleFrameTiming.sampleFramesToDurationNanos(
            sampleFrames = it,
            sampleRateHz = audioFormat.sampleRateHz,
        )
    }

    fun exerciseRelativeSampleToRecordingSample(exerciseSampleFrame: Long): Long {
        require(exerciseSampleFrame in 0..gradedExerciseSampleFrames) {
            "exerciseSampleFrame is outside the graded exercise recording."
        }
        return Math.addExact(exerciseStartSampleFrame, exerciseSampleFrame)
    }

    fun exerciseRelativeDurationNanosToRecordingSample(durationNanos: Long): Long {
        val exerciseSampleFrame = SampleFrameTiming.durationNanosToSampleFrames(
            durationNanos = durationNanos,
            sampleRateHz = audioFormat.sampleRateHz,
        )
        return exerciseRelativeSampleToRecordingSample(exerciseSampleFrame)
    }

}

object SampleFrameTiming {
    private const val NANOS_PER_SECOND = 1_000_000_000L

    /** Converts using integer division, rounding fractional nanoseconds down. */
    fun sampleFramesToDurationNanos(sampleFrames: Long, sampleRateHz: Int): Long {
        require(sampleFrames >= 0L) { "sampleFrames must not be negative." }
        require(sampleRateHz > 0) { "sampleRateHz must be positive." }
        val wholeSeconds = sampleFrames / sampleRateHz
        val remainingFrames = sampleFrames % sampleRateHz
        return Math.addExact(
            Math.multiplyExact(wholeSeconds, NANOS_PER_SECOND),
            Math.multiplyExact(remainingFrames, NANOS_PER_SECOND) / sampleRateHz,
        )
    }

    /** Converts using integer division, rounding partial sample frames down. */
    fun durationNanosToSampleFrames(durationNanos: Long, sampleRateHz: Int): Long {
        require(durationNanos >= 0L) { "durationNanos must not be negative." }
        require(sampleRateHz > 0) { "sampleRateHz must be positive." }
        val wholeSeconds = durationNanos / NANOS_PER_SECOND
        val remainingNanos = durationNanos % NANOS_PER_SECOND
        return Math.addExact(
            Math.multiplyExact(wholeSeconds, sampleRateHz.toLong()),
            Math.multiplyExact(remainingNanos, sampleRateHz.toLong()) / NANOS_PER_SECOND,
        )
    }
}
