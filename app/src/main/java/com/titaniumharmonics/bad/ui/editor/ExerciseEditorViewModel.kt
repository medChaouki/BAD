package com.titaniumharmonics.bad.ui.editor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.titaniumharmonics.bad.exercise.ContentResolverExerciseDocumentStore
import com.titaniumharmonics.bad.exercise.Exercise
import com.titaniumharmonics.bad.exercise.ExerciseDocumentStore
import com.titaniumharmonics.bad.exercise.ExerciseFormat
import com.titaniumharmonics.bad.exercise.ExpectedNote
import com.titaniumharmonics.bad.exercise.TimeSignature
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExerciseEditorViewModel(
    private val documentStore: ExerciseDocumentStore,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ExerciseEditorUiState())
    val uiState: StateFlow<ExerciseEditorUiState> = mutableUiState.asStateFlow()

    private var sourceExercise: Exercise? = null
    private var documentJob: Job? = null

    fun createExercise() {
        documentJob?.cancel()
        sourceExercise = null
        mutableUiState.value = ExerciseEditorUiState()
    }

    fun loadExercise(documentUri: String) {
        documentJob?.cancel()
        sourceExercise = null
        mutableUiState.value = ExerciseEditorUiState(
            sourceDocumentUri = documentUri,
            isLoading = true,
        )

        documentJob = viewModelScope.launch {
            try {
                val exercise = withContext(Dispatchers.IO) {
                    documentStore.read(documentUri)
                }
                applyLoadedExercise(exercise, documentUri)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                mutableUiState.value = ExerciseEditorUiState(
                    errorMessage = exception.message ?: "Exercise loading failed.",
                )
            }
        }
    }

    fun setExerciseName(exerciseName: String) {
        mutableUiState.value = mutableUiState.value.copy(
            exerciseName = exerciseName,
            message = null,
            errorMessage = null,
        )
    }

    internal fun applyLoadedExercise(
        exercise: Exercise,
        documentUri: String,
    ) {
        sourceExercise = exercise
        mutableUiState.value = ExerciseEditorUiState(
            exerciseName = exercise.name,
            tempoBpmText = exercise.tempoBpm.toEditorText(),
            measures = List(exercise.measureCount) { index ->
                EditorMeasureUiState(
                    id = index + 1,
                    originalMeasureIndex = index,
                )
            },
            sourceDocumentUri = documentUri,
        )
    }

    fun setTempoBpmText(tempoBpmText: String) {
        if (!BPM_INPUT_PATTERN.matches(tempoBpmText)) return
        mutableUiState.value = mutableUiState.value.copy(
            tempoBpmText = tempoBpmText,
            message = null,
            errorMessage = null,
        )
    }

    fun addMeasure() {
        val state = mutableUiState.value
        val nextMeasureId = (state.measures.lastOrNull()?.id ?: 0) + 1
        mutableUiState.value = state.copy(
            measures = state.measures + EditorMeasureUiState(id = nextMeasureId),
            message = null,
            errorMessage = null,
        )
    }

    fun deleteMeasure(measureId: Int) {
        val state = mutableUiState.value
        if (state.measures.none { it.id == measureId }) return
        mutableUiState.value = state.copy(
            measures = state.measures.filterNot { it.id == measureId },
            message = null,
            errorMessage = null,
        )
    }

    fun saveExercise(destinationDocumentUri: String? = null) {
        if (documentJob?.isActive == true) return
        val state = mutableUiState.value
        val documentUri = destinationDocumentUri ?: state.sourceDocumentUri
        if (documentUri == null) {
            mutableUiState.value = state.copy(
                errorMessage = "Choose where to save the exercise.",
            )
            return
        }

        val exercise = runCatching { buildEditedExercise(state) }
            .getOrElse { exception ->
                mutableUiState.value = state.copy(
                    errorMessage = exception.message ?: "Exercise is not ready to save.",
                )
                return
            }

        mutableUiState.value = state.copy(
            isSaving = true,
            message = null,
            errorMessage = null,
        )
        documentJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    documentStore.write(documentUri, exercise)
                }
                sourceExercise = exercise
                mutableUiState.value = mutableUiState.value.copy(
                    sourceDocumentUri = documentUri,
                    isSaving = false,
                    message = "Exercise saved.",
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                mutableUiState.value = mutableUiState.value.copy(
                    isSaving = false,
                    errorMessage = exception.message ?: "Exercise saving failed.",
                )
            }
        }
    }

    internal fun buildEditedExercise(state: ExerciseEditorUiState = uiState.value): Exercise {
        require(state.exerciseName.isNotBlank()) {
            "Exercise name must not be blank."
        }
        val tempoBpm = state.tempoBpmText.toDoubleOrNull()
        require(tempoBpm != null && tempoBpm.isFinite() && tempoBpm > 0.0) {
            "BPM must be greater than zero."
        }
        require(state.measures.isNotEmpty()) {
            "Add at least one measure."
        }

        return sourceExercise?.let { original ->
            original.copy(
                name = state.exerciseName.trim(),
                tempoBpm = tempoBpm,
                measureCount = state.measures.size,
                notes = original.notes.remapToMeasures(
                    originalExercise = original,
                    editedMeasures = state.measures,
                ),
            )
        } ?: Exercise(
            formatVersion = ExerciseFormat.CURRENT_VERSION,
            id = state.exerciseName.toExerciseId(),
            name = state.exerciseName.trim(),
            description = "",
            tempoBpm = tempoBpm,
            timeSignature = TimeSignature(numerator = 4, denominator = 4),
            countInMeasures = 1,
            measureCount = state.measures.size,
            ticksPerQuarterNote = DEFAULT_TICKS_PER_QUARTER_NOTE,
            notes = emptyList(),
        )
    }

    override fun onCleared() {
        documentJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val DEFAULT_TICKS_PER_QUARTER_NOTE = 480
        private val BPM_INPUT_PATTERN = Regex("""\d*(\.\d*)?""")

        fun factory(context: Context): ViewModelProvider.Factory {
            val applicationContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T {
                    require(modelClass.isAssignableFrom(ExerciseEditorViewModel::class.java))
                    return ExerciseEditorViewModel(
                        documentStore = ContentResolverExerciseDocumentStore(
                            applicationContext.contentResolver,
                        ),
                    ) as T
                }
            }
        }
    }
}

private fun Double.toEditorText(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

private fun String.toExerciseId(): String {
    val normalized = trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
    return normalized.ifBlank { "untitled-exercise" }
}

private fun List<ExpectedNote>.remapToMeasures(
    originalExercise: Exercise,
    editedMeasures: List<EditorMeasureUiState>,
): List<ExpectedNote> {
    val ticksPerMeasure =
        originalExercise.ticksPerQuarterNote.toLong() *
            originalExercise.timeSignature.numerator *
            4L /
            originalExercise.timeSignature.denominator
    val newIndexByOriginalIndex = editedMeasures
        .mapIndexedNotNull { editedIndex, measure ->
            measure.originalMeasureIndex?.let { originalIndex ->
                originalIndex to editedIndex
            }
        }
        .toMap()

    return mapNotNull { note ->
        val originalMeasureIndex = (note.positionTicks / ticksPerMeasure).toInt()
        val editedMeasureIndex = newIndexByOriginalIndex[originalMeasureIndex]
            ?: return@mapNotNull null
        val positionWithinMeasure = note.positionTicks % ticksPerMeasure
        note.copy(
            positionTicks = editedMeasureIndex * ticksPerMeasure + positionWithinMeasure,
        )
    }
}
