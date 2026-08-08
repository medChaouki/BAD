package com.titaniumharmonics.bad.ui.history

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.titaniumharmonics.bad.exercise.ExerciseLibraryItem
import com.titaniumharmonics.bad.exercise.ExerciseLibraryRepository
import com.titaniumharmonics.bad.history.ExerciseRun
import com.titaniumharmonics.bad.history.ExerciseRunCollection
import com.titaniumharmonics.bad.history.ExerciseRunDeleteResult
import com.titaniumharmonics.bad.history.ExerciseRunLoadResult
import com.titaniumharmonics.bad.history.ExerciseRunPersistenceError
import com.titaniumharmonics.bad.history.ExerciseRunRepository
import com.titaniumharmonics.bad.history.ExerciseRunSaveResult
import com.titaniumharmonics.bad.history.ExerciseRunSummary
import com.titaniumharmonics.bad.history.ExerciseRunSummaryCollection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseHistoryViewModelInstrumentedTest {
    @Test
    fun observationFilteringConfirmationFailureAndReactiveDeletionRemainDomainSafe() = runBlocking {
        val repository = FakeRunRepository(
            listOf(summary("new", 2L, 120.0), summary("old", 1L, 80.0)),
        )
        lateinit var viewModel: ExerciseHistoryViewModel
        onMain {
            viewModel = ExerciseHistoryViewModel(
                exerciseId = "exercise",
                runRepository = repository,
                exerciseLibraryRepository = FakeExerciseRepository,
            )
        }
        var ready = awaitReady(viewModel) { it.totalRunCount == 2 }
        assertEquals("Current exercise", ready.exerciseName)
        assertEquals(listOf("new", "old"), ready.runs.map { it.runId })

        onMain { viewModel.selectBpm(80.0) }
        ready = awaitReady(viewModel) { it.selectedBpm == 80.0 }
        assertEquals(listOf("old"), ready.runs.map { it.runId })
        onMain { viewModel.selectBpm(null) }

        onMain {
            viewModel.requestRunDeletion("new")
            viewModel.cancelRunDeletion()
        }
        assertEquals(0, repository.deleteCalls)
        assertNull(awaitReady(viewModel) { it.runPendingDeletion == null }.runPendingDeletion)

        repository.nextDeleteResult = ExerciseRunDeleteResult.Failed(
            ExerciseRunPersistenceError.DeleteFailure("new"),
        )
        onMain {
            viewModel.requestRunDeletion("new")
            viewModel.confirmRunDeletion()
        }
        ready = awaitReady(viewModel) { it.actionErrorMessage != null }
        assertEquals(1, repository.deleteCalls)
        assertTrue(ready.runs.any { it.runId == "new" })

        repository.nextDeleteResult = ExerciseRunDeleteResult.Deleted
        onMain {
            viewModel.requestRunDeletion("new")
            viewModel.confirmRunDeletion()
        }
        ready = awaitReady(viewModel) { it.totalRunCount == 1 }
        assertEquals(2, repository.deleteCalls)
        assertEquals(listOf("old"), ready.runs.map { it.runId })
    }

    @Test
    fun emptyAndQueryFailureAreDistinctAndOrphansUseTheSnapshotName() = runBlocking {
        val repository = FakeRunRepository(emptyList())
        lateinit var viewModel: ExerciseHistoryViewModel
        onMain {
            viewModel = ExerciseHistoryViewModel(
                exerciseId = "missing-exercise",
                runRepository = repository,
                exerciseLibraryRepository = EmptyExerciseRepository,
            )
        }
        val empty = withTimeout(5_000L) {
            viewModel.uiState.filterIsInstance<ExerciseHistoryUiState.Empty>().first()
        }
        assertEquals("Exercise history", empty.exerciseName)

        repository.summaries.value = ExerciseRunSummaryCollection(
            listOf(summary("orphan", 3L, 90.0).copy(exerciseNameSnapshot = "Deleted exercise")),
        )
        assertEquals("Deleted exercise", awaitReady(viewModel) { it.totalRunCount == 1 }.exerciseName)

        repository.summaries.value = ExerciseRunSummaryCollection(
            emptyList(),
            ExerciseRunPersistenceError.LoadFailure(null),
        )
        val error = withTimeout(5_000L) {
            viewModel.uiState.filterIsInstance<ExerciseHistoryUiState.Error>().first()
        }
        assertEquals("Saved-run history could not be loaded.", error.message)
    }

    private suspend fun awaitReady(
        viewModel: ExerciseHistoryViewModel,
        predicate: (ExerciseHistoryUiState.Ready) -> Boolean,
    ): ExerciseHistoryUiState.Ready = withTimeout(5_000L) {
        viewModel.uiState.filterIsInstance<ExerciseHistoryUiState.Ready>().first(predicate)
    }

    private fun onMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private class FakeRunRepository(initial: List<ExerciseRunSummary>) : ExerciseRunRepository {
        val summaries = MutableStateFlow(ExerciseRunSummaryCollection(initial))
        var deleteCalls = 0
        var nextDeleteResult: ExerciseRunDeleteResult = ExerciseRunDeleteResult.Deleted

        override suspend fun saveRun(run: ExerciseRun) = ExerciseRunSaveResult.Saved
        override suspend fun getRun(runId: String): ExerciseRunLoadResult =
            ExerciseRunLoadResult.Failed(ExerciseRunPersistenceError.MissingRun(runId))
        override fun observeRun(runId: String): Flow<ExerciseRunLoadResult> =
            flowOf(ExerciseRunLoadResult.Failed(ExerciseRunPersistenceError.MissingRun(runId)))
        override fun observeRunsForExercise(exerciseId: String): Flow<ExerciseRunCollection> =
            flowOf(ExerciseRunCollection(emptyList()))
        override fun observeAllRuns(): Flow<ExerciseRunCollection> =
            flowOf(ExerciseRunCollection(emptyList()))
        override fun observeRunSummariesForExercise(
            exerciseId: String,
        ): Flow<ExerciseRunSummaryCollection> = summaries
        override fun observeAllRunSummaries(): Flow<ExerciseRunSummaryCollection> = summaries
        override suspend fun deleteRun(runId: String): ExerciseRunDeleteResult {
            deleteCalls += 1
            val result = nextDeleteResult
            if (result == ExerciseRunDeleteResult.Deleted) {
                summaries.value = ExerciseRunSummaryCollection(
                    summaries.value.summaries.filterNot { it.runId == runId },
                )
            }
            return result
        }
        override suspend fun deleteRunsForExercise(exerciseId: String) =
            ExerciseRunDeleteResult.Deleted
    }

    private object FakeExerciseRepository : ExerciseLibraryRepository {
        override fun loadExercises() = listOf(
            ExerciseLibraryItem(
                exerciseId = "exercise",
                documentUri = "content://exercise",
                fileName = "exercise.bad.json",
                exerciseName = "Current exercise",
                tempoBpm = 120.0,
                patternCount = 1,
                expandedMeasureCount = 1,
            ),
        )
        override fun deleteExercise(documentUri: String) = Unit
    }

    private object EmptyExerciseRepository : ExerciseLibraryRepository {
        override fun loadExercises(): List<ExerciseLibraryItem> = emptyList()
        override fun deleteExercise(documentUri: String) = Unit
    }

    private fun summary(runId: String, completed: Long, bpm: Double) = ExerciseRunSummary(
        runId = runId,
        exerciseId = "exercise",
        exerciseNameSnapshot = "Snapshot exercise",
        startedAtEpochMillis = completed,
        completedAtEpochMillis = completed,
        bpm = bpm,
        exerciseDurationMillis = 1_000.0,
        expandedMeasureCount = 1,
        totalExpectedNotes = 4,
        accuracy = 0.8,
        hitRate = 0.9,
        meanAbsoluteTimingErrorMillis = 20.0,
        signedMeanTimingErrorMillis = -4.0,
        missedCount = 1,
        extraCount = 0,
        schemaVersion = 1,
        appVersion = "test",
    )
}
