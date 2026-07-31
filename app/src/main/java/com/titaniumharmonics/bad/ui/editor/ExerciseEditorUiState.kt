package com.titaniumharmonics.bad.ui.editor

import com.titaniumharmonics.bad.exercise.MeasureSubdivision
import com.titaniumharmonics.bad.exercise.MeasurePatternConstraints

data class ExerciseEditorUiState(
    val exerciseName: String = "",
    val tempoBpmText: String = DEFAULT_TEMPO_BPM.toString(),
    val measures: List<EditorMeasureUiState> = emptyList(),
    val sourceDocumentUri: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val documentUriReadyToPlay: String? = null,
    val message: String? = null,
    val errorMessage: String? = null,
) {
    val canSave: Boolean
        get() = exerciseName.isNotBlank() &&
            tempoBpmText.toDoubleOrNull()?.let { it.isFinite() && it > 0.0 } == true &&
            measures.isNotEmpty() &&
            !isLoading &&
            !isSaving

    val patternCount: Int
        get() = measures.size

    val totalExpandedMeasureCount: Int
        get() = measures.sumOf(EditorMeasureUiState::multiplier)

    private companion object {
        const val DEFAULT_TEMPO_BPM = 120
    }
}

data class EditorMeasureUiState(
    val id: Int,
    val originalMeasureIndex: Int? = null,
    val subdivision: MeasureSubdivision = MeasureSubdivision.QUARTER,
    val multiplier: Int = MeasurePatternConstraints.DEFAULT_MULTIPLIER,
    val expandedStartMeasureNumber: Int = 1,
    val expandedEndMeasureNumber: Int = 1,
    val notes: List<EditorNoteUiState> = emptyList(),
    val slots: List<EditorRhythmSlotUiState> = emptyList(),
    val unmappedNoteCount: Int = 0,
    val gridUnavailableReason: String? = null,
) {
    val expandedMeasureLabel: String
        get() = if (expandedStartMeasureNumber == expandedEndMeasureNumber) {
            "Measure $expandedStartMeasureNumber"
        } else {
            "Measures $expandedStartMeasureNumber–$expandedEndMeasureNumber"
        }
}

internal fun List<EditorMeasureUiState>.withExpandedMeasureRanges():
    List<EditorMeasureUiState> {
    var nextMeasureNumber = 1
    return map { pattern ->
        val endMeasureNumber = nextMeasureNumber + pattern.multiplier - 1
        pattern.copy(
            expandedStartMeasureNumber = nextMeasureNumber,
            expandedEndMeasureNumber = endMeasureNumber,
        ).also {
            nextMeasureNumber = endMeasureNumber + 1
        }
    }
}

data class EditorNoteUiState(
    val positionWithinMeasureTicks: Long,
    val accent: Boolean = false,
    val targetIntensity: Double? = null,
)

data class EditorRhythmSlotUiState(
    val index: Int,
    val positionTicks: Long,
    val positionWithinMeasureTicks: Long,
    val countLabel: String,
    val beatNumber: Int,
    val isBeatStart: Boolean,
    val hasNote: Boolean,
    val isAccented: Boolean,
)
