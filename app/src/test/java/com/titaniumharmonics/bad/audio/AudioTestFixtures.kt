package com.titaniumharmonics.bad.audio

import com.titaniumharmonics.bad.exercise.RuntimeExercise
import com.titaniumharmonics.bad.exercise.RuntimeMeasure
import com.titaniumharmonics.bad.exercise.TimeSignature

fun runtimeExerciseForAudioTest(): RuntimeExercise = RuntimeExercise(
    id = "recording-test",
    name = "Recording Test",
    description = "Audio timing fixture",
    tempoBpm = 120.0,
    timeSignature = TimeSignature(numerator = 4, denominator = 4),
    ticksPerQuarterNote = 480,
    measures = listOf(
        RuntimeMeasure(
            index = 0,
            startTick = 0L,
            durationTicks = 1_920L,
            notes = emptyList(),
        ),
    ),
)

fun recordedSessionFile(
    sampleRateHz: Int = 48_000,
    exerciseStartSampleFrame: Long = sampleRateHz.toLong(),
    totalSampleFrames: Long = sampleRateHz * 2L,
): RecordedSession {
    val file = java.io.File(
        java.nio.file.Files.createTempDirectory("bad-recording").toFile(),
        "recording.wav",
    )
    file.writeBytes(byteArrayOf(1))
    return RecordedSession(
        wavFilePath = file.absolutePath,
        audioFormat = PcmAudioFormat(
            sampleRateHz = sampleRateHz,
            channelCount = 1,
            encoding = PcmEncoding.SIGNED_16_BIT_LITTLE_ENDIAN,
        ),
        totalRecordedSampleFrames = totalSampleFrames,
        exerciseStartSampleFrame = exerciseStartSampleFrame,
        runtimeExercise = runtimeExerciseForAudioTest(),
    )
}
