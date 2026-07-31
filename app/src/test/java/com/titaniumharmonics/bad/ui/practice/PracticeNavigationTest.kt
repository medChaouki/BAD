package com.titaniumharmonics.bad.ui.practice

import org.junit.Assert.assertEquals
import org.junit.Test

class PracticeNavigationTest {
    @Test
    fun navigateAwayFromPracticeForEditing_unloadsBeforeNavigation() {
        val events = mutableListOf<String>()

        navigateAwayFromPracticeForEditing(
            unloadExercise = { events += "unload" },
            navigate = { events += "navigate" },
        )

        assertEquals(listOf("unload", "navigate"), events)
    }
}
