package com.titaniumharmonics.bad.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUiStateTest {
    @Test
    fun selectingExerciseForPractice_returnsToPracticeWithDocumentToLoad() {
        val libraryState = AppUiState().openExerciseLibrary(
            ExerciseLibraryPurpose.PRACTICE,
        )

        val selectedState = libraryState.openLibraryExercise(
            "content://exercise/practice",
        )

        assertEquals(AppDestination.PRACTICE, selectedState.destination)
        assertEquals(
            "content://exercise/practice",
            selectedState.practiceDocumentUriToLoad,
        )
        assertFalse(selectedState.startPracticeAfterLoad)
        assertNull(selectedState.editorDocumentUri)
    }

    @Test
    fun selectingExerciseForModify_opensEditorAndReturnsToLibrary() {
        val libraryState = AppUiState().openExerciseLibrary(
            ExerciseLibraryPurpose.MODIFY,
        )

        val selectedState = libraryState.openLibraryExercise(
            "content://exercise/modify",
        )

        assertEquals(AppDestination.EXERCISE_EDITOR, selectedState.destination)
        assertEquals(
            "content://exercise/modify",
            selectedState.editorDocumentUri,
        )
        assertEquals(
            AppDestination.EXERCISE_LIBRARY,
            selectedState.editorReturnDestination,
        )
        assertNull(selectedState.practiceDocumentUriToLoad)
    }

    @Test
    fun playingSavedEditorExercise_returnsToPracticeAndRequestsAutoStart() {
        val editorState = AppUiState(
            destination = AppDestination.EXERCISE_EDITOR,
            editorDocumentUri = "content://exercise/editor",
        )

        val practiceState = editorState.playEditorExercise(
            "content://exercise/saved",
        )

        assertEquals(AppDestination.PRACTICE, practiceState.destination)
        assertEquals(
            "content://exercise/saved",
            practiceState.practiceDocumentUriToLoad,
        )
        assertTrue(practiceState.startPracticeAfterLoad)
        assertNull(practiceState.editorDocumentUri)
    }
}
