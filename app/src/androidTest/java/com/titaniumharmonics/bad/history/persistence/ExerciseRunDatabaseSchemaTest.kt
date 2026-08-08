package com.titaniumharmonics.bad.history.persistence

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseRunDatabaseSchemaTest {
    private val databaseName = "exercise-run-schema-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ExerciseRunDatabase::class.java,
    )

    @Test
    fun exportedVersionOneSchemaOpensWithoutTouchingExistingPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("migration-sentinel", Context.MODE_PRIVATE)
        preferences.edit().putString("value", "preserve-me").commit()
        helper.createDatabase(databaseName, ExerciseRunDatabase.DATABASE_VERSION).close()

        val database = Room.databaseBuilder(
            context,
            ExerciseRunDatabase::class.java,
            databaseName,
        ).build()
        database.openHelper.writableDatabase
        database.close()

        assertEquals("preserve-me", preferences.getString("value", null))
        context.deleteDatabase(databaseName)
        preferences.edit().clear().commit()
    }
}
