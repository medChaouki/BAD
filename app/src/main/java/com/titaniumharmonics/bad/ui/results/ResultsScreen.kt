package com.titaniumharmonics.bad.ui.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.titaniumharmonics.bad.audio.result.PracticeResult
import com.titaniumharmonics.bad.audio.result.ProductionGraphModel
import com.titaniumharmonics.bad.history.ExerciseRunSaveState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    result: PracticeResult,
    graphModel: ProductionGraphModel,
    showDetails: Boolean,
    onOpenDetails: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    retryEnabled: Boolean = true,
    onReturnToPractice: () -> Unit,
    onReturnToLibrary: () -> Unit,
    saveState: ExerciseRunSaveState? = null,
    onRetrySave: (() -> Unit)? = null,
    onOpenDebug: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (showDetails) "Detailed results" else "Practice results") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        if (showDetails) {
            ResultDetails(result, Modifier.padding(padding))
        } else {
            ResultOverview(
                result = result,
                graphModel = graphModel,
                onOpenDetails = onOpenDetails,
                onRetry = onRetry,
                retryEnabled = retryEnabled,
                onReturnToPractice = onReturnToPractice,
                onReturnToLibrary = onReturnToLibrary,
                saveState = saveState,
                onRetrySave = onRetrySave,
                onOpenDebug = onOpenDebug,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun ResultOverview(
    result: PracticeResult,
    graphModel: ProductionGraphModel,
    onOpenDetails: () -> Unit,
    onRetry: () -> Unit,
    retryEnabled: Boolean,
    onReturnToPractice: () -> Unit,
    onReturnToLibrary: () -> Unit,
    saveState: ExerciseRunSaveState?,
    onRetrySave: (() -> Unit)?,
    onOpenDebug: (() -> Unit)?,
    modifier: Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column {
                Text(result.exerciseName, style = MaterialTheme.typography.headlineSmall)
                Text("${result.bpm.toInt()} BPM · ${result.totalExpectedNotes} expected notes")
            }
        }
        item { ResultsSummary(result) }
        item { ProductionResultGraph(graphModel) }
        saveState?.let { state ->
            item { RunSaveIndicator(state, onRetrySave) }
        }
        item { Button(onClick = onOpenDetails, modifier = Modifier.fillMaxWidth()) { Text("Detailed results") } }
        onOpenDebug?.let { openDebug ->
            item {
                OutlinedButton(onClick = openDebug, modifier = Modifier.fillMaxWidth()) {
                    Text("Open debug analysis")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRetry,
                    enabled = retryEnabled,
                    modifier = Modifier.weight(1f),
                ) { Text("Retry") }
                OutlinedButton(onClick = onReturnToPractice, modifier = Modifier.weight(1f)) { Text("Practice") }
            }
        }
        item { TextButton(onClick = onReturnToLibrary, modifier = Modifier.fillMaxWidth()) { Text("Exercise library") } }
        if (!retryEnabled) {
            item { Text("Source exercise is no longer available.") }
        }
    }
}

@Composable
private fun RunSaveIndicator(
    state: ExerciseRunSaveState,
    onRetrySave: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            when (state) {
                ExerciseRunSaveState.NotSaved -> "Not saved"
                is ExerciseRunSaveState.Saving -> "Saving..."
                is ExerciseRunSaveState.Saved -> "Saved"
                is ExerciseRunSaveState.SaveFailed -> "Save failed"
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        if (state is ExerciseRunSaveState.SaveFailed && onRetrySave != null) {
            TextButton(onClick = onRetrySave) { Text("Retry save") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedRunLoadScreen(
    message: String,
    loading: Boolean,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Practice results") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(if (loading) "Loading saved run..." else message)
        }
    }
}

@Composable
private fun ResultDetails(result: PracticeResult, modifier: Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("Expected notes", style = MaterialTheme.typography.titleLarge) }
        items(result.judgedNotes, key = { "note-${it.expectedNoteIndex}" }) { JudgedNoteItem(it) }
        if (result.extraHits.isNotEmpty()) {
            item { Text("Extra hits", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp)) }
            items(result.extraHits, key = { "extra-${it.detectedHitIndex}" }) { ExtraHitItem(it) }
        }
    }
}
