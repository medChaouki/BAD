package com.titaniumharmonics.bad.ui.editor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.titaniumharmonics.bad.exercise.ContentResolverExerciseDocumentStore
import com.titaniumharmonics.bad.exercise.EditableExercise
import com.titaniumharmonics.bad.exercise.ExerciseDocumentStore
import com.titaniumharmonics.bad.exercise.ExerciseFormat
import com.titaniumharmonics.bad.exercise.ExpectedNote
import com.titaniumharmonics.bad.exercise.MeasureSubdivision
import com.titaniumharmonics.bad.exercise.MeasurePatternConstraints
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

    private var sourceExercise: EditableExercise? = null
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
        exercise: EditableExercise,
        documentUri: String,
    ) {
        sourceExercise = exercise
        mutableUiState.value = ExerciseEditorUiState(
            exerciseName = exercise.name,
            tempoBpmText = exercise.tempoBpm.toEditorText(),
            measures = List(exercise.measureCount) { index ->
                EditorRhythmGrid.buildMeasure(
                    id = index + 1,
                    editedMeasureIndex = index,
                    originalMeasureIndex = index,
                    subdivision = exercise.measureSubdivisions[index],
                    multiplier = exercise.measureMultipliers[index],
                    ticksPerQuarterNote = exercise.ticksPerQuarterNote,
                    timeSignature = exercise.timeSignature,
                    notes = exercise.editorNotesForMeasure(index),
                )
            }.withExpandedMeasureRanges(),
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
        val nextMeasureId = state.measures.nextMeasureId()
        val ticksPerQuarterNote =
            sourceExercise?.ticksPerQuarterNote ?: DEFAULT_TICKS_PER_QUARTER_NOTE
        val timeSignature =
            sourceExercise?.timeSignature ?: EditorRhythmGrid.DEFAULT_TIME_SIGNATURE
        val subdivision = MeasureSubdivision.QUARTER
        mutableUiState.value = state.copy(
            measures = (
                state.measures + EditorRhythmGrid.buildMeasure(
                    id = nextMeasureId,
                    editedMeasureIndex = state.measures.size,
                    subdivision = subdivision,
                    ticksPerQuarterNote = ticksPerQuarterNote,
                    timeSignature = timeSignature,
                    notes = EditorRhythmGrid.fullyEnabledNotes(
                        subdivision = subdivision,
                        ticksPerQuarterNote = ticksPerQuarterNote,
                        timeSignature = timeSignature,
                    ),
                )
            ).withExpandedMeasureRanges(),
            message = null,
            errorMessage = null,
        )
    }

    fun increaseMeasureMultiplier(measureId: Int) {
        updateMeasureMultiplier(measureId, change = 1)
    }

    fun decreaseMeasureMultiplier(measureId: Int) {
        updateMeasureMultiplier(measureId, change = -1)
    }

    private fun updateMeasureMultiplier(
        measureId: Int,
        change: Int,
    ) {
        val state = mutableUiState.value
        if (state.measures.none { it.id == measureId }) return
        mutableUiState.value = state.copy(
            measures = state.measures.map { measure ->
                if (measure.id == measureId) {
                    measure.copy(
                        multiplier = (measure.multiplier + change).coerceIn(
                            MeasurePatternConstraints.MIN_MULTIPLIER,
                            MeasurePatternConstraints.MAX_MULTIPLIER,
                        ),
                    )
                } else {
                    measure
                }
            }.withExpandedMeasureRanges(),
            message = null,
            errorMessage = null,
        )
    }

    fun setMeasureSubdivision(
        measureId: Int,
        subdivision: MeasureSubdivision,
    ) {
        val state = mutableUiState.value
        if (state.measures.none { it.id == measureId }) return
        val ticksPerQuarterNote =
            sourceExercise?.ticksPerQuarterNote ?: DEFAULT_TICKS_PER_QUARTER_NOTE
        val timeSignature =
            sourceExercise?.timeSignature ?: EditorRhythmGrid.DEFAULT_TIME_SIGNATURE
        val updatedMeasures = state.measures.map { measure ->
            if (measure.id == measureId) {
                measure.copy(
                    subdivision = subdivision,
                    notes = EditorRhythmGrid.fullyEnabledNotes(
                        subdivision = subdivision,
                        ticksPerQuarterNote = ticksPerQuarterNote,
                        timeSignature = timeSignature,
                    ),
                )
            } else {
                measure
            }
        }.rebuildGrid(sourceExercise)
        mutableUiState.value = state.copy(
            measures = updatedMeasures,
            message = null,
            errorMessage = null,
        )
    }

    fun toggleMeasureNote(
        measureId: Int,
        positionWithinMeasureTicks: Long,
    ) {
        val state = mutableUiState.value
        val updatedMeasures = state.measures.map { measure ->
            if (measure.id != measureId) return@map measure
            val existingNote = measure.notes.find { note ->
                note.positionWithinMeasureTicks == positionWithinMeasureTicks
            }
            val updatedNotes = if (existingNote == null) {
                measure.notes + EditorNoteUiState(
                    positionWithinMeasureTicks = positionWithinMeasureTicks,
                )
            } else {
                measure.notes - existingNote
            }
            measure.copy(
                notes = updatedNotes.sortedBy(
                    EditorNoteUiState::positionWithinMeasureTicks,
                ),
            )
        }.rebuildGrid(sourceExercise)
        mutableUiState.value = state.copy(
            measures = updatedMeasures,
            message = null,
            errorMessage = null,
        )
    }

    fun deleteMeasure(measureId: Int) {
        val state = mutableUiState.value
        if (state.measures.none { it.id == measureId }) return
        mutableUiState.value = state.copy(
            measures = state.measures
                .filterNot { it.id == measureId }
                .rebuildGrid(sourceExercise),
            message = null,
            errorMessage = null,
        )
    }

    fun saveExercise(
        destinationDocumentUri: String? = null,
        playAfterSave: Boolean = false,
    ) {
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
            documentUriReadyToPlay = null,
            message = null,
            errorMessage = null,
        )
        documentJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    documentStore.write(documentUri, exercise)
                }
                sourceExercise = exercise
                val rebasedMeasures = mutableUiState.value.measures
                    .mapIndexed { index, measure ->
                        measure.copy(originalMeasureIndex = index)
                    }
                    .rebuildGrid(exercise)
                mutableUiState.value = mutableUiState.value.copy(
                    measures = rebasedMeasures,
                    sourceDocumentUri = documentUri,
                    isSaving = false,
                    documentUriReadyToPlay = documentUri.takeIf { playAfterSave },
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

    fun consumePlayRequest() {
        mutableUiState.value = mutableUiState.value.copy(
            documentUriReadyToPlay = null,
        )
    }

    fun duplicateMeasurePattern(measureId: Int) {
        val state = mutableUiState.value
        val sourceIndex = state.measures.indexOfFirst { it.id == measureId }
        if (sourceIndex < 0) return

        val source = state.measures[sourceIndex]
        val duplicate = source.copy(
            id = state.measures.nextMeasureId(),
            originalMeasureIndex = null,
            notes = source.notes.toList(),
            slots = emptyList(),
        )
        val updatedMeasures = state.measures.toMutableList().apply {
            add(sourceIndex + 1, duplicate)
        }
        updateMeasurePatterns(state, updatedMeasures)
    }

    fun clearMeasurePattern(measureId: Int) {
        val state = mutableUiState.value
        if (state.measures.none { it.id == measureId }) return
        val updatedMeasures = state.measures.map { measure ->
            if (measure.id == measureId) {
                measure.copy(notes = emptyList())
            } else {
                measure
            }
        }
        updateMeasurePatterns(state, updatedMeasures)
    }

    fun moveMeasurePatternUp(measureId: Int) {
        moveMeasurePattern(measureId, indexChange = -1)
    }

    fun moveMeasurePatternDown(measureId: Int) {
        moveMeasurePattern(measureId, indexChange = 1)
    }

    private fun moveMeasurePattern(
        measureId: Int,
        indexChange: Int,
    ) {
        val state = mutableUiState.value
        val sourceIndex = state.measures.indexOfFirst { it.id == measureId }
        if (sourceIndex < 0) return
        val destinationIndex = sourceIndex + indexChange
        if (destinationIndex !in state.measures.indices) return

        val updatedMeasures = state.measures.toMutableList().apply {
            val movedPattern = removeAt(sourceIndex)
            add(destinationIndex, movedPattern)
        }
        updateMeasurePatterns(state, updatedMeasures)
    }

    private fun updateMeasurePatterns(
        state: ExerciseEditorUiState,
        measures: List<EditorMeasureUiState>,
    ) {
        mutableUiState.value = state.copy(
            measures = measures.rebuildGrid(sourceExercise),
            message = null,
            errorMessage = null,
        )
    }

    internal fun buildEditedExercise(
        state: ExerciseEditorUiState = uiState.value,
    ): EditableExercise {
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
                notes = state.measures.toExpectedNotes(
                    ticksPerMeasure = original.ticksPerMeasure(),
                ),
                measureSubdivisions = state.measures.map(
                    EditorMeasureUiState::subdivision,
                ),
                measureMultipliers = state.measures.map(
                    EditorMeasureUiState::multiplier,
                ),
            )
        } ?: EditableExercise(
            formatVersion = ExerciseFormat.CURRENT_VERSION,
            id = state.exerciseName.toExerciseId(),
            name = state.exerciseName.trim(),
            description = "",
            tempoBpm = tempoBpm,
            timeSignature = TimeSignature(numerator = 4, denominator = 4),
            countInMeasures = 1,
            measureCount = state.measures.size,
            ticksPerQuarterNote = DEFAULT_TICKS_PER_QUARTER_NOTE,
            notes = state.measures.toExpectedNotes(
                ticksPerMeasure = DEFAULT_TICKS_PER_MEASURE,
            ),
            measureSubdivisions = state.measures.map(
                EditorMeasureUiState::subdivision,
            ),
            measureMultipliers = state.measures.map(
                EditorMeasureUiState::multiplier,
            ),
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

private fun EditableExercise.editorNotesForMeasure(
    measureIndex: Int,
): List<EditorNoteUiState> {
    val ticksPerMeasure = ticksPerMeasure()
    val measureStart = measureIndex * ticksPerMeasure
    val measureEnd = measureStart + ticksPerMeasure
    return notes
        .filter { note -> note.positionTicks in measureStart until measureEnd }
        .map { note ->
            EditorNoteUiState(
                positionWithinMeasureTicks = note.positionTicks - measureStart,
                accent = note.accent,
                targetIntensity = note.targetIntensity,
            )
        }
}

private fun EditableExercise.ticksPerMeasure(): Long =
    ticksPerQuarterNote.toLong() *
        timeSignature.numerator *
        4L /
        timeSignature.denominator

private fun List<EditorMeasureUiState>.toExpectedNotes(
    ticksPerMeasure: Long,
): List<ExpectedNote> = flatMapIndexed { measureIndex, measure ->
    measure.notes.map { note ->
        ExpectedNote(
            positionTicks =
                measureIndex * ticksPerMeasure + note.positionWithinMeasureTicks,
            accent = note.accent,
            targetIntensity = note.targetIntensity,
        )
    }
}

private fun List<EditorMeasureUiState>.rebuildGrid(
    sourceExercise: EditableExercise?,
): List<EditorMeasureUiState> = mapIndexed { editedIndex, measure ->
    EditorRhythmGrid.buildMeasure(
        id = measure.id,
        editedMeasureIndex = editedIndex,
        originalMeasureIndex = measure.originalMeasureIndex,
        subdivision = measure.subdivision,
        multiplier = measure.multiplier,
        ticksPerQuarterNote = sourceExercise?.ticksPerQuarterNote
            ?: EditorRhythmGrid.DEFAULT_TICKS_PER_QUARTER_NOTE,
        timeSignature = sourceExercise?.timeSignature
            ?: EditorRhythmGrid.DEFAULT_TIME_SIGNATURE,
        notes = measure.notes,
    )
}.withExpandedMeasureRanges()

private fun List<EditorMeasureUiState>.nextMeasureId(): Int =
    (maxOfOrNull(EditorMeasureUiState::id) ?: 0) + 1

private const val DEFAULT_TICKS_PER_MEASURE = 1_920L
