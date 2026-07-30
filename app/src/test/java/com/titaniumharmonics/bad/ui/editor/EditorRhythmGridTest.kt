package com.titaniumharmonics.bad.ui.editor

import com.titaniumharmonics.bad.exercise.MeasureSubdivision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorRhythmGridTest {
    @Test
    fun quarterGrid_hasFourSlotsSpacedBy480Ticks() {
        val measure = EditorRhythmGrid.buildMeasure(
            id = 1,
            editedMeasureIndex = 0,
            subdivision = MeasureSubdivision.QUARTER,
        )

        assertEquals(4, measure.slots.size)
        assertEquals(
            listOf(0L, 480L, 960L, 1_440L),
            measure.slots.map(EditorRhythmSlotUiState::positionTicks),
        )
        assertEquals(
            listOf("1", "2", "3", "4"),
            measure.slots.map(EditorRhythmSlotUiState::countLabel),
        )
    }

    @Test
    fun eighthAndSixteenthGrids_haveExpectedSlotCountsAndSpacing() {
        val eighth = EditorRhythmGrid.buildMeasure(
            id = 1,
            editedMeasureIndex = 0,
            subdivision = MeasureSubdivision.EIGHTH,
        )
        val sixteenth = EditorRhythmGrid.buildMeasure(
            id = 2,
            editedMeasureIndex = 0,
            subdivision = MeasureSubdivision.SIXTEENTH,
        )

        assertEquals(8, eighth.slots.size)
        assertEquals(240L, eighth.slots[1].positionTicks)
        assertEquals(16, sixteenth.slots.size)
        assertEquals(120L, sixteenth.slots[1].positionTicks)
        assertEquals(
            listOf("1", "e", "&", "a"),
            sixteenth.slots.take(4).map(EditorRhythmSlotUiState::countLabel),
        )
    }

    @Test
    fun tripletGrid_hasTwelveSlotsSpacedBy160Ticks() {
        val measure = EditorRhythmGrid.buildMeasure(
            id = 1,
            editedMeasureIndex = 0,
            subdivision = MeasureSubdivision.EIGHTH_TRIPLET,
        )

        assertEquals(12, measure.slots.size)
        assertEquals(160L, measure.slots[1].positionTicks)
        assertEquals(1_760L, measure.slots.last().positionTicks)
        assertEquals(
            listOf("1", "trip", "let", "2", "trip", "let"),
            measure.slots.take(6).map(EditorRhythmSlotUiState::countLabel),
        )
    }

    @Test
    fun laterMeasures_includeTheirMeasureOffset() {
        val measure = EditorRhythmGrid.buildMeasure(
            id = 3,
            editedMeasureIndex = 2,
            subdivision = MeasureSubdivision.EIGHTH,
        )

        assertEquals(3_840L, measure.slots.first().positionTicks)
        assertEquals(4_080L, measure.slots[1].positionTicks)
    }

    @Test
    fun existingNotes_mapOnlyToExactSelectedGridSlots() {
        val measure = EditorRhythmGrid.buildMeasure(
            id = 1,
            editedMeasureIndex = 0,
            subdivision = MeasureSubdivision.SIXTEENTH,
            notes = listOf(
                EditorNoteUiState(
                    positionWithinMeasureTicks = 0,
                    accent = true,
                ),
                EditorNoteUiState(positionWithinMeasureTicks = 120),
                EditorNoteUiState(positionWithinMeasureTicks = 180),
            ),
        )

        assertTrue(measure.slots[0].hasNote)
        assertTrue(measure.slots[0].isAccented)
        assertTrue(measure.slots[1].hasNote)
        assertFalse(measure.slots[2].hasNote)
        assertEquals(1, measure.unmappedNoteCount)
    }

    @Test
    fun localNotes_mapAtTheEditedMeasureOffset() {
        val measure = EditorRhythmGrid.buildMeasure(
            id = 2,
            editedMeasureIndex = 1,
            originalMeasureIndex = 1,
            subdivision = MeasureSubdivision.QUARTER,
            notes = listOf(EditorNoteUiState(positionWithinMeasureTicks = 0)),
        )

        assertEquals(1_920L, measure.slots.first().positionTicks)
        assertTrue(measure.slots.first().hasNote)
    }

    @Test
    fun fullyEnabledTripletNotes_coverEveryTripletSlot() {
        val notes = EditorRhythmGrid.fullyEnabledNotes(
            subdivision = MeasureSubdivision.EIGHTH_TRIPLET,
        )

        assertEquals(12, notes.size)
        assertEquals(
            (0L..1_760L step 160L).toList(),
            notes.map(EditorNoteUiState::positionWithinMeasureTicks),
        )
    }
}
