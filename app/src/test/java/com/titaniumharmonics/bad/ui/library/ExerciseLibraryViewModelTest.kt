package com.titaniumharmonics.bad.ui.library

import com.titaniumharmonics.bad.exercise.ExerciseLibraryItem
import com.titaniumharmonics.bad.exercise.ExerciseLibraryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseLibraryViewModelTest {
    @Test
    fun requestDeletion_selectsOnlyAnExercisePresentInTheValidatedLibrary() {
        val viewModel = ExerciseLibraryViewModel(FakeExerciseLibraryRepository())
        val exercise = ExerciseLibraryItem(
            exerciseId = "valid-id",
            documentUri = "content://exercise/valid",
            fileName = "valid.json",
            exerciseName = "Valid exercise",
            tempoBpm = 100.0,
            patternCount = 2,
            expandedMeasureCount = 5,
        )
        viewModel.applyExercises(listOf(exercise))

        viewModel.requestDeletion("content://exercise/not-listed")
        assertNull(viewModel.uiState.value.exercisePendingDeletion)

        viewModel.requestDeletion(exercise.documentUri)
        assertEquals(exercise, viewModel.uiState.value.exercisePendingDeletion)
    }

    private class FakeExerciseLibraryRepository : ExerciseLibraryRepository {
        override fun loadExercises(): List<ExerciseLibraryItem> = emptyList()

        override fun deleteExercise(documentUri: String) = Unit
    }
}
