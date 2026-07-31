package com.titaniumharmonics.bad.ui.editor

import com.titaniumharmonics.bad.exercise.EditableExercise
import com.titaniumharmonics.bad.exercise.ExerciseDocumentStore
import com.titaniumharmonics.bad.exercise.ExerciseFormat
import com.titaniumharmonics.bad.exercise.ExerciseJsonCodec
import com.titaniumharmonics.bad.exercise.ExpectedNote
import com.titaniumharmonics.bad.exercise.MeasureSubdivision
import com.titaniumharmonics.bad.exercise.MeasurePatternConstraints
import com.titaniumharmonics.bad.exercise.RuntimeExpectedNote
import com.titaniumharmonics.bad.exercise.TimeSignature
import com.titaniumharmonics.bad.exercise.compileForTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
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
        assertEquals(listOf(1, 1), measures.map(EditorMeasureUiState::multiplier))
    }

    @Test
    fun measureMultiplier_incrementsAndDecrements() {
        val viewModel = ExerciseEditorViewModel(FakeExerciseDocumentStore())
        viewModel.addMeasure()

        viewModel.increaseMeasureMultiplier(measureId = 1)
        assertEquals(2, viewModel.uiState.value.measures.single().multiplier)

        viewModel.decreaseMeasureMultiplier(measureId = 1)
        assertEquals(1, viewModel.uiState.value.measures.single().multiplier)
    }

    @Test
    fun measureMultiplier_cannotDecreaseBelowOne() {
        val viewModel = ExerciseEditorViewModel(FakeExerciseDocumentStore())
        viewModel.addMeasure()

        viewModel.decreaseMeasureMultiplier(measureId = 1)
        viewModel.decreaseMeasureMultiplier(measureId = 1)

        assertEquals(1, viewModel.uiState.value.measures.single().multiplier)
    }

    @Test
    fun measureMultiplier_cannotIncreaseAboveSafetyLimit() {
        val viewModel = ExerciseEditorViewModel(FakeExerciseDocumentStore())
        viewModel.addMeasure()

        repeat(MeasurePatternConstraints.MAX_MULTIPLIER + 5) {
            viewModel.increaseMeasureMultiplier(measureId = 1)
        }

        assertEquals(
            MeasurePatternConstraints.MAX_MULTIPLIER,
            viewModel.uiState.value.measures.single().multiplier,
        )
    }

    @Test
    fun mixedMultipliers_updateExpandedCountRangesAndFollowingIndexes() {
        val viewModel = ExerciseEditorViewModel(FakeExerciseDocumentStore())
        repeat(3) { viewModel.addMeasure() }
        repeat(3) { viewModel.increaseMeasureMultiplier(measureId = 1) }
        viewModel.increaseMeasureMultiplier(measureId = 2)

        val expandedState = viewModel.uiState.value
        assertEquals(3, expandedState.patternCount)
        assertEquals(7, expandedState.totalExpandedMeasureCount)
        assertEquals(
            listOf(1, 5, 7),
            expandedState.measures.map(EditorMeasureUiState::expandedStartMeasureNumber),
        )
        assertEquals(
            listOf(4, 6, 7),
            expandedState.measures.map(EditorMeasureUiState::expandedEndMeasureNumber),
        )
        assertEquals(
            listOf("Measures 1–4", "Measures 5–6", "Measure 7"),
            expandedState.measures.map(EditorMeasureUiState::expandedMeasureLabel),
        )

        viewModel.decreaseMeasureMultiplier(measureId = 1)

        val updatedMeasures = viewModel.uiState.value.measures
        assertEquals(
            listOf(1, 4, 6),
            updatedMeasures.map(EditorMeasureUiState::expandedStartMeasureNumber),
        )
        assertEquals(
            listOf(3, 5, 6),
            updatedMeasures.map(EditorMeasureUiState::expandedEndMeasureNumber),
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
        assertEquals(listOf(1), exercise.measureMultipliers)
        assertEquals(
            listOf(0L, 480L, 960L, 1_440L),
            exercise.notes.map(ExpectedNote::positionTicks),
        )
    }

    @Test
    fun buildAndReload_preservesMeasureMultipliers() {
        val viewModel = ExerciseEditorViewModel(FakeExerciseDocumentStore())
        val original = EditableExercise(
            formatVersion = ExerciseFormat.CURRENT_VERSION,
            id = "multiplier-test",
            name = "Multiplier test",
            description = "",
            tempoBpm = 100.0,
            timeSignature = TimeSignature(4, 4),
            countInMeasures = 1,
            measureCount = 2,
            ticksPerQuarterNote = 480,
            notes = emptyList(),
            measureMultipliers = listOf(4, 2),
        )

        viewModel.applyLoadedExercise(original, "content://exercise/multipliers")
        val edited = viewModel.buildEditedExercise()
        val reopenedViewModel = ExerciseEditorViewModel(FakeExerciseDocumentStore())
        reopenedViewModel.applyLoadedExercise(
            edited,
            "content://exercise/multipliers",
        )

        assertEquals(listOf(4, 2), edited.measureMultipliers)
        assertEquals(
            listOf(4, 2),
            reopenedViewModel.uiState.value.measures.map(
                EditorMeasureUiState::multiplier,
            ),
        )
        assertEquals(6, reopenedViewModel.uiState.value.totalExpandedMeasureCount)
    }

    @Test
    fun subdivisionSelection_resetsMeasureWithEveryNewSlotEnabled() {
        val viewModel = ExerciseEditorViewModel(FakeExerciseDocumentStore())
        val original = EditableExercise(
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
        val original = EditableExercise(
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
        val original = EditableExercise(
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

    @Test
    fun duplicatePattern_insertsIndependentCopyImmediatelyAfterSource() {
        val viewModel = loadedPatternViewModel()
        val source = viewModel.uiState.value.measures.first()

        viewModel.duplicateMeasurePattern(source.id)

        val measures = viewModel.uiState.value.measures
        val duplicate = measures[1]
        assertEquals(listOf(1, 4, 2, 3), measures.map(EditorMeasureUiState::id))
        assertNotEquals(source.id, duplicate.id)
        assertEquals(source.subdivision, duplicate.subdivision)
        assertEquals(source.multiplier, duplicate.multiplier)
        assertEquals(source.notes, duplicate.notes)
        assertNotSame(source.notes, duplicate.notes)
        assertEquals(1, duplicate.unmappedNoteCount)

        viewModel.toggleMeasureNote(
            measureId = duplicate.id,
            positionWithinMeasureTicks = 160L,
        )

        val updatedMeasures = viewModel.uiState.value.measures
        assertEquals(
            listOf(0L, 100L, 160L),
            updatedMeasures[0].notes.map(EditorNoteUiState::positionWithinMeasureTicks),
        )
        assertEquals(
            listOf(0L, 100L),
            updatedMeasures[1].notes.map(EditorNoteUiState::positionWithinMeasureTicks),
        )
    }

    @Test
    fun duplicatePattern_recalculatesExpandedCountAndFollowingRanges() {
        val viewModel = loadedPatternViewModel()

        viewModel.duplicateMeasurePattern(measureId = 1)

        val state = viewModel.uiState.value
        assertEquals(9, state.totalExpandedMeasureCount)
        assertEquals(
            listOf(1, 4, 7, 9),
            state.measures.map(EditorMeasureUiState::expandedStartMeasureNumber),
        )
        assertEquals(
            listOf(3, 6, 8, 9),
            state.measures.map(EditorMeasureUiState::expandedEndMeasureNumber),
        )
    }

    @Test
    fun clearPattern_removesMappedAndOutOfGridNotesButPreservesPatternSettings() {
        val viewModel = loadedPatternViewModel()
        val original = viewModel.uiState.value.measures.first()

        viewModel.clearMeasurePattern(original.id)

        val cleared = viewModel.uiState.value.measures.first()
        assertEquals(emptyList<EditorNoteUiState>(), cleared.notes)
        assertEquals(0, cleared.unmappedNoteCount)
        assertEquals(0, cleared.slots.count(EditorRhythmSlotUiState::hasNote))
        assertEquals(original.subdivision, cleared.subdivision)
        assertEquals(original.multiplier, cleared.multiplier)
        assertEquals(original.id, cleared.id)
    }

    @Test
    fun movePatternUpAndDown_preservesIdentityContentAndRecalculatesRanges() {
        val viewModel = loadedPatternViewModel()
        val originalById = viewModel.uiState.value.measures.associateBy { it.id }

        viewModel.moveMeasurePatternUp(measureId = 2)

        var measures = viewModel.uiState.value.measures
        assertEquals(listOf(2, 1, 3), measures.map(EditorMeasureUiState::id))
        assertEquals(listOf(1, 3, 6), measures.map { it.expandedStartMeasureNumber })
        assertEquals(originalById.getValue(2).notes, measures[0].notes)
        assertEquals(originalById.getValue(2).subdivision, measures[0].subdivision)
        assertEquals(originalById.getValue(2).multiplier, measures[0].multiplier)

        viewModel.moveMeasurePatternDown(measureId = 2)

        measures = viewModel.uiState.value.measures
        assertEquals(listOf(1, 2, 3), measures.map(EditorMeasureUiState::id))
        assertEquals(listOf(1, 4, 6), measures.map { it.expandedStartMeasureNumber })
    }

    @Test
    fun movePattern_doesNothingBeyondFirstOrLastPosition() {
        val viewModel = loadedPatternViewModel()
        val originalIds = viewModel.uiState.value.measures.map(EditorMeasureUiState::id)

        viewModel.moveMeasurePatternUp(measureId = originalIds.first())
        viewModel.moveMeasurePatternDown(measureId = originalIds.last())

        assertEquals(
            originalIds,
            viewModel.uiState.value.measures.map(EditorMeasureUiState::id),
        )
    }

    @Test
    fun reorderedPatterns_arePreservedByJsonAndRuntimeCompilation() {
        val viewModel = loadedPatternViewModel()
        viewModel.moveMeasurePatternUp(measureId = 3)
        viewModel.moveMeasurePatternUp(measureId = 3)

        val edited = viewModel.buildEditedExercise()
        val reopened = ExerciseJsonCodec.decode(ExerciseJsonCodec.encode(edited))
        val runtime = reopened.compileForTest()

        assertEquals(
            listOf(
                MeasureSubdivision.SIXTEENTH,
                MeasureSubdivision.EIGHTH_TRIPLET,
                MeasureSubdivision.EIGHTH,
            ),
            reopened.measureSubdivisions,
        )
        assertEquals(listOf(1, 3, 2), reopened.measureMultipliers)
        assertEquals(
            listOf(120L, 1_920L, 2_020L, 2_080L, 3_840L, 3_940L, 4_000L),
            runtime.notes.take(7).map(RuntimeExpectedNote::positionTicks),
        )
        assertEquals(6, runtime.measureCount)
    }

    @Test
    fun addingAfterReorder_usesANewStablePatternId() {
        val viewModel = loadedPatternViewModel()
        viewModel.moveMeasurePatternUp(measureId = 3)

        viewModel.addMeasure()

        assertEquals(
            listOf(1, 3, 2, 4),
            viewModel.uiState.value.measures.map(EditorMeasureUiState::id),
        )
    }

    private fun loadedPatternViewModel(): ExerciseEditorViewModel =
        ExerciseEditorViewModel(FakeExerciseDocumentStore()).also { viewModel ->
            viewModel.applyLoadedExercise(
                exercise = EditableExercise(
                    formatVersion = ExerciseFormat.CURRENT_VERSION,
                    id = "pattern-actions",
                    name = "Pattern actions",
                    description = "",
                    tempoBpm = 100.0,
                    timeSignature = TimeSignature(4, 4),
                    countInMeasures = 1,
                    measureCount = 3,
                    ticksPerQuarterNote = 480,
                    notes = listOf(
                        ExpectedNote(positionTicks = 0L),
                        ExpectedNote(positionTicks = 100L),
                        ExpectedNote(positionTicks = 160L),
                        ExpectedNote(positionTicks = 1_920L + 240L),
                        ExpectedNote(positionTicks = 3_840L + 120L),
                    ),
                    measureSubdivisions = listOf(
                        MeasureSubdivision.EIGHTH_TRIPLET,
                        MeasureSubdivision.EIGHTH,
                        MeasureSubdivision.SIXTEENTH,
                    ),
                    measureMultipliers = listOf(3, 2, 1),
                ),
                documentUri = "content://exercise/pattern-actions",
            )
        }

    private class FakeExerciseDocumentStore : ExerciseDocumentStore {
        override fun read(documentUri: String): EditableExercise =
            error("Not used by this test.")

        override fun write(documentUri: String, exercise: EditableExercise) {
            error("Not used by this test.")
        }
    }
}
