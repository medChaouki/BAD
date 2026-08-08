package com.titaniumharmonics.bad.history.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.titaniumharmonics.bad.history.ExerciseRunDeleteResult
import com.titaniumharmonics.bad.history.ExerciseRunLoadResult
import com.titaniumharmonics.bad.history.ExerciseRunPersistenceError
import com.titaniumharmonics.bad.history.ExerciseRunSaveResult
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseRunRepositoryInstrumentedTest {
    private lateinit var database: ExerciseRunDatabase
    private lateinit var repository: RoomExerciseRunRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ExerciseRunDatabase::class.java,
        ).build()
        repository = RoomExerciseRunRepository(database.exerciseRunDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveLoadObserveAndSummaryRoundTrip() = runBlocking {
        val run = androidExerciseRunFixture("run-a", "exercise-a", 3_000L)

        assertEquals(ExerciseRunSaveResult.Saved, repository.saveRun(run))
        assertRun(run, (repository.getRun(run.runId) as ExerciseRunLoadResult.Found).run)
        assertRun(
            run,
            (repository.observeRun(run.runId).first() as ExerciseRunLoadResult.Found).run,
        )
        val summary = repository.observeAllRunSummaries().first().single()
        assertEquals(run.runId, summary.runId)
        assertEquals(run.practiceResult.accuracy, summary.accuracy, 0.0)
    }

    @Test
    fun exerciseQueriesAndDeletesAreExplicitlyScoped() = runBlocking {
        val first = androidExerciseRunFixture("run-1", "exercise-a", 1_000L)
        val second = androidExerciseRunFixture("run-2", "exercise-a", 2_000L)
        val other = androidExerciseRunFixture("run-3", "exercise-b", 3_000L)
        listOf(first, second, other).forEach { repository.saveRun(it) }

        assertEquals(
            listOf(second.runId, first.runId),
            repository.observeRunsForExercise("exercise-a").first().runs.map { it.runId },
        )
        assertEquals(ExerciseRunDeleteResult.Deleted, repository.deleteRun(first.runId))
        assertEquals(
            listOf(second.runId, other.runId).sorted(),
            repository.observeAllRuns().first().runs.map { it.runId }.sorted(),
        )
        assertEquals(
            ExerciseRunDeleteResult.Deleted,
            repository.deleteRunsForExercise("exercise-a"),
        )
        assertEquals(listOf(other.runId), repository.observeAllRuns().first().runs.map { it.runId })
    }

    @Test
    fun oneCorruptedRunDoesNotBlockValidRuns() = runBlocking {
        val valid = androidExerciseRunFixture("valid", "exercise-a", 2_000L)
        val corrupt = androidExerciseRunFixture("corrupt", "exercise-a", 1_000L)
        repository.saveRun(valid)
        database.exerciseRunDao().insert(
            ExerciseRunMapper.toEntity(corrupt).copy(payloadJson = "{broken"),
        )

        val collection = repository.observeAllRuns().first()

        assertEquals(listOf(valid.runId), collection.runs.map { it.runId })
        assertTrue(collection.failures.single() is ExerciseRunPersistenceError.CorruptedPayload)
    }

    @Test
    fun identicalSaveIsIdempotentWhileConflictAndLoadErrorsRemainSafe() = runBlocking {
        val run = androidExerciseRunFixture("errors", "exercise-a", 8_000L)
        assertEquals(ExerciseRunSaveResult.Saved, repository.saveRun(run))

        assertEquals(ExerciseRunSaveResult.Saved, repository.saveRun(run))
        val conflict = repository.saveRun(
            androidExerciseRunFixture("errors", "exercise-a", 8_100L),
        ) as ExerciseRunSaveResult.Failed
        val missing = repository.getRun("absent") as ExerciseRunLoadResult.Failed
        val future = androidExerciseRunFixture("future", "exercise-a", 9_000L)
        database.exerciseRunDao().insert(
            ExerciseRunMapper.toEntity(future).copy(runSchemaVersion = 99),
        )
        val unsupported = repository.getRun(future.runId) as ExerciseRunLoadResult.Failed

        assertTrue(conflict.error is ExerciseRunPersistenceError.SaveFailure)
        assertTrue(missing.error is ExerciseRunPersistenceError.MissingRun)
        assertTrue(unsupported.error is ExerciseRunPersistenceError.UnsupportedSchema)
    }

    @Test
    fun persistedRunSurvivesDatabaseRestartAndMutableExternalStateDeletion() = runBlocking {
        database.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "exercise-run-restart-test.db"
        context.deleteDatabase(databaseName)
        val originalExercise = File(context.cacheDir, "exercise-run-source.json")
        val wav = File(context.cacheDir, "exercise-run-source.wav")
        originalExercise.writeText("{\"tempoBpm\":120}")
        wav.writeBytes(byteArrayOf(1, 2, 3))
        val run = androidExerciseRunFixture("restart", "deleted-exercise", 5_000L)

        var diskDatabase = Room.databaseBuilder(
            context, ExerciseRunDatabase::class.java, databaseName,
        ).build()
        var diskRepository = RoomExerciseRunRepository(diskDatabase.exerciseRunDao())
        assertEquals(ExerciseRunSaveResult.Saved, diskRepository.saveRun(run))
        diskDatabase.close()

        context.getSharedPreferences("hit_detection_configuration", Context.MODE_PRIVATE)
            .edit().clear().putInt("version", 999).commit()
        context.getSharedPreferences("metronome_configuration", Context.MODE_PRIVATE)
            .edit().clear().putInt("version", 999).commit()
        context.getSharedPreferences("timing_calibration", Context.MODE_PRIVATE)
            .edit().clear().putInt("algorithm_version", 999).commit()
        context.getSharedPreferences("judgement_configuration", Context.MODE_PRIVATE)
            .edit().clear().putInt("version", 999).commit()
        originalExercise.writeText("{\"tempoBpm\":220}")
        assertTrue(originalExercise.delete())
        assertTrue(wav.delete())

        diskDatabase = Room.databaseBuilder(
            context, ExerciseRunDatabase::class.java, databaseName,
        ).build()
        diskRepository = RoomExerciseRunRepository(diskDatabase.exerciseRunDao())
        val loaded = (diskRepository.getRun(run.runId) as ExerciseRunLoadResult.Found).run

        assertRun(run, loaded)
        assertFalse(originalExercise.exists())
        assertFalse(wav.exists())
        diskDatabase.close()
        context.deleteDatabase(databaseName)
        Unit
    }

    private fun assertRun(expected: com.titaniumharmonics.bad.history.ExerciseRun,
                          actual: com.titaniumharmonics.bad.history.ExerciseRun) {
        assertEquals(expected.runId, actual.runId)
        assertEquals(expected.runtimeExerciseSnapshot, actual.runtimeExerciseSnapshot)
        assertEquals(expected.practiceResult.judgedNotes, actual.practiceResult.judgedNotes)
        assertEquals(expected.practiceResult.extraHits, actual.practiceResult.extraHits)
        assertEquals(expected.productionGraph.envelopePoints, actual.productionGraph.envelopePoints)
        assertEquals(expected.productionGraph.matchedHits, actual.productionGraph.matchedHits)
        assertEquals(expected.detectionSnapshot, actual.detectionSnapshot)
        assertEquals(expected.metronomeSnapshot, actual.metronomeSnapshot)
        assertEquals(expected.judgementSnapshot, actual.judgementSnapshot)
        assertEquals(expected.calibrationMetadata, actual.calibrationMetadata)
    }
}
