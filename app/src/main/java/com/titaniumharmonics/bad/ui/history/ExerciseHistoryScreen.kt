package com.titaniumharmonics.bad.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.titaniumharmonics.bad.history.ExerciseRunSummary

@Composable
fun ExerciseHistoryRoute(
    exerciseId: String,
    onOpenRun: (String) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val factory = remember(context, exerciseId) {
        ExerciseHistoryViewModel.factory(context, exerciseId)
    }
    val viewModel: ExerciseHistoryViewModel = viewModel(
        key = "exercise-history-$exerciseId",
        factory = factory,
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ExerciseHistoryScreen(
        uiState = uiState,
        onOpenRun = onOpenRun,
        onSelectBpm = viewModel::selectBpm,
        onSelectSort = viewModel::selectSortMode,
        onRequestDeletion = viewModel::requestRunDeletion,
        onCancelDeletion = viewModel::cancelRunDeletion,
        onConfirmDeletion = viewModel::confirmRunDeletion,
        onRetryLoad = viewModel::refresh,
        onNavigateBack = onNavigateBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseHistoryScreen(
    uiState: ExerciseHistoryUiState,
    onOpenRun: (String) -> Unit,
    onSelectBpm: (Double?) -> Unit,
    onSelectSort: (ExerciseHistorySortMode) -> Unit,
    onRequestDeletion: (String) -> Unit,
    onCancelDeletion: () -> Unit,
    onConfirmDeletion: () -> Unit,
    onRetryLoad: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(uiState.exerciseName()) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("Back") }
                },
            )
        },
    ) { padding ->
        when (uiState) {
            ExerciseHistoryUiState.Loading -> HistoryCenteredContent(padding) {
                CircularProgressIndicator()
            }
            is ExerciseHistoryUiState.Empty -> HistoryCenteredContent(padding) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No runs yet.", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Complete this exercise to start building history.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is ExerciseHistoryUiState.Error -> HistoryCenteredContent(padding) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(uiState.message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onRetryLoad) { Text("Retry") }
                }
            }
            is ExerciseHistoryUiState.Ready -> HistoryReadyContent(
                state = uiState,
                onOpenRun = onOpenRun,
                onSelectBpm = onSelectBpm,
                onSelectSort = onSelectSort,
                onRequestDeletion = onRequestDeletion,
                modifier = Modifier.padding(padding),
            )
        }
    }

    val ready = uiState as? ExerciseHistoryUiState.Ready
    ready?.runPendingDeletion?.let { pending ->
        AlertDialog(
            onDismissRequest = onCancelDeletion,
            title = { Text("Delete this run?") },
            text = { Text("This removes the saved result permanently.") },
            confirmButton = {
                TextButton(
                    onClick = onConfirmDeletion,
                    enabled = ready.deletingRunId == null,
                    modifier = Modifier.testTag("history-delete-confirm"),
                ) {
                    Text(
                        if (ready.deletingRunId == null) "Delete" else "Deleting…",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onCancelDeletion,
                    enabled = ready.deletingRunId == null,
                ) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun HistoryReadyContent(
    state: ExerciseHistoryUiState.Ready,
    onOpenRun: (String) -> Unit,
    onSelectBpm: (Double?) -> Unit,
    onSelectSort: (ExerciseHistorySortMode) -> Unit,
    onRequestDeletion: (String) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (state.totalRunCount == 1) "1 saved run" else "${state.totalRunCount} saved runs",
            style = MaterialTheme.typography.titleMedium,
        )
        HistoryControls(state, onSelectBpm, onSelectSort)
        state.actionErrorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        if (state.runs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text("No runs match this BPM filter.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.runs, key = ExerciseRunSummary::runId) { run ->
                    ExerciseHistoryRunCard(
                        run = run,
                        deleting = state.deletingRunId == run.runId,
                        onOpen = { onOpenRun(run.runId) },
                        onDelete = { onRequestDeletion(run.runId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryControls(
    state: ExerciseHistoryUiState.Ready,
    onSelectBpm: (Double?) -> Unit,
    onSelectSort: (ExerciseHistorySortMode) -> Unit,
) {
    var bpmExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { bpmExpanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history-bpm-filter"),
            ) {
                Text(state.selectedBpm?.let(::formatHistoryBpm) ?: "All BPM")
            }
            DropdownMenu(
                expanded = bpmExpanded,
                onDismissRequest = { bpmExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("All BPM") },
                    onClick = {
                        bpmExpanded = false
                        onSelectBpm(null)
                    },
                )
                state.availableBpms.forEach { bpm ->
                    DropdownMenuItem(
                        text = { Text(formatHistoryBpm(bpm)) },
                        modifier = Modifier.testTag("history-bpm-${bpm.toBits()}"),
                        onClick = {
                            bpmExpanded = false
                            onSelectBpm(bpm)
                        },
                    )
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { sortExpanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history-sort"),
            ) {
                Text(state.sortMode.displayName)
            }
            DropdownMenu(
                expanded = sortExpanded,
                onDismissRequest = { sortExpanded = false },
            ) {
                ExerciseHistorySortMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.displayName) },
                        modifier = Modifier.testTag("history-sort-${mode.name}"),
                        onClick = {
                            sortExpanded = false
                            onSelectSort(mode)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseHistoryRunCard(
    run: ExerciseRunSummary,
    deleting: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history-run-${run.runId}")
            .clickable(enabled = !deleting, onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatHistoryTimestamp(run.completedAtEpochMillis))
                Text(formatHistoryBpm(run.bpm), fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Accuracy ${formatHistoryPercentage(run.accuracy)} · " +
                    "Hit rate ${formatHistoryPercentage(run.hitRate)}",
            )
            Text(
                "Mean error ${formatHistoryTimingError(run.meanAbsoluteTimingErrorMillis)} · " +
                    formatHistoryBias(run.signedMeanTimingErrorMillis),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Missed ${run.missedCount} · Extra ${run.extraCount}")
                TextButton(
                    onClick = onDelete,
                    enabled = !deleting,
                    modifier = Modifier.testTag("history-delete-${run.runId}"),
                ) {
                    Text(if (deleting) "Deleting…" else "Delete")
                }
            }
        }
    }
}

@Composable
private fun HistoryCenteredContent(
    padding: PaddingValues,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private fun ExerciseHistoryUiState.exerciseName(): String = when (this) {
    ExerciseHistoryUiState.Loading -> "Exercise history"
    is ExerciseHistoryUiState.Ready -> exerciseName
    is ExerciseHistoryUiState.Empty -> exerciseName
    is ExerciseHistoryUiState.Error -> exerciseName
}
