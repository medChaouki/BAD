package com.titaniumharmonics.bad.audio.matching

interface JudgementConfigurationStore {
    fun load(): Map<String, *>
    fun save(values: Map<String, Any>)
    fun reset()
}

class JudgementConfigurationRepository(
    private val store: JudgementConfigurationStore,
) {
    fun load(): JudgementConfiguration = JudgementConfigurationCodec.decode(store.load())

    fun save(configuration: JudgementConfiguration) {
        store.save(JudgementConfigurationCodec.encode(configuration))
    }

    fun reset(): JudgementConfiguration {
        store.reset()
        return JudgementConfiguration.DEFAULT
    }
}

object JudgementConfigurationCodec {
    fun encode(value: JudgementConfiguration): Map<String, Any> = mapOf(
        "version" to value.version,
        "on_time_before_ms" to value.onTimeBeforeMillis.toString(),
        "on_time_after_ms" to value.onTimeAfterMillis.toString(),
        "maximum_early_ms" to value.maximumEarlyMillis.toString(),
        "maximum_late_ms" to value.maximumLateMillis.toString(),
        "minimum_confidence" to value.minimumDetectedHitConfidence.toString(),
        "extra_hit_handling_enabled" to value.extraHitHandlingEnabled,
    )

    fun decode(values: Map<String, *>): JudgementConfiguration = runCatching {
        if (values.isEmpty()) return JudgementConfiguration.DEFAULT
        val version = values["version"] as? Int ?: return JudgementConfiguration.DEFAULT
        if (version != JudgementConfiguration.CURRENT_VERSION) {
            return JudgementConfiguration.DEFAULT
        }
        JudgementConfiguration(
            onTimeBeforeMillis = values.double("on_time_before_ms"),
            onTimeAfterMillis = values.double("on_time_after_ms"),
            maximumEarlyMillis = values.double("maximum_early_ms"),
            maximumLateMillis = values.double("maximum_late_ms"),
            minimumDetectedHitConfidence = values.double("minimum_confidence"),
            extraHitHandlingEnabled = values.boolean("extra_hit_handling_enabled"),
            version = version,
        )
    }.getOrDefault(JudgementConfiguration.DEFAULT)

    private fun Map<String, *>.double(key: String): Double = when (val value = this[key]) {
        is String -> value.toDouble()
        is Float -> value.toDouble()
        else -> error("Missing $key.")
    }

    private fun Map<String, *>.boolean(key: String): Boolean =
        this[key] as? Boolean ?: error("Missing $key.")
}
