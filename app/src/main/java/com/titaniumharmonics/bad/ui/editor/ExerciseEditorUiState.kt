package com.titaniumharmonics.bad.ui.editor

import com.titaniumharmonics.bad.exercise.MeasureSubdivision

data class ExerciseEditorUiState(
    val exerciseName: String = "",
    val tempoBpmText: String = DEFAULT_TEMPO_BPM.toString(),
    val measures: List<EditorMeasureUiState> = emptyList(),
    val sourceDocumentUri: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
) {
    val canSave: Boolean
        get() = exerciseName.isNotBlank() &&
            tempoBpmText.toDoubleOrNull()?.let { it.isFinite() && it > 0.0 } == true &&
            measures.isNotEmpty() &&
            !isLoading &&
            !isSaving

    private companion object {
        const val DEFAULT_TEMPO_BPM = 120
    }
}

data class EditorMeasureUiState(
    val id: Int,
    val originalMeasureIndex: Int? = null,
    val subdivision: MeasureSubdivision = MeasureSubdivision.QUARTER,
    val notes: List<EditorNoteUiState> = emptyList(),
    val slots: List<EditorRhythmSlotUiState> = emptyList(),
    val unmappedNoteCount: Int = 0,
    val gridUnavailableReason: String? = null,
)

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
