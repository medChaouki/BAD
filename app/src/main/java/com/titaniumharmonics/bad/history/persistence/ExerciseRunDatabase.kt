package com.titaniumharmonics.bad.history.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ExerciseRunEntity::class],
    version = ExerciseRunDatabase.DATABASE_VERSION,
    exportSchema = true,
)
abstract class ExerciseRunDatabase : RoomDatabase() {
    abstract fun exerciseRunDao(): ExerciseRunDao

    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "exercise-runs.db"

        @Volatile
        private var instance: ExerciseRunDatabase? = null

        fun getInstance(context: Context): ExerciseRunDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                ExerciseRunDatabase::class.java,
                DATABASE_NAME,
            ).build().also { database -> instance = database }
        }

        internal fun clearInstanceForTest() {
            instance = null
        }
    }
}
