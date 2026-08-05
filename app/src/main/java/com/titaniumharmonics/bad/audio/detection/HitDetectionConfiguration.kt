package com.titaniumharmonics.bad.audio.detection

import com.titaniumharmonics.bad.audio.calibration.TimingCalibration

enum class UncertainCandidateBehaviour {
    RETAIN_AS_DRUM,
    REJECT_AS_METRONOME,
}

data class MetronomeRejectionConfiguration(
    val enabled: Boolean = true,
    val fftSize: Int = DEFAULT_FFT_SIZE,
    val analysisWindowMillis: Double = DEFAULT_ANALYSIS_WINDOW_MILLIS,
    val metronomeBandWidthHz: Double = DEFAULT_BAND_WIDTH_HZ,
    val minimumMetronomeBandEnergyRatio: Double = DEFAULT_MINIMUM_BAND_RATIO,
    val minimumBroadbandResidualEnergy: Double = DEFAULT_MINIMUM_BROADBAND_ENERGY,
    val spectralConfidenceThreshold: Double = DEFAULT_SPECTRAL_CONFIDENCE,
    val maximumScheduledDistanceMillis: Double = DEFAULT_MAXIMUM_SCHEDULED_DISTANCE_MILLIS,
    val uncertainCandidateBehaviour: UncertainCandidateBehaviour =
        UncertainCandidateBehaviour.RETAIN_AS_DRUM,
) {
    init {
        require(fftSize in SUPPORTED_FFT_SIZES)
        require(analysisWindowMillis.isFinite() && analysisWindowMillis in 5.0..40.0)
        require(metronomeBandWidthHz.isFinite() && metronomeBandWidthHz in 100.0..3_000.0)
        require(minimumMetronomeBandEnergyRatio.isUnitValue())
        require(
            minimumBroadbandResidualEnergy.isFinite() &&
                minimumBroadbandResidualEnergy in 0.0..1.0,
        )
        require(spectralConfidenceThreshold.isUnitValue())
        require(
            maximumScheduledDistanceMillis.isFinite() &&
                maximumScheduledDistanceMillis in 0.0..100.0,
        )
    }

    companion object {
        val SUPPORTED_FFT_SIZES = setOf(128, 256, 512, 1_024, 2_048)
        const val DEFAULT_FFT_SIZE = 1_024
        const val DEFAULT_ANALYSIS_WINDOW_MILLIS = 16.0
        const val DEFAULT_BAND_WIDTH_HZ = 600.0
        const val DEFAULT_MINIMUM_BAND_RATIO = 0.60
        const val DEFAULT_MINIMUM_BROADBAND_ENERGY = 0.01
        const val DEFAULT_SPECTRAL_CONFIDENCE = 0.60
        const val DEFAULT_MAXIMUM_SCHEDULED_DISTANCE_MILLIS = 30.0
    }
}

data class HitDetectionConfiguration(
    val enabled: Boolean = true,
    val minimumAbsoluteThreshold: Double = 0.02,
    val noiseFloorMultiplier: Double = 3.0,
    val minimumSignalToNoiseRatio: Double = 2.0,
    val minimumAttackRise: Double = 0.012,
    val onsetLookBackMillis: Double = 12.0,
    val peakSearchMillis: Double = 25.0,
    val releaseHysteresisRatio: Double = 0.50,
    val minimumHitSpacingMillis: Double = 35.0,
    val minimumConfidence: Double = 0.30,
    val applyTimingCalibration: Boolean = true,
    val metronomeRejection: MetronomeRejectionConfiguration =
        MetronomeRejectionConfiguration(),
    val version: Int = CURRENT_VERSION,
) {
    init {
        require(minimumAbsoluteThreshold.isFinite() && minimumAbsoluteThreshold in 0.0..1.0)
        require(noiseFloorMultiplier.isFinite() && noiseFloorMultiplier in 1.0..12.0)
        require(minimumSignalToNoiseRatio.isFinite() && minimumSignalToNoiseRatio in 1.0..20.0)
        require(minimumAttackRise.isFinite() && minimumAttackRise in 0.0..1.0)
        require(onsetLookBackMillis.isFinite() && onsetLookBackMillis in 0.0..50.0)
        require(peakSearchMillis.isFinite() && peakSearchMillis in 1.0..100.0)
        require(releaseHysteresisRatio.isFinite() && releaseHysteresisRatio in 0.1..0.95)
        require(minimumHitSpacingMillis.isFinite() && minimumHitSpacingMillis in 5.0..200.0)
        require(minimumConfidence.isUnitValue())
        require(version > 0)
    }

    companion object {
        const val CURRENT_VERSION = 1
        val DEFAULT = HitDetectionConfiguration()
    }
}

/** Frozen detector settings and calibration used by one practice recording. */
data class SessionDetectionSnapshot(
    val configuration: HitDetectionConfiguration = HitDetectionConfiguration.DEFAULT,
    val timingCalibration: TimingCalibration? = null,
) {
    companion object {
        val COMPATIBILITY_FALLBACK = SessionDetectionSnapshot()
    }
}

private fun Double.isUnitValue(): Boolean = isFinite() && this in 0.0..1.0
