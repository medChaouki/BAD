package com.titaniumharmonics.bad.ui.history

import com.titaniumharmonics.bad.history.ExerciseRunSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun availableHistoryBpms(summaries: List<ExerciseRunSummary>): List<Double> =
    summaries.asSequence()
        .map(ExerciseRunSummary::bpm)
        .filter { it.isFinite() && it > 0.0 }
        .distinctBy(Double::toBits)
        .sorted()
        .toList()

internal fun presentHistoryRuns(
    summaries: List<ExerciseRunSummary>,
    selectedBpm: Double?,
    sortMode: ExerciseHistorySortMode,
): List<ExerciseRunSummary> {
    val filtered = if (selectedBpm == null) {
        summaries
    } else {
        summaries.filter { it.bpm.toBits() == selectedBpm.toBits() }
    }
    return filtered.sortedWith(sortMode.comparator())
}

private fun ExerciseHistorySortMode.comparator(): Comparator<ExerciseRunSummary> {
    val newestTieBreak = compareByDescending<ExerciseRunSummary> {
        it.completedAtEpochMillis
    }.thenByDescending(ExerciseRunSummary::runId)
    return when (this) {
        ExerciseHistorySortMode.NEWEST_FIRST -> newestTieBreak
        ExerciseHistorySortMode.OLDEST_FIRST ->
            compareBy<ExerciseRunSummary> { it.completedAtEpochMillis }
                .thenByDescending(ExerciseRunSummary::runId)
        ExerciseHistorySortMode.BEST_ACCURACY ->
            compareBy<ExerciseRunSummary> { !it.accuracy.isFinite() }
                .thenByDescending { it.accuracy.takeIf(Double::isFinite) }
                .then(newestTieBreak)
        ExerciseHistorySortMode.LOWEST_TIMING_ERROR ->
            compareBy<ExerciseRunSummary> {
                it.meanAbsoluteTimingErrorMillis?.isFinite() != true
            }.thenBy {
                it.meanAbsoluteTimingErrorMillis?.takeIf(Double::isFinite)
            }.then(newestTieBreak)
    }
}

internal fun formatHistoryTimestamp(
    epochMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = runCatching {
    DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", locale)
        .withZone(zoneId)
        .format(Instant.ofEpochMilli(epochMillis))
}.getOrDefault("Date unavailable")

internal fun formatHistoryBpm(bpm: Double): String = when {
    !bpm.isFinite() || bpm <= 0.0 -> "— BPM"
    bpm % 1.0 == 0.0 -> "${bpm.toLong()} BPM"
    else -> "${"%.1f".format(Locale.ROOT, bpm)} BPM"
}

internal fun formatHistoryPercentage(value: Double): String =
    if (value.isFinite()) "${(value.coerceIn(0.0, 1.0) * 100.0).roundToInt()}%" else "—"

internal fun formatHistoryTimingError(value: Double?): String =
    value?.takeIf(Double::isFinite)?.let { "${abs(it).roundToInt()} ms" } ?: "—"

internal fun formatHistoryBias(value: Double?): String {
    val finite = value?.takeIf(Double::isFinite) ?: return "Bias unavailable"
    val rounded = abs(finite).roundToInt()
    return when {
        rounded == 0 -> "Balanced"
        finite < 0.0 -> "$rounded ms early"
        else -> "$rounded ms late"
    }
}
