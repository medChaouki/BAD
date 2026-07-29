package com.titaniumharmonics.bad.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.titaniumharmonics.bad.exercise.ExerciseStorageInitializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val mutableUiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            val defaultFolderUri = withContext(Dispatchers.IO) {
                ExerciseStorageInitializer(application).initialize()
            }
            mutableUiState.value = mutableUiState.value.copy(
                defaultExerciseFolderUri = defaultFolderUri?.toString(),
                storageInitializationComplete = true,
            )
        }
    }

    fun createExercise() {
        mutableUiState.value = mutableUiState.value.copy(
            destination = AppDestination.EXERCISE_EDITOR,
            editorDocumentUri = null,
        )
    }

    fun modifyExercise(documentUri: String) {
        mutableUiState.value = mutableUiState.value.copy(
            destination = AppDestination.EXERCISE_EDITOR,
            editorDocumentUri = documentUri,
        )
    }

    fun closeExerciseEditor() {
        mutableUiState.value = mutableUiState.value.copy(
            destination = AppDestination.PRACTICE,
            editorDocumentUri = null,
        )
    }
}
