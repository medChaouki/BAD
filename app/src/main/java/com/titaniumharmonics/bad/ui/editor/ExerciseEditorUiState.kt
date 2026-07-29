package com.titaniumharmonics.bad.ui.editor

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
)
