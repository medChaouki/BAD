package com.titaniumharmonics.bad.ui.results

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.titaniumharmonics.bad.audio.matching.HitJudgement
import com.titaniumharmonics.bad.audio.result.ProductionGraphModel
import kotlin.math.abs

@Composable
fun ProductionResultGraph(
    model: ProductionGraphModel,
    modifier: Modifier = Modifier,
) {
    var viewport by remember(model) {
        mutableStateOf(ProductionGraphViewport.full(model.exerciseDurationSamples))
    }
    var selection by remember(model) { mutableStateOf<ProductionGraphSelection?>(null) }
    val earlyColor = Color(0xFF3F7DFF)
    val onTimeColor = Color(0xFF2E9D55)
    val lateColor = MaterialTheme.colorScheme.error
    val missedColor = Color.Gray
    val extraColor = Color(0xFFB3261E)
    val expectedColor = MaterialTheme.colorScheme.onSurface
    val envelopeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    val guideColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Performance timeline", style = MaterialTheme.typography.titleMedium)
        Text(
            "│ Expected · ▲ Early · ● On Time · ■ Late · Gray X Missed · Red X Extra",
            style = MaterialTheme.typography.labelSmall,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .semantics {
                    contentDescription = productionGraphContentDescription(model)
                }
                .pointerInput(model, viewport) {
                    detectTapGestures { tap ->
                        val tappedSample = viewport.sampleAtFraction(tap.x / size.width)
                        val tolerance = (viewport.spanSamples * 22.0 / size.width)
                            .toLong().coerceAtLeast(1L)
                        selection = ProductionGraphSelectionResolver.nearest(
                            model,
                            tappedSample,
                            tolerance,
                        )
                    }
                },
        ) {
            val plotTop = 18.dp.toPx()
            val plotBottom = size.height - 42.dp.toPx()
            val markerY = plotTop + 32.dp.toPx()
            val extraY = plotTop + 64.dp.toPx()
            fun x(sample: Long): Float =
                ((sample - viewport.startSample).toDouble() / viewport.spanSamples)
                    .coerceIn(0.0, 1.0).toFloat() * size.width
            fun visible(sample: Long): Boolean = sample in viewport.startSample..viewport.endSample
            fun markerVisible(sample: Long): Boolean = when {
                sample < 0L -> viewport.startSample == 0L
                sample > model.exerciseDurationSamples ->
                    viewport.endSample >= model.exerciseDurationSamples.coerceAtLeast(1L)
                else -> visible(sample)
            }

            model.measureGuides.filter { visible(it.exerciseSample) }.forEach { guide ->
                val guideX = x(guide.exerciseSample)
                drawLine(
                    guideColor,
                    Offset(guideX, 0f),
                    Offset(guideX, size.height),
                    1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 7f)),
                )
            }

            val envelopePath = Path()
            model.envelopePoints.filter { visible(it.exerciseSample) }.forEachIndexed { index, point ->
                val pointX = x(point.exerciseSample)
                val normalized = (point.amplitude / model.maximumEnvelopeAmplitude)
                    .coerceIn(0f, 1f)
                val pointY = plotBottom - normalized * (plotBottom - plotTop)
                if (index == 0) envelopePath.moveTo(pointX, pointY) else
                    envelopePath.lineTo(pointX, pointY)
            }
            drawPath(envelopePath, envelopeColor, style = Stroke(2.dp.toPx()))

            model.timingConnectors.forEach { connector ->
                if (!markerVisible(connector.expectedExerciseSample) &&
                    !markerVisible(connector.calibratedActualSample)
                ) return@forEach
                val expectedX = x(connector.expectedExerciseSample)
                val actualX = x(connector.calibratedActualSample)
                val color = when {
                    connector.timingErrorSamples < 0L -> earlyColor
                    connector.timingErrorSamples > 0L -> lateColor
                    else -> onTimeColor
                }
                if (abs(actualX - expectedX) > 2.dp.toPx()) {
                    drawLine(color.copy(alpha = 0.7f), Offset(expectedX, markerY), Offset(actualX, markerY), 2.dp.toPx())
                    drawArrowHead(actualX, markerY, actualX >= expectedX, color)
                }
            }

            model.expectedNotes.filter { visible(it.exerciseSample) }.forEach { note ->
                val expectedX = x(note.exerciseSample)
                drawLine(expectedColor, Offset(expectedX, markerY - 15.dp.toPx()), Offset(expectedX, markerY + 15.dp.toPx()), 2.dp.toPx())
            }
            model.matchedHits.filter { markerVisible(it.calibratedExerciseSample) }.forEach { hit ->
                val hitX = x(hit.calibratedExerciseSample)
                val radius = (4.5 + hit.relativeIntensity * 4.0).dp.toPx()
                when (hit.judgement) {
                    HitJudgement.EARLY -> drawTriangle(Offset(hitX, markerY), radius, earlyColor)
                    HitJudgement.ON_TIME -> drawCircle(onTimeColor, radius, Offset(hitX, markerY))
                    HitJudgement.LATE -> drawRect(lateColor, Offset(hitX - radius, markerY - radius), androidx.compose.ui.geometry.Size(radius * 2, radius * 2))
                    HitJudgement.MISSED -> Unit
                }
            }
            model.missedNotes.filter { visible(it.exerciseSample) }.forEach { note ->
                drawX(Offset(x(note.exerciseSample), markerY), missedColor, 7.dp.toPx())
            }
            model.extraHits.filter { markerVisible(it.calibratedExerciseSample) }.forEach { hit ->
                val radius = (5.0 + hit.relativeIntensity * 3.0).dp.toPx()
                drawX(Offset(x(hit.calibratedExerciseSample), extraY), extraColor, radius)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatAxisTime(viewport.startSample, model.sampleRateHz), style = MaterialTheme.typography.labelSmall)
            Text(formatAxisTime((viewport.startSample + viewport.endSample) / 2L, model.sampleRateHz), style = MaterialTheme.typography.labelSmall)
            Text(formatAxisTime(viewport.endSample, model.sampleRateHz), style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { viewport = viewport.zoomIn(model.exerciseDurationSamples) }, modifier = Modifier.weight(1f)) { Text("+") }
            OutlinedButton(onClick = { viewport = viewport.pan(model.exerciseDurationSamples, -1) }, modifier = Modifier.weight(1f)) { Text("←") }
            OutlinedButton(onClick = { viewport = viewport.pan(model.exerciseDurationSamples, 1) }, modifier = Modifier.weight(1f)) { Text("→") }
            OutlinedButton(onClick = { viewport = viewport.zoomOut(model.exerciseDurationSamples) }, modifier = Modifier.weight(1f)) { Text("−") }
            OutlinedButton(onClick = { viewport = ProductionGraphViewport.full(model.exerciseDurationSamples) }, modifier = Modifier.weight(1.4f)) { Text("Fit") }
        }
        selection?.let { SelectedGraphItem(model, it) }
    }
}

