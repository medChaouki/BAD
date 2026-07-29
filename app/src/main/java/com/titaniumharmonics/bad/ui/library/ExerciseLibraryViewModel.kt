package com.titaniumharmonics.bad.ui.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.titaniumharmonics.bad.exercise.DefaultExerciseLibraryRepository
import com.titaniumharmonics.bad.exercise.ExerciseLibraryItem
import com.titaniumharmonics.bad.exercise.ExerciseLibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExerciseLibraryViewModel(
    private val repository: ExerciseLibraryRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ExerciseLibraryUiState())
    val uiState: StateFlow<ExerciseLibraryUiState> = mutableUiState.asStateFlow()

    private var libraryJob: Job? = null

    fun refresh() {
        libraryJob?.cancel()
        mutableUiState.value = mutableUiState.value.copy(
            isLoading = true,
            errorMessage = null,
        )
        libraryJob = viewModelScope.launch {
            try {
                val exercises = withContext(Dispatchers.IO) {
                    repository.loadExercises()
                }
                applyExercises(exercises)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                mutableUiState.value = mutableUiState.value.copy(
                    isLoading = false,
                    errorMessage = exception.message ?: "Unable to load exercise files.",
                )
            }
        }
    }

    fun requestDeletion(documentUri: String) {
        val exercise = mutableUiState.value.exercises.firstOrNull {
            it.documentUri == documentUri
        } ?: return
        mutableUiState.value = mutableUiState.value.copy(
            exercisePendingDeletion = exercise,
            errorMessage = null,
        )
    }

    internal fun applyExercises(
        exercises: List<ExerciseLibraryItem>,
    ) {
        mutableUiState.value = ExerciseLibraryUiState(
            exercises = exercises,
        )
    }

    fun cancelDeletion() {
        if (mutableUiState.value.deletingDocumentUri != null) return
        mutableUiState.value = mutableUiState.value.copy(
            exercisePendingDeletion = null,
        )
    }

    fun confirmDeletion() {
        if (libraryJob?.isActive == true) return
        val exercise = mutableUiState.value.exercisePendingDeletion ?: return
        mutableUiState.value = mutableUiState.value.copy(
            deletingDocumentUri = exercise.documentUri,
            errorMessage = null,
        )
        libraryJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.deleteExercise(exercise.documentUri)
                }
                mutableUiState.value = mutableUiState.value.copy(
                    exercises = mutableUiState.value.exercises.filterNot {
                        it.documentUri == exercise.documentUri
                    },
                    deletingDocumentUri = null,
                    exercisePendingDeletion = null,
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                mutableUiState.value = mutableUiState.value.copy(
                    deletingDocumentUri = null,
                    exercisePendingDeletion = null,
                    errorMessage = exception.message ?: "Unable to delete exercise file.",
                )
            }
        }
    }

    override fun onCleared() {
        libraryJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val applicationContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T {
                    require(modelClass.isAssignableFrom(ExerciseLibraryViewModel::class.java))
                    return ExerciseLibraryViewModel(
                        repository = DefaultExerciseLibraryRepository(applicationContext),
                    ) as T
                }
            }
        }
    }
}
