package com.titaniumharmonics.bad.audio.calibration

import java.math.BigInteger
import java.util.Collections

data class TimingCalibrationConfig(
    val clickCount: Int = 8,
    val leadingSilenceMillis: Int = 750,
    val clickIntervalMillis: Int = 500,
    val trailingSilenceMillis: Int = 750,
    val microphoneWarmupMillis: Long = 250L,
    val searchRadiusMillis: Int = 250,
    val minimumCorrelation: Double = 0.35,
    val minimumMatchedClicks: Int = 6,
    val maximumSpacingErrorMillis: Double = 8.0,
    val maximumOffsetSpreadMillis: Double = 6.0,
    val highConfidenceCorrelation: Double = 0.65,
    val highConfidenceSpreadMillis: Double = 3.0,
    val algorithmVersion: Int = CURRENT_ALGORITHM_VERSION,
) {
    init {
        require(clickCount > 0 && minimumMatchedClicks in 1..clickCount)
        require(leadingSilenceMillis > searchRadiusMillis)
        require(clickIntervalMillis >= searchRadiusMillis * 2)
        require(trailingSilenceMillis > 0 && microphoneWarmupMillis >= 0L)
        require(minimumCorrelation in 0.0..1.0)
        require(maximumSpacingErrorMillis >= 0.0 && maximumOffsetSpreadMillis >= 0.0)
        require(algorithmVersion > 0)
    }

    fun expectedClickSamples(sampleRateHz: Int, playbackStartSampleFrame: Long): LongArray =
        LongArray(clickCount) { index ->
            Math.addExact(
                playbackStartSampleFrame,
                millisecondsToSamples(
                    leadingSilenceMillis.toLong() + index.toLong() * clickIntervalMillis,
                    sampleRateHz,
                ),
            )
        }

    fun sequenceSampleCount(sampleRateHz: Int): Int {
        val duration = leadingSilenceMillis.toLong() +
            (clickCount - 1L) * clickIntervalMillis + trailingSilenceMillis
        return millisecondsToSamples(duration, sampleRateHz).toInt()
    }

    companion object {
        const val LEGACY_POSITIVE_OFFSET_VERSION = 1
        const val CURRENT_ALGORITHM_VERSION = 2
    }
}

enum class CalibrationConfidence { HIGH, MEDIUM, LOW }

data class TimingCalibration(
    val offsetSamples: Long,
    val sampleRateHz: Int,
    val confidence: CalibrationConfidence,
    val expectedClickCount: Int,
    val matchedClickCount: Int,
    val offsetSpreadSamples: Long,
    val calibratedAtEpochMillis: Long,
    val algorithmVersion: Int,
) {
    init {
        require(offsetSamples > 0L) { "A timing calibration offset must be positive." }
        require(sampleRateHz > 0)
        require(expectedClickCount > 0 && matchedClickCount in 1..expectedClickCount)
        require(offsetSpreadSamples >= 0L && calibratedAtEpochMillis >= 0L)
        require(algorithmVersion > 0)
    }

    val offsetMillis: Double
        get() = offsetSamples * 1_000.0 / sampleRateHz

    fun offsetSamplesAt(targetSampleRateHz: Int): Long =
        TimingCalibrationMath.convertSampleRate(
            offsetSamples,
            sampleRateHz,
            targetSampleRateHz,
        )

    fun calibrateHit(rawExerciseRelativeHitSample: Long, targetSampleRateHz: Int): Long =
        Math.subtractExact(rawExerciseRelativeHitSample, offsetSamplesAt(targetSampleRateHz))
}

data class CalibrationClickMatch(
    val expectedSample: Long,
    val detectedSample: Long,
    val correlation: Double,
) {
    val offsetSamples: Long = detectedSample - expectedSample
}

data class CalibrationDiagnostics(
    val wavFilePath: String,
    val sampleRateHz: Int,
    val totalSampleFrames: Long,
    val expectedClickSamples: List<Long>,
    val matches: List<CalibrationClickMatch>,
    val medianOffsetSamples: Long?,
    val offsetSpreadSamples: Long?,
    val waveform: List<CalibrationWaveformPoint> = emptyList(),
) {
    init {
        require(sampleRateHz > 0 && totalSampleFrames > 0L)
    }
}

data class CalibrationWaveformPoint(
    val sampleFrame: Long,
    val normalizedAmplitude: Float,
)

sealed interface CalibrationProcessingResult {
    data class Success(
        val calibration: TimingCalibration,
        val diagnostics: CalibrationDiagnostics,
    ) : CalibrationProcessingResult

    data class Failure(
        val reason: CalibrationFailureReason,
        val diagnostics: CalibrationDiagnostics? = null,
        val reviewableCalibration: TimingCalibration? = null,
    ) : CalibrationProcessingResult
}

