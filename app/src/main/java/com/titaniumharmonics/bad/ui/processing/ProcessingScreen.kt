package com.titaniumharmonics.bad.ui.processing

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.titaniumharmonics.bad.audio.result.PracticeResultState
import com.titaniumharmonics.bad.audio.analysis.AudioAnalysisState
import com.titaniumharmonics.bad.audio.result.PracticeVerdict
import com.titaniumharmonics.bad.audio.result.ProductionGraphModel
import com.titaniumharmonics.bad.audio.result.PracticeResult
import com.titaniumharmonics.bad.ui.practice.PracticeUiState
import kotlinx.coroutines.delay

@Composable
fun ProcessingRoute(
    uiState: PracticeUiState,
    onResultsReady: (PracticeResult, ProductionGraphModel) -> Unit,
    onRetry: () -> Unit,
) {
    var enteredAtMillis by rememberSaveable { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var measuringShownAtMillis by rememberSaveable { mutableLongStateOf(0L) }
    var verdictShownAtMillis by rememberSaveable { mutableLongStateOf(0L) }
    val ready = uiState.practiceResult as? PracticeResultState.Ready
    val verdict = uiState.practiceVerdict

    LaunchedEffect(uiState.audioAnalysis, measuringShownAtMillis) {
        if (
            uiState.audioAnalysis is AudioAnalysisState.Ready &&
            measuringShownAtMillis == 0L
        ) {
            delay(remainingDelayMillis(
                startedAtMillis = enteredAtMillis,
                durationMillis = MINIMUM_STAGE_VISIBILITY_MILLIS,
                nowMillis = SystemClock.elapsedRealtime(),
            ))
            measuringShownAtMillis = SystemClock.elapsedRealtime()
        }
    }
    LaunchedEffect(ready, verdict, measuringShownAtMillis) {
        if (
            ready != null && verdict != null &&
            measuringShownAtMillis > 0L && verdictShownAtMillis == 0L
        ) {
            delay(remainingDelayMillis(
                startedAtMillis = measuringShownAtMillis,
                durationMillis = MINIMUM_STAGE_VISIBILITY_MILLIS,
                nowMillis = SystemClock.elapsedRealtime(),
            ))
            verdictShownAtMillis = SystemClock.elapsedRealtime()
        }
    }
    LaunchedEffect(ready, verdictShownAtMillis) {
        if (ready != null && verdictShownAtMillis > 0L) {
            delay(remainingDelayMillis(
                startedAtMillis = verdictShownAtMillis,
                durationMillis = VERDICT_DURATION_MILLIS,
                nowMillis = SystemClock.elapsedRealtime(),
            ))
            onResultsReady(ready.result, ready.graphModel)
        }
    }

    val derivedPresentation = uiState.processingPresentation(verdictShownAtMillis > 0L)
    val presentation = if (
        measuringShownAtMillis == 0L && derivedPresentation.stage == ProcessingStage.MEASURING
    ) {
        ProcessingPresentation(ProcessingStage.WEIGHING)
    } else {
        derivedPresentation
    }
    ProcessingScreen(
        presentation = presentation,
        onRetry = {
            enteredAtMillis = SystemClock.elapsedRealtime()
            measuringShownAtMillis = 0L
            verdictShownAtMillis = 0L
            onRetry()
        },
    )
}

@Composable
fun ProcessingScreen(
    presentation: ProcessingPresentation,
    onRetry: () -> Unit,
) {
    var dotCount by remember { mutableIntStateOf(1) }
    val loading = presentation.stage in setOf(
        ProcessingStage.WEIGHING,
        ProcessingStage.MEASURING,
    )
    LaunchedEffect(loading) {
        if (loading) {
            while (true) {
                delay(DOT_INTERVAL_MILLIS)
                dotCount = dotCount % 3 + 1
            }
        }
    }

    val description = when (presentation.stage) {
        ProcessingStage.WEIGHING -> "Processing audio"
        ProcessingStage.MEASURING -> "Measuring performance"
        ProcessingStage.VERDICT -> "Final verdict: ${presentation.verdict.displayText()}"
        ProcessingStage.FAILED -> "Processing failed. Double tap to retry."
    }
    val interaction = if (presentation.stage == ProcessingStage.FAILED) {
        Modifier.clickable(onClick = onRetry)
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(interaction)
            .semantics {
                contentDescription = description
                liveRegion = if (presentation.stage == ProcessingStage.VERDICT) {
                    LiveRegionMode.Assertive
                } else {
                    LiveRegionMode.Polite
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            when (presentation.stage) {
                ProcessingStage.WEIGHING -> ProcessingText("YOU HAVE BEEN WEIGHED", dotCount)
                ProcessingStage.MEASURING -> ProcessingText("YOU HAVE BEEN MEASURED", dotCount)
                ProcessingStage.VERDICT -> {
                    Text(
                        "AND YOU HAVE BEEN FOUND...",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        presentation.verdict.displayText(),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                ProcessingStage.FAILED -> {
                    Text(
                        "PROCESSING FAILED\n\nTAP TO RETRY",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessingText(text: String, dotCount: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = text,
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = ".".repeat(dotCount),
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun PracticeVerdict?.displayText(): String = when (this) {
    PracticeVerdict.EARLY -> "EARLY"
    PracticeVerdict.ON_TIME -> "ON TIME"
    PracticeVerdict.LATE -> "LATE"
    PracticeVerdict.MISSING -> "MISSING"
    null -> ""
}

internal const val MINIMUM_STAGE_VISIBILITY_MILLIS = 1_500L
internal const val VERDICT_DURATION_MILLIS = 900L
internal const val DOT_INTERVAL_MILLIS = 225L

internal fun remainingDelayMillis(
    startedAtMillis: Long,
    durationMillis: Long,
    nowMillis: Long,
): Long = (durationMillis - (nowMillis - startedAtMillis)).coerceAtLeast(0L)
