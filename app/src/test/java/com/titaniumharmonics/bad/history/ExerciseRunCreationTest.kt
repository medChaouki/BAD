package com.titaniumharmonics.bad.history

import com.titaniumharmonics.bad.history.persistence.exerciseRunFixture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseRunCreationTest {
    @Test
    fun factoryPreservesTheCompletedResultAndGraphExactly() {
        val source = exerciseRunFixture()

        val mapped = ExerciseRunFactory("7.2-test").create(
            runId = "stable-id",
            startedAtEpochMillis = 10L,
            completedAtEpochMillis = 20L,
            practiceResult = source.practiceResult,
            productionGraph = source.productionGraph,
        )

        assertEquals("stable-id", mapped.runId)
        assertEquals(10L, mapped.startedAtEpochMillis)
        assertEquals(20L, mapped.completedAtEpochMillis)
        assertEquals("7.2-test", mapped.appVersion)
        assertSame(source.practiceResult, mapped.practiceResult)
        assertSame(source.productionGraph, mapped.productionGraph)
        assertSame(source.runtimeExerciseSnapshot, mapped.runtimeExerciseSnapshot)
        assertEquals(source.detectionSnapshot, mapped.detectionSnapshot)
        assertEquals(source.metronomeSnapshot, mapped.metronomeSnapshot)
        assertEquals(source.judgementSnapshot, mapped.judgementSnapshot)
        assertEquals(source.calibrationMetadata, mapped.calibrationMetadata)
    }

    @Test
    fun noCompletedCandidateDoesNotSave() = runBlocking {
        val repository = FakeRepository()
        val coordinator = ExerciseRunSaveCoordinator(repository)

        assertEquals(ExerciseRunSaveState.NotSaved, coordinator.save())
        assertEquals(0, repository.saveCalls)
    }

    @Test
    fun successfulRunSavesExactlyOnceAndNewAttemptUsesItsOwnId() = runBlocking {
        val repository = FakeRepository()
        val coordinator = ExerciseRunSaveCoordinator(repository)
        val first = exerciseRunFixture(runId = "first")
        val second = exerciseRunFixture(runId = "second")

        assertEquals(ExerciseRunSaveState.NotSaved, coordinator.stage(first))
        assertEquals(ExerciseRunSaveState.Saved("first"), coordinator.save())
        assertEquals(ExerciseRunSaveState.Saved("first"), coordinator.save())
        coordinator.stage(first)
        assertEquals(ExerciseRunSaveState.Saved("first"), coordinator.save())
        assertEquals(listOf("first"), repository.savedIds)

        coordinator.stage(second)
        assertEquals(ExerciseRunSaveState.Saved("second"), coordinator.save())
        assertEquals(listOf("first", "second"), repository.savedIds)
    }

    @Test
    fun failedSaveKeepsCandidateAndRetryUsesSameRunId() = runBlocking {
        val repository = FakeRepository(
            responses = ArrayDeque(
                listOf(
                    ExerciseRunSaveResult.Failed(
                        ExerciseRunPersistenceError.SaveFailure("retry-id"),
                    ),
                    ExerciseRunSaveResult.Saved,
                ),
            ),
        )
        val coordinator = ExerciseRunSaveCoordinator(repository)
        coordinator.stage(exerciseRunFixture(runId = "retry-id"))

        val failed = coordinator.save()
        assertTrue(failed is ExerciseRunSaveState.SaveFailed)
        assertEquals(ExerciseRunSaveState.Saved("retry-id"), coordinator.save())
        assertEquals(listOf("retry-id", "retry-id"), repository.savedIds)
    }

    @Test
    fun savingStateIsObservableBeforePersistenceCompletes() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val repository = FakeRepository(beforeSaveReturns = { release.await() })
        val coordinator = ExerciseRunSaveCoordinator(repository)
        coordinator.stage(exerciseRunFixture(runId = "in-flight"))

        val saving = async { coordinator.save() }
        while (repository.saveCalls == 0) kotlinx.coroutines.yield()
        assertEquals(ExerciseRunSaveState.Saving("in-flight"), coordinator.state)

        release.complete(Unit)
        assertEquals(ExerciseRunSaveState.Saved("in-flight"), saving.await())
    }

    @Test
    fun wavCleanupRequiresSuccessfulPersistenceAndDisabledDebugRetention() {
        val releasePolicy = ExerciseRunWavPolicy(debugRetentionEnabled = false)
        val debugPolicy = ExerciseRunWavPolicy(debugRetentionEnabled = true)

        assertTrue(releasePolicy.shouldDelete(persistenceSucceeded = true))
        assertTrue(!releasePolicy.shouldDelete(persistenceSucceeded = false))
        assertTrue(!debugPolicy.shouldDelete(persistenceSucceeded = true))
    }

    private class FakeRepository(
        private val responses: ArrayDeque<ExerciseRunSaveResult> = ArrayDeque(),
        private val beforeSaveReturns: suspend () -> Unit = {},
    ) : ExerciseRunRepository {
        val savedIds = mutableListOf<String>()
        val saveCalls: Int get() = savedIds.size

        override suspend fun saveRun(run: ExerciseRun): ExerciseRunSaveResult {
            savedIds += run.runId
            beforeSaveReturns()
            return if (responses.isEmpty()) ExerciseRunSaveResult.Saved else responses.removeFirst()
        }

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
        ): Flow<List<ExerciseRunSummary>> = flowOf(emptyList())

        override fun observeAllRunSummaries(): Flow<List<ExerciseRunSummary>> = flowOf(emptyList())

        override suspend fun deleteRun(runId: String): ExerciseRunDeleteResult =
            ExerciseRunDeleteResult.Deleted

        override suspend fun deleteRunsForExercise(exerciseId: String): ExerciseRunDeleteResult =
            ExerciseRunDeleteResult.Deleted
    }
}
