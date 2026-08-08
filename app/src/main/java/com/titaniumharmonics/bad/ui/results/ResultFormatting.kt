package com.titaniumharmonics.bad.ui.results

import java.util.Locale
import kotlin.math.abs

internal fun formatPercent(value: Double): String =
    "${(value.coerceIn(0.0, 1.0) * 100.0).toInt()}%"

internal fun formatOptionalMillis(value: Double?): String =
    value?.let { String.format(Locale.ROOT, "%.1f ms", it) } ?: "—"

internal fun formatSignedMillis(value: Double): String =
    String.format(Locale.ROOT, "%+.1f ms", value)

internal fun formatBias(value: Double?): String = when {
    value == null -> "—"
    abs(value) < 0.05 -> "Neutral"
    value < 0.0 -> String.format(Locale.ROOT, "%.1f ms early", abs(value))
    else -> String.format(Locale.ROOT, "%.1f ms late", value)
}

internal fun formatSeconds(valueMillis: Double): String =
    String.format(Locale.ROOT, "%.3f s", valueMillis / 1_000.0)