enum class CalibrationFailureReason(val userMessage: String) {
    MICROPHONE_PERMISSION_DENIED("Microphone permission is required."),
    EXTERNAL_AUDIO_ROUTE("Disconnect headphones and external audio devices."),
    AUDIO_ROUTE_CHANGED("The audio route changed during calibration."),
    MICROPHONE_UNAVAILABLE("Could not access the built-in microphone."),
    AUDIO_OUTPUT_UNAVAILABLE("Could not start phone-speaker output."),
    RECORDING_FAILED("Calibration recording failed."),
    PLAYBACK_FAILED("Calibration click playback failed."),
    TOO_FEW_CLICKS("Too few calibration clicks were detected."),
    LOW_CORRELATION("Calibration clicks were too weak or the room was too noisy."),
    INCONSISTENT_TIMING("Detected click timing was inconsistent."),
    NON_POSITIVE_OFFSET(
        "Calibration produced an invalid timing offset. Keep the phone still, use the " +
            "built-in speaker and microphone, and try again in a quiet room.",
    ),
    INVALID_RECORDING("The calibration recording was invalid."),
    UNSUPPORTED_SAMPLE_RATE("The calibration sample rate is unsupported."),
    CANCELLED("Calibration cancelled."),
    UNKNOWN("Calibration failed."),
}

enum class CalibrationAudioDeviceKind {
    BUILT_IN_SPEAKER,
    BUILT_IN_EARPIECE,
    BUILT_IN_MICROPHONE,
    WIRED,
    BLUETOOTH,
    USB,
    HDMI,
    DOCK,
    OTHER,
}

enum class CalibrationRouteStatus { ALLOWED, BLOCKED, UNCERTAIN }

data class CalibrationRouteDecision(
    val status: CalibrationRouteStatus,
    val message: String,
)

object CalibrationRoutePolicy {
    fun evaluate(
        outputDevices: Set<CalibrationAudioDeviceKind>,
        inputDevices: Set<CalibrationAudioDeviceKind>,
    ): CalibrationRouteDecision {
        val externalKinds = setOf(
            CalibrationAudioDeviceKind.WIRED,
            CalibrationAudioDeviceKind.BLUETOOTH,
            CalibrationAudioDeviceKind.USB,
            CalibrationAudioDeviceKind.HDMI,
            CalibrationAudioDeviceKind.DOCK,
        )
        if ((outputDevices + inputDevices).any { it in externalKinds }) {
            return CalibrationRouteDecision(
                CalibrationRouteStatus.BLOCKED,
                "External audio device detected. Disconnect it and check again.",
            )
        }
        val outputIsKnown = outputDevices.isNotEmpty() && outputDevices.all {
            it == CalibrationAudioDeviceKind.BUILT_IN_SPEAKER ||
                it == CalibrationAudioDeviceKind.BUILT_IN_EARPIECE
        }
        val inputIsKnown = inputDevices.isNotEmpty() && inputDevices.all {
            it == CalibrationAudioDeviceKind.BUILT_IN_MICROPHONE
        }
        return if (outputIsKnown && inputIsKnown) {
            CalibrationRouteDecision(
                CalibrationRouteStatus.ALLOWED,
                "Phone speaker and built-in microphone are available.",
            )
        } else {
            CalibrationRouteDecision(
                CalibrationRouteStatus.UNCERTAIN,
                "Android could not confirm every active route. Confirm that all external " +
                    "audio devices are disconnected.",
            )
        }
    }
}

object TimingCalibrationMath {
    fun convertSampleRate(offsetSamples: Long, sourceRateHz: Int, targetRateHz: Int): Long {
        require(sourceRateHz > 0 && targetRateHz > 0)
        val numerator = BigInteger.valueOf(offsetSamples)
            .multiply(BigInteger.valueOf(targetRateHz.toLong()))
        val denominator = BigInteger.valueOf(sourceRateHz.toLong())
        val division = numerator.divideAndRemainder(denominator)
        val magnitudeTwice = division[1].abs().shiftLeft(1)
        val rounded = if (magnitudeTwice >= denominator) {
            division[0] + BigInteger.valueOf(numerator.signum().toLong())
        } else {
            division[0]
        }
        if (rounded < LONG_MINIMUM || rounded > LONG_MAXIMUM) {
            throw ArithmeticException("Converted calibration offset exceeds Long range.")
        }
        return rounded.toLong()
    }

    fun median(values: List<Long>): Long {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else {
            val lower = BigInteger.valueOf(sorted[middle - 1])
            val upper = BigInteger.valueOf(sorted[middle])
            val sum = lower + upper
            val division = sum.divideAndRemainder(BigInteger.valueOf(2L))
            (if (division[1] == BigInteger.ZERO) {
                division[0]
            } else {
                division[0] + BigInteger.valueOf(sum.signum().toLong())
            }).toLong()
        }
    }

    fun medianAbsoluteDeviation(values: List<Long>, median: Long): Long =
        median(values.map { safeAbsoluteDifference(it, median) })

    private fun safeAbsoluteDifference(first: Long, second: Long): Long {
        val difference = BigInteger.valueOf(first).subtract(BigInteger.valueOf(second)).abs()
        return difference.min(BigInteger.valueOf(Long.MAX_VALUE)).toLong()
    }

    private val LONG_MINIMUM = BigInteger.valueOf(Long.MIN_VALUE)
    private val LONG_MAXIMUM = BigInteger.valueOf(Long.MAX_VALUE)
}

internal fun millisecondsToSamples(milliseconds: Long, sampleRateHz: Int): Long {
    require(milliseconds >= 0L && sampleRateHz > 0)
    return Math.multiplyExact(milliseconds, sampleRateHz.toLong()) / 1_000L
}

internal fun <T> immutableList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(values.toList())