@Composable
private fun SelectedGraphItem(model: ProductionGraphModel, selection: ProductionGraphSelection) {
    Card(Modifier.fillMaxWidth().semantics { contentDescription = "Selected performance event details" }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            when (selection) {
                is ProductionGraphSelection.MatchedNote -> {
                    val expected = model.expectedNotes.first { it.expectedNoteIndex == selection.expectedNoteIndex }
                    val actual = model.matchedHits.first { it.expectedNoteIndex == selection.expectedNoteIndex }
                    val connector = model.timingConnectors.first { it.expectedNoteIndex == selection.expectedNoteIndex }
                    Text("Measure ${expected.measureIndex + 1} · Beat ${formatGraphBeat(expected.beatPosition)}", style = MaterialTheme.typography.titleSmall)
                    Text("Expected: ${formatSeconds(expected.exerciseTimeMillis)}")
                    Text("Detected: ${formatSeconds(actual.calibratedExerciseTimeMillis)} (calibrated)")
                    Text("Error: ${formatSignedMillis(connector.timingErrorMillis)}")
                    Text("Result: ${actual.judgement.displayName()}")
                    Text("Intensity: ${formatPercent(actual.relativeIntensity)}")
                    Text("Confidence: ${formatPercent(actual.confidence)}")
                    outsideRangeText(actual.calibratedExerciseSample, model)?.let { Text(it) }
                }
                is ProductionGraphSelection.MissedNote -> {
                    val note = model.missedNotes.first { it.expectedNoteIndex == selection.expectedNoteIndex }
                    Text("Measure ${note.measureIndex + 1} · Beat ${formatGraphBeat(note.beatPosition)}", style = MaterialTheme.typography.titleSmall)
                    Text("Expected: ${formatSeconds(note.exerciseTimeMillis)}")
                    Text("Result: Missed")
                }
                is ProductionGraphSelection.ExtraHit -> {
                    val hit = model.extraHits.first { it.detectedHitIndex == selection.detectedHitIndex }
                    Text("Extra hit", style = MaterialTheme.typography.titleSmall)
                    Text("Detected: ${formatSeconds(hit.calibratedExerciseTimeMillis)} (calibrated)")
                    Text("Intensity: ${formatPercent(hit.relativeIntensity)}")
                    Text("Confidence: ${formatPercent(hit.confidence)}")
                    outsideRangeText(hit.calibratedExerciseSample, model)?.let { Text(it) }
                }
            }
        }
    }
}

private fun DrawScope.drawArrowHead(x: Float, y: Float, pointsRight: Boolean, color: Color) {
    val direction = if (pointsRight) -1f else 1f
    val length = 5.dp.toPx()
    drawLine(color, Offset(x, y), Offset(x + direction * length, y - length), 2.dp.toPx())
    drawLine(color, Offset(x, y), Offset(x + direction * length, y + length), 2.dp.toPx())
}

private fun DrawScope.drawTriangle(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x - radius, center.y + radius)
        lineTo(center.x + radius, center.y + radius)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawX(center: Offset, color: Color, radius: Float) {
    drawLine(color, Offset(center.x - radius, center.y - radius), Offset(center.x + radius, center.y + radius), 3.dp.toPx())
    drawLine(color, Offset(center.x + radius, center.y - radius), Offset(center.x - radius, center.y + radius), 3.dp.toPx())
}

private fun HitJudgement.displayName(): String = when (this) {
    HitJudgement.EARLY -> "Early"
    HitJudgement.ON_TIME -> "On Time"
    HitJudgement.LATE -> "Late"
    HitJudgement.MISSED -> "Missed"
}

private fun formatAxisTime(sample: Long, sampleRateHz: Int): String =
    if (sampleRateHz <= 0) "0 s" else String.format(java.util.Locale.ROOT, "%.2f s", sample.toDouble() / sampleRateHz)

private fun formatGraphBeat(zeroBasedBeat: Double): String =
    String.format(java.util.Locale.ROOT, "%.2f", zeroBasedBeat + 1.0).trimEnd('0').trimEnd('.')

private fun outsideRangeText(sample: Long, model: ProductionGraphModel): String? = when {
    sample < 0L -> "Marker is before the visible exercise range."
    sample > model.exerciseDurationSamples -> "Marker is after the visible exercise range."
    else -> null
}
