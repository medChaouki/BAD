package com.titaniumharmonics.bad.ui.editor

import com.titaniumharmonics.bad.exercise.MeasureSubdivision
import com.titaniumharmonics.bad.exercise.MeasurePatternConstraints
import com.titaniumharmonics.bad.exercise.TimeSignature

internal object EditorRhythmGrid {
    const val DEFAULT_TICKS_PER_QUARTER_NOTE = 480
    val DEFAULT_TIME_SIGNATURE = TimeSignature(numerator = 4, denominator = 4)

    fun buildMeasure(
        id: Int,
        editedMeasureIndex: Int,
        originalMeasureIndex: Int? = null,
        subdivision: MeasureSubdivision = MeasureSubdivision.QUARTER,
        multiplier: Int = MeasurePatternConstraints.DEFAULT_MULTIPLIER,
        ticksPerQuarterNote: Int = DEFAULT_TICKS_PER_QUARTER_NOTE,
        timeSignature: TimeSignature = DEFAULT_TIME_SIGNATURE,
        notes: List<EditorNoteUiState> = emptyList(),
    ): EditorMeasureUiState {
        if (timeSignature != DEFAULT_TIME_SIGNATURE) {
            return EditorMeasureUiState(
                id = id,
                originalMeasureIndex = originalMeasureIndex,
                subdivision = subdivision,
                multiplier = multiplier,
                notes = notes,
                unmappedNoteCount = notes.size,
                gridUnavailableReason = "Rhythmic grid supports 4/4 exercises only.",
            )
        }

        val subdivisionsPerQuarter = subdivision.subdivisionsPerQuarter
        if (ticksPerQuarterNote % subdivisionsPerQuarter != 0) {
            return EditorMeasureUiState(
                id = id,
                originalMeasureIndex = originalMeasureIndex,
                subdivision = subdivision,
                multiplier = multiplier,
                notes = notes,
                unmappedNoteCount = notes.size,
                gridUnavailableReason =
                    "$ticksPerQuarterNote ticks per quarter note cannot represent " +
                        "${subdivision.displayName} slots exactly.",
            )
        }

        val ticksPerSlot = ticksPerQuarterNote / subdivisionsPerQuarter
        val ticksPerMeasure = ticksPerQuarterNote.toLong() * BEATS_PER_MEASURE
        val editedMeasureOffset = editedMeasureIndex * ticksPerMeasure
        val notesByLocalTick = notes.associateBy { note ->
            note.positionWithinMeasureTicks
        }
        val slots = List(subdivision.slotCount) { slotIndex ->
            val localTick = slotIndex * ticksPerSlot.toLong()
            val note = notesByLocalTick[localTick]
            val positionWithinBeat = slotIndex % subdivisionsPerQuarter
            EditorRhythmSlotUiState(
                index = slotIndex,
                positionTicks = editedMeasureOffset + localTick,
                positionWithinMeasureTicks = localTick,
                countLabel = subdivision.countLabel(
                    beatNumber = slotIndex / subdivisionsPerQuarter + 1,
                    positionWithinBeat = positionWithinBeat,
                ),
                beatNumber = slotIndex / subdivisionsPerQuarter + 1,
                isBeatStart = positionWithinBeat == 0,
                hasNote = note != null,
                isAccented = note?.accent == true,
            )
        }
        val slotTicks = slots.mapTo(mutableSetOf()) {
            it.positionWithinMeasureTicks
        }

        return EditorMeasureUiState(
            id = id,
            originalMeasureIndex = originalMeasureIndex,
            subdivision = subdivision,
            multiplier = multiplier,
            notes = notes,
            slots = slots,
            unmappedNoteCount = notes.count { note ->
                note.positionWithinMeasureTicks !in slotTicks
            },
        )
    }

    fun fullyEnabledNotes(
        subdivision: MeasureSubdivision,
        ticksPerQuarterNote: Int = DEFAULT_TICKS_PER_QUARTER_NOTE,
        timeSignature: TimeSignature = DEFAULT_TIME_SIGNATURE,
    ): List<EditorNoteUiState> {
        if (
            timeSignature != DEFAULT_TIME_SIGNATURE ||
            ticksPerQuarterNote % subdivision.subdivisionsPerQuarter != 0
        ) {
            return emptyList()
        }
        val ticksPerSlot =
            ticksPerQuarterNote / subdivision.subdivisionsPerQuarter
        return List(subdivision.slotCount) { slotIndex ->
            EditorNoteUiState(
                positionWithinMeasureTicks = slotIndex * ticksPerSlot.toLong(),
            )
        }
    }

    private const val BEATS_PER_MEASURE = 4
}

internal val MeasureSubdivision.displayName: String
    get() = when (this) {
        MeasureSubdivision.QUARTER -> "Quarter"
        MeasureSubdivision.EIGHTH -> "Eighth"
        MeasureSubdivision.EIGHTH_TRIPLET -> "Triplet"
        MeasureSubdivision.SIXTEENTH -> "Sixteenth"
    }

private val MeasureSubdivision.subdivisionsPerQuarter: Int
    get() = when (this) {
        MeasureSubdivision.QUARTER -> 1
        MeasureSubdivision.EIGHTH -> 2
        MeasureSubdivision.EIGHTH_TRIPLET -> 3
        MeasureSubdivision.SIXTEENTH -> 4
    }

private val MeasureSubdivision.slotCount: Int
    get() = subdivisionsPerQuarter * 4

private fun MeasureSubdivision.countLabel(
    beatNumber: Int,
    positionWithinBeat: Int,
): String = when (this) {
    MeasureSubdivision.QUARTER -> beatNumber.toString()
    MeasureSubdivision.EIGHTH -> listOf(beatNumber.toString(), "&")[positionWithinBeat]
    MeasureSubdivision.EIGHTH_TRIPLET ->
        listOf(beatNumber.toString(), "trip", "let")[positionWithinBeat]
    MeasureSubdivision.SIXTEENTH ->
        listOf(beatNumber.toString(), "e", "&", "a")[positionWithinBeat]
}
