package com.titaniumharmonics.bad.audio.calibration

import android.annotation.SuppressLint
import android.content.Context

interface TimingCalibrationStore {
    fun load(expectedAlgorithmVersion: Int): TimingCalibration?
    fun save(calibration: TimingCalibration)
    fun reset()
}

class TimingCalibrationRepository(
    private val store: TimingCalibrationStore,
    private val algorithmVersion: Int = TimingCalibrationConfig().algorithmVersion,
) {
    fun activeCalibration(): TimingCalibration? = store.load(algorithmVersion)
    fun hasValidCalibration(): Boolean = activeCalibration() != null
    fun offsetSamplesFor(targetSampleRateHz: Int): Long? =
        activeCalibration()?.offsetSamplesAt(targetSampleRateHz)
    fun saveSuccessful(calibration: TimingCalibration) {
        require(calibration.algorithmVersion == algorithmVersion)
        require(calibration.confidence != CalibrationConfidence.LOW)
        store.save(calibration)
    }
    fun saveAccepted(calibration: TimingCalibration) {
        require(calibration.algorithmVersion == algorithmVersion)
        store.save(calibration)
    }
    fun reset() = store.reset()
}

@SuppressLint("UseKtx")
class SharedPreferencesTimingCalibrationStore(context: Context) : TimingCalibrationStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun load(expectedAlgorithmVersion: Int): TimingCalibration? {
        val values = preferences.all
        if (values.isEmpty()) return null
        val calibration = TimingCalibrationPersistenceCodec.decode(
            values = preferences.all,
            expectedAlgorithmVersion = expectedAlgorithmVersion,
        )
        if (calibration == null) preferences.edit().clear().apply()
        return calibration
    }

    override fun save(calibration: TimingCalibration) {
        check(
            preferences.edit()
                .putLong(OFFSET_SAMPLES, calibration.offsetSamples)
                .putInt(SAMPLE_RATE_HZ, calibration.sampleRateHz)
                .putString(CONFIDENCE, calibration.confidence.name)
                .putInt(EXPECTED_CLICK_COUNT, calibration.expectedClickCount)
                .putInt(MATCHED_CLICK_COUNT, calibration.matchedClickCount)
                .putLong(OFFSET_SPREAD_SAMPLES, calibration.offsetSpreadSamples)
                .putLong(CALIBRATED_AT, calibration.calibratedAtEpochMillis)
                .putInt(ALGORITHM_VERSION, calibration.algorithmVersion)
                .commit(),
        ) { "Unable to persist timing calibration." }
    }

    override fun reset() {
        preferences.edit().clear().apply()
    }

    internal companion object {
        const val PREFERENCES_NAME = "timing_calibration"
        const val OFFSET_SAMPLES = "offset_samples"
        const val SAMPLE_RATE_HZ = "sample_rate_hz"
        const val CONFIDENCE = "confidence"
        const val EXPECTED_CLICK_COUNT = "expected_click_count"
        const val MATCHED_CLICK_COUNT = "matched_click_count"
        const val OFFSET_SPREAD_SAMPLES = "offset_spread_samples"
        const val CALIBRATED_AT = "calibrated_at_epoch_millis"
        const val ALGORITHM_VERSION = "algorithm_version"
    }
}

object TimingCalibrationPersistenceCodec {
    fun decode(values: Map<String, *>, expectedAlgorithmVersion: Int): TimingCalibration? =
        runCatching {
            val version = values[SharedPreferencesTimingCalibrationStore.ALGORITHM_VERSION] as? Int
                ?: return null
            val compatibleLegacyPositiveVersion =
                expectedAlgorithmVersion == TimingCalibrationConfig.CURRENT_ALGORITHM_VERSION &&
                    version == TimingCalibrationConfig.LEGACY_POSITIVE_OFFSET_VERSION
            if (version != expectedAlgorithmVersion && !compatibleLegacyPositiveVersion) return null
            val offsetSamples =
                values[SharedPreferencesTimingCalibrationStore.OFFSET_SAMPLES] as? Long
                    ?: return null
            if (offsetSamples <= 0L) return null
            TimingCalibration(
                offsetSamples = offsetSamples,
                sampleRateHz = values[SharedPreferencesTimingCalibrationStore.SAMPLE_RATE_HZ] as? Int
                    ?: return null,
                confidence = CalibrationConfidence.valueOf(
                    values[SharedPreferencesTimingCalibrationStore.CONFIDENCE] as? String
                        ?: return null,
                ),
                expectedClickCount =
                    values[SharedPreferencesTimingCalibrationStore.EXPECTED_CLICK_COUNT] as? Int
                        ?: return null,
                matchedClickCount =
                    values[SharedPreferencesTimingCalibrationStore.MATCHED_CLICK_COUNT] as? Int
                        ?: return null,
                offsetSpreadSamples =
                    values[SharedPreferencesTimingCalibrationStore.OFFSET_SPREAD_SAMPLES] as? Long
                        ?: return null,
                calibratedAtEpochMillis =
                    values[SharedPreferencesTimingCalibrationStore.CALIBRATED_AT] as? Long
                        ?: return null,
                algorithmVersion = expectedAlgorithmVersion,
            )
        }.getOrNull()
}
