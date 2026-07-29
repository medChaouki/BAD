package com.titaniumharmonics.bad.ui.editor

import com.titaniumharmonics.bad.exercise.Exercise
import com.titaniumharmonics.bad.exercise.ExerciseDocumentStore
import com.titaniumharmonics.bad.exercise.ExerciseFormat
import com.titaniumharmonics.bad.exercise.ExpectedNote
import com.titaniumharmonics.bad.exercise.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseEditorViewModelTest {
    @Test
    fun addMeasure_appendsPlaceholderMeasures() {
        val viewModel = ExerciseEditorViewModel(FakeExerciseDocumentStore())

        viewModel.addMeasure()
        viewModel.addMeasure()

        assertEquals(
            listOf(
                EditorMeasureUiState(id = 1),
                EditorMeasureUiState(id = 2),
            ),
            viewModel.uiState.value.measures,
        )
    }

    @Test
    fun addMeasure_preservesEnteredNameAndBpm() {
        val viewModel = ExerciseEditorViewModel(FakeExerciseDocumentStore())

        viewModel.setExerciseName("Backbeat Builder")
        viewModel.setTempoBpmText("96")
        viewModel.addMeasure()

        assertEquals("Backbeat Builder", viewModel.uiState.value.exerciseName)
        assertEquals("96", viewModel.uiState.value.tempoBpmText)
        assertEquals(1, viewModel.uiState.value.measures.size)
    }

    @Test
    fun deleteMeasure_removesOnlySelectedMeasure() {
        val viewModel = ExerciseEditorViewModel(FakeExerciseDocumentStore())
        viewModel.addMeasure()
        viewModel.addMeasure()
        viewModel.addMeasure()

        viewModel.deleteMeasure(measureId = 2)

        assertEquals(
            listOf(
                EditorMeasureUiState(id = 1),
                EditorMeasureUiState(id = 3),
            ),
            viewModel.uiState.value.measures,
        )
    }

    @Test
    fun buildNewExercise_keepsEmptyMeasuresNoteFree() {
        val viewModel = ExerciseEditorViewModel(FakeExerciseDocumentStore())
        viewModel.setExerciseName("Silent Measure")
        viewModel.addMeasure()

        val exercise = viewModel.buildEditedExercise()

        assertEquals(1, exercise.measureCount)
        assertEquals(emptyList<ExpectedNote>(), exercise.notes)
    }

    @Test
    fun buildEditedExercise_preservesUnsupportedLoadedFields() {
        val viewModel = ExerciseEditorViewModel(FakeExerciseDocumentStore())
        val original = Exercise(
            formatVersion = ExerciseFormat.CURRENT_VERSION,
            id = "original-id",
            name = "Original",
            description = "Keep this",
            tempoBpm = 100.0,
            timeSignature = TimeSignature(3, 4),
            countInMeasures = 2,
            measureCount = 1,
            ticksPerQuarterNote = 480,
            notes = listOf(ExpectedNote(positionTicks = 240, targetIntensity = 0.7)),
        )

        viewModel.applyLoadedExercise(original, "content://exercise/original")
        viewModel.setExerciseName("Edited")
        viewModel.setTempoBpmText("110")
        viewModel.addMeasure()

        val edited = viewModel.buildEditedExercise()

        assertEquals("original-id", edited.id)
        assertEquals("Keep this", edited.description)
        assertEquals(TimeSignature(3, 4), edited.timeSignature)
        assertEquals(2, edited.countInMeasures)
        assertEquals(original.notes, edited.notes)
        assertEquals("Edited", edited.name)
        assertEquals(110.0, edited.tempoBpm, 0.0)
        assertEquals(2, edited.measureCount)
    }

    @Test
    fun buildEditedExercise_deletesMeasureNotesAndShiftsLaterNotes() {
        val viewModel = ExerciseEditorViewModel(FakeExerciseDocumentStore())
        val original = Exercise(
            formatVersion = ExerciseFormat.CURRENT_VERSION,
            id = "three-measures",
            name = "Three measures",
            description = "",
            tempoBpm = 100.0,
            timeSignature = TimeSignature(4, 4),
            countInMeasures = 1,
            measureCount = 3,
            ticksPerQuarterNote = 480,
            notes = listOf(
                ExpectedNote(positionTicks = 0),
                ExpectedNote(positionTicks = 1_920),
                ExpectedNote(positionTicks = 3_840),
            ),
        )
        viewModel.applyLoadedExercise(original, "content://exercise/three-measures")

        viewModel.deleteMeasure(measureId = 2)
        val edited = viewModel.buildEditedExercise()

        assertEquals(2, edited.measureCount)
        assertEquals(
            listOf(0L, 1_920L),
            edited.notes.map(ExpectedNote::positionTicks),
        )
    }

    private class FakeExerciseDocumentStore : ExerciseDocumentStore {
        override fun read(documentUri: String): Exercise =
            error("Not used by this test.")

        override fun write(documentUri: String, exercise: Exercise) {
            error("Not used by this test.")
        }
    }
}
