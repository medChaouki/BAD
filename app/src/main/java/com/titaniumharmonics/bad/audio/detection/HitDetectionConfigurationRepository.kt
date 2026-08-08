package com.titaniumharmonics.bad.audio.detection

import android.annotation.SuppressLint
import android.content.Context

interface HitDetectionConfigurationStore {
    fun load(): Map<String, *>
    fun save(values: Map<String, Any>)
    fun reset()
}

class HitDetectionConfigurationRepository(
    private val store: HitDetectionConfigurationStore,
) {
    fun load(): HitDetectionConfiguration = HitDetectionConfigurationCodec.decode(store.load())

    fun save(configuration: HitDetectionConfiguration) {
        store.save(HitDetectionConfigurationCodec.encode(configuration))
    }

    fun reset(): HitDetectionConfiguration {
        store.reset()
        return HitDetectionConfiguration.DEFAULT
    }
}

@SuppressLint("UseKtx")
class SharedPreferencesHitDetectionConfigurationStore(context: Context) :
    HitDetectionConfigurationStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        "hit_detection_configuration",
        Context.MODE_PRIVATE,
    )

    override fun load(): Map<String, *> = preferences.all

    override fun save(values: Map<String, Any>) {
        val editor = preferences.edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                else -> error("Unsupported hit-detection setting value for $key.")
            }
        }
        check(editor.commit()) { "Unable to persist hit-detection settings." }
    }

    override fun reset() {
        check(preferences.edit().clear().commit()) { "Unable to reset hit-detection settings." }
    }
}

object HitDetectionConfigurationCodec {
    fun encode(value: HitDetectionConfiguration): Map<String, Any> = mapOf(
        "version" to value.version,
        "enabled" to value.enabled,
        "minimum_absolute_threshold" to value.minimumAbsoluteThreshold.toString(),
        "noise_floor_multiplier" to value.noiseFloorMultiplier.toString(),
        "minimum_snr" to value.minimumSignalToNoiseRatio.toString(),
        "minimum_attack_rise" to value.minimumAttackRise.toString(),
        "onset_look_back_ms" to value.onsetLookBackMillis.toString(),
        "peak_search_ms" to value.peakSearchMillis.toString(),
        "release_hysteresis_ratio" to value.releaseHysteresisRatio.toString(),
        "minimum_hit_spacing_ms" to value.minimumHitSpacingMillis.toString(),
        "minimum_confidence" to value.minimumConfidence.toString(),
        "apply_timing_calibration" to value.applyTimingCalibration,
        "rejection_enabled" to value.metronomeRejection.enabled,
        "fft_size" to value.metronomeRejection.fftSize,
        "fft_window_ms" to value.metronomeRejection.analysisWindowMillis.toString(),
        "metronome_band_width_hz" to value.metronomeRejection.metronomeBandWidthHz.toString(),
        "minimum_band_ratio" to
            value.metronomeRejection.minimumMetronomeBandEnergyRatio.toString(),
        "minimum_broadband_energy" to
            value.metronomeRejection.minimumBroadbandResidualEnergy.toString(),
        "spectral_confidence" to
            value.metronomeRejection.spectralConfidenceThreshold.toString(),
        "maximum_scheduled_distance_ms" to
            value.metronomeRejection.maximumScheduledDistanceMillis.toString(),
        "uncertain_behaviour" to value.metronomeRejection.uncertainCandidateBehaviour.name,
    )

    fun decode(values: Map<String, *>): HitDetectionConfiguration = runCatching {
        if (values.isEmpty()) return HitDetectionConfiguration.DEFAULT
        val version = values["version"] as? Int ?: return HitDetectionConfiguration.DEFAULT
        if (version != HitDetectionConfiguration.CURRENT_VERSION) {
            return HitDetectionConfiguration.DEFAULT
        }
        HitDetectionConfiguration(
            enabled = values.boolean("enabled"),
            minimumAbsoluteThreshold = values.float("minimum_absolute_threshold"),
            noiseFloorMultiplier = values.float("noise_floor_multiplier"),
            minimumSignalToNoiseRatio = values.float("minimum_snr"),
            minimumAttackRise = values.float("minimum_attack_rise"),
            onsetLookBackMillis = values.float("onset_look_back_ms"),
            peakSearchMillis = values.float("peak_search_ms"),
            releaseHysteresisRatio = values.float("release_hysteresis_ratio"),
            minimumHitSpacingMillis = values.float("minimum_hit_spacing_ms"),
            minimumConfidence = values.float("minimum_confidence"),
            applyTimingCalibration = values.boolean("apply_timing_calibration"),
            metronomeRejection = MetronomeRejectionConfiguration(
                enabled = values.boolean("rejection_enabled"),
                fftSize = values.int("fft_size"),
                analysisWindowMillis = values.float("fft_window_ms"),
                metronomeBandWidthHz = values.float("metronome_band_width_hz"),
                minimumMetronomeBandEnergyRatio = values.float("minimum_band_ratio"),
                minimumBroadbandResidualEnergy = values.float("minimum_broadband_energy"),
                spectralConfidenceThreshold = values.float("spectral_confidence"),
                maximumScheduledDistanceMillis = values.float("maximum_scheduled_distance_ms"),
                uncertainCandidateBehaviour = UncertainCandidateBehaviour.valueOf(
                    values["uncertain_behaviour"] as? String ?: error("Missing setting."),
                ),
            ),
            version = version,
        )
    }.getOrDefault(HitDetectionConfiguration.DEFAULT)

    private fun Map<String, *>.boolean(key: String): Boolean =
        this[key] as? Boolean ?: error("Missing $key.")

    private fun Map<String, *>.int(key: String): Int =
        this[key] as? Int ?: error("Missing $key.")

    private fun Map<String, *>.float(key: String): Double = when (val value = this[key]) {
        is String -> value.toDouble()
        is Float -> value.toDouble()
        else -> error("Missing $key.")
    }
}
