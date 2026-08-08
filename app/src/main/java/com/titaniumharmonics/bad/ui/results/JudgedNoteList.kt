package com.titaniumharmonics.bad.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.titaniumharmonics.bad.audio.matching.HitJudgement
import com.titaniumharmonics.bad.audio.result.ExtraHit
import com.titaniumharmonics.bad.audio.result.JudgedNote

@Composable
internal fun JudgedNoteItem(note: JudgedNote, modifier: Modifier = Modifier) {
    val judgementLabel = when (note.judgement) {
        HitJudgement.EARLY -> "Early"
        HitJudgement.ON_TIME -> "On Time"
        HitJudgement.LATE -> "Late"
        HitJudgement.MISSED -> "Missed"
    }
    val semanticColor = when (note.judgement) {
        HitJudgement.EARLY -> Color(0xFF3F7DFF)
        HitJudgement.ON_TIME -> Color(0xFF2E9D55)
        HitJudgement.LATE -> MaterialTheme.colorScheme.error
        HitJudgement.MISSED -> Color.Gray
    }
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "Measure ${note.measureIndex + 1} · Beat ${formatBeat(note.beatPosition)}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(judgementLabel, color = semanticColor, style = MaterialTheme.typography.labelLarge)
            if (note.matchedHit != null) {
                Text("Expected: ${formatSeconds(note.expectedExerciseTimeMillis)}")
                Text("Detected: ${formatSeconds(checkNotNull(note.calibratedDetectedTimeMillis))}")
                Text("Error: ${formatSignedMillis(checkNotNull(note.timingErrorMillis))}")
                Text("Intensity: ${formatPercent(checkNotNull(note.relativeIntensity))}")
                Text("Confidence: ${formatPercent(checkNotNull(note.detectionConfidence))}")
            }
        }
    }
}

@Composable
internal fun ExtraHitItem(hit: ExtraHit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Extra hit", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleSmall)
            Text("Detected: ${formatSeconds(hit.calibratedTimeMillis)}")
            Text("Intensity: ${formatPercent(hit.relativeIntensity)}")
            Text("Confidence: ${formatPercent(hit.confidence)}")
        }
    }
}

private fun formatBeat(zeroBasedBeat: Double): String {
    val oneBased = zeroBasedBeat + 1.0
    return if (oneBased % 1.0 == 0.0) oneBased.toInt().toString() else
        String.format(java.util.Locale.ROOT, "%.2f", oneBased)
}

