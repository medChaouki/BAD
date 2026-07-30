package com.titaniumharmonics.bad.ui.editor

import com.titaniumharmonics.bad.exercise.Exercise
import com.titaniumharmonics.bad.exercise.ExerciseDocumentStore
import com.titaniumharmonics.bad.exercise.ExerciseFormat
import com.titaniumharmonics.bad.exercise.ExpectedNote
import com.titaniumharmonics.bad.exercise.MeasureSubdivision
import com.titaniumharmonics.bad.exercise.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseEditorViewModelTest {
    @Test
    fun addMeasure_appendsEmptyQuarterNoteGrids() {
        val viewModel = ExerciseEditorViewModel(FakeExerciseDocumentStore())

        viewModel.addMeasure()
        viewModel.addMeasure()

        val measures = viewModel.uiState.value.measures
        assertEquals(listOf(1, 2), measures.map(EditorMeasureUiState::id))
        assertEquals(
            listOf(MeasureSubdivision.QUARTER, MeasureSubdivision.QUARTER),
            measures.map(EditorMeasureUiState::subdivision),
        )
        assertEquals(listOf(4, 4), measures.map { it.slots.size })
        assertEquals(8, measures.sumOf { measure -> measure.slots.count { it.hasNote } })
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
            listOf(1, 3),
            viewModel.uiState.value.measures.map(EditorMeasureUiState::id),
        )
        assertEquals(
            listOf(1_920L, 2_400L, 2_880L, 3_360L),
            viewModel.uiState.value.measures.last().slots.map(
                EditorRhythmSlotUiState::positionTicks,
            ),
        )
    }

    @Test
    fun buildNewExercise_enablesEveryQuarterNoteByDefault() {
        val viewModel = ExerciseEditorViewModel(FakeExerciseDocumentStore())
        viewModel.setExerciseName("Silent Measure")
        viewModel.addMeasure()

        val exercise = viewModel.buildEditedExercise()

        assertEquals(1, exercise.measureCount)
        assertEquals(
            listOf(0L, 480L, 960L, 1_440L),
            exercise.notes.map(ExpectedNote::positionTicks),
        )
    }

    @Test
    fun subdivisionSelection_resetsMeasureWithEveryNewSlotEnabled() {
        val viewModel = ExerciseEditorViewModel(FakeExerciseDocumentStore())
        val original = Exercise(
            formatVersion = ExerciseFormat.CURRENT_VERSION,
            id = "subdivision-test",
            name = "Subdivision test",
            description = "",
            tempoBpm = 100.0,
            timeSignature = TimeSignature(4, 4),
            countInMeasures = 1,
            measureCount = 1,
            ticksPerQuarterNote = 480,
            notes = listOf(ExpectedNote(positionTicks = 160)),
        )
        viewModel.applyLoadedExercise(original, "content://exercise/subdivision")

        viewModel.setMeasureSubdivision(
            measureId = 1,
            subdivision = MeasureSubdivision.EIGHTH_TRIPLET,
        )
        val edited = viewModel.buildEditedExercise()

        assertEquals(
            listOf(MeasureSubdivision.EIGHTH_TRIPLET),
            edited.measureSubdivisions,
        )
        assertEquals(
            (0L..1_760L step 160L).toList(),
            edited.notes.map(ExpectedNote::positionTicks),
        )
        assertEquals(12, viewModel.uiState.value.measures.single().slots.count { it.hasNote })
        assertEquals(0, viewModel.uiState.value.measures.single().unmappedNoteCount)
    }

    @Test
    fun toggleMeasureNote_disablesAndReenablesTheSelectedSlot() {
        val viewModel = ExerciseEditorViewModel(FakeExerciseDocumentStore())
        viewModel.setExerciseName("Toggle test")
        viewModel.addMeasure()

        viewModel.toggleMeasureNote(
            measureId = 1,
            positionWithinMeasureTicks = 480,
        )

        assertEquals(
            listOf(0L, 960L, 1_440L),
            viewModel.buildEditedExercise().notes.map(ExpectedNote::positionTicks),
        )
        assertEquals(
            false,
            viewModel.uiState.value.measures.single().slots[1].hasNote,
        )

        viewModel.toggleMeasureNote(
            measureId = 1,
            positionWithinMeasureTicks = 480,
        )

        assertEquals(
            listOf(0L, 480L, 960L, 1_440L),
            viewModel.buildEditedExercise().notes.map(ExpectedNote::positionTicks),
        )
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
        assertEquals(
            listOf(MeasureSubdivision.QUARTER, MeasureSubdivision.QUARTER),
            edited.measureSubdivisions,
        )
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
        assertEquals(2, edited.measureSubdivisions.size)
    }

    private class FakeExerciseDocumentStore : ExerciseDocumentStore {
        override fun read(documentUri: String): Exercise =
            error("Not used by this test.")

        override fun write(documentUri: String, exercise: Exercise) {
            error("Not used by this test.")
        }
    }
}
