package com.titaniumharmonics.bad.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.titaniumharmonics.bad.audio.result.PracticeResult

@Composable
internal fun ResultsSummary(result: PracticeResult, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryMetric("Accuracy", formatPercent(result.accuracy), Modifier.weight(1f))
            SummaryMetric("Hit rate", formatPercent(result.hitRate), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryMetric(
                "Mean timing error",
                formatOptionalMillis(result.meanAbsoluteTimingErrorMillis),
                Modifier.weight(1f),
            )
            SummaryMetric("Bias", formatBias(result.signedMeanTimingErrorMillis), Modifier.weight(1f))
        }
        Text(
            "Accuracy counts on-time notes; hit rate counts every matched note.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CountLabel("Early", result.earlyCount, Color(0xFF3F7DFF))
            CountLabel("On Time", result.onTimeCount, Color(0xFF2E9D55))
            CountLabel("Late", result.lateCount, MaterialTheme.colorScheme.error)
            CountLabel("Missed", result.missedCount, Color.Gray)
            CountLabel("Extra", result.extraCount, Color(0xFFB3261E))
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun CountLabel(label: String, count: Int, color: Color) {
    Column {
        Text(count.toString(), style = MaterialTheme.typography.titleMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
