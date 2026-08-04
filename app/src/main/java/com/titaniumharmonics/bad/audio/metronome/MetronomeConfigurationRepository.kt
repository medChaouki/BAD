package com.titaniumharmonics.bad.audio.metronome

import android.annotation.SuppressLint
import android.content.Context

interface MetronomeConfigurationStore {
    fun load(): Map<String, *>
    fun save(values: Map<String, Any>)
    fun reset()
}

class MetronomeConfigurationRepository(
    private val store: MetronomeConfigurationStore,
) {
    fun load(): MetronomeConfiguration = MetronomeConfigurationCodec.decode(store.load())

    fun save(configuration: MetronomeConfiguration) {
        store.save(MetronomeConfigurationCodec.encode(configuration))
    }

    fun reset(): MetronomeConfiguration {
        store.reset()
        return MetronomeConfiguration.DEFAULT
    }
}

@SuppressLint("UseKtx")
class SharedPreferencesMetronomeConfigurationStore(context: Context) :
    MetronomeConfigurationStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun load(): Map<String, *> = preferences.all

    override fun save(values: Map<String, Any>) {
        val editor = preferences.edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is Int -> editor.putInt(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                else -> error("Unsupported metronome setting value for $key.")
            }
        }
        check(editor.commit()) { "Unable to persist metronome settings." }
    }

    override fun reset() {
        check(preferences.edit().clear().commit()) { "Unable to reset metronome settings." }
    }

    private companion object {
        const val PREFERENCES_NAME = "metronome_configuration"
    }
}

object MetronomeConfigurationCodec {
    private const val VERSION = "version"
    private const val TONE_FREQUENCY = "tone_frequency_hz"
    private const val TONE_DURATION = "tone_duration_ms"
    private const val NORMAL_VOLUME = "normal_volume_percent"
    private const val ACCENT_VOLUME = "accent_volume_percent"
    private const val WINDOW = "window"
    private const val NOTCH_ENABLED = "notch_enabled"
    private const val NOTCH_CENTER = "notch_center_hz"
    private const val NOTCH_Q = "notch_q"
    private const val NOTCH_LINKED = "notch_linked"

    fun encode(configuration: MetronomeConfiguration): Map<String, Any> = mapOf(
        VERSION to configuration.version,
        TONE_FREQUENCY to configuration.tone.frequencyHz,
        TONE_DURATION to configuration.tone.durationMillis,
        NORMAL_VOLUME to configuration.tone.normalVolumePercent,
        ACCENT_VOLUME to configuration.tone.accentVolumePercent,
        WINDOW to configuration.tone.window.name,
        NOTCH_ENABLED to configuration.notch.enabled,
        NOTCH_CENTER to configuration.notch.centerFrequencyHz,
        NOTCH_Q to configuration.notch.qFactor.toFloat(),
        NOTCH_LINKED to configuration.notch.centerLinkedToTone,
    )

    fun decode(values: Map<String, *>): MetronomeConfiguration = runCatching {
        if (values.isEmpty()) return MetronomeConfiguration.DEFAULT
        val version = values[VERSION] as? Int ?: return MetronomeConfiguration.DEFAULT
        if (version != MetronomeConfiguration.CURRENT_VERSION) {
            return MetronomeConfiguration.DEFAULT
        }
        MetronomeConfiguration(
            tone = MetronomeToneConfiguration(
                frequencyHz = values[TONE_FREQUENCY] as? Int
                    ?: return MetronomeConfiguration.DEFAULT,
                durationMillis = values[TONE_DURATION] as? Int
                    ?: return MetronomeConfiguration.DEFAULT,
                normalVolumePercent = values[NORMAL_VOLUME] as? Int
                    ?: return MetronomeConfiguration.DEFAULT,
                accentVolumePercent = values[ACCENT_VOLUME] as? Int
                    ?: return MetronomeConfiguration.DEFAULT,
                window = MetronomeWindow.valueOf(
                    values[WINDOW] as? String ?: return MetronomeConfiguration.DEFAULT,
                ),
            ),
            notch = MetronomeNotchConfiguration(
                enabled = values[NOTCH_ENABLED] as? Boolean
                    ?: return MetronomeConfiguration.DEFAULT,
                centerFrequencyHz = values[NOTCH_CENTER] as? Int
                    ?: return MetronomeConfiguration.DEFAULT,
                qFactor = (values[NOTCH_Q] as? Float)?.toDouble()
                    ?: return MetronomeConfiguration.DEFAULT,
                centerLinkedToTone = values[NOTCH_LINKED] as? Boolean
                    ?: return MetronomeConfiguration.DEFAULT,
            ),
            version = version,
        )
    }.getOrDefault(MetronomeConfiguration.DEFAULT)
}
