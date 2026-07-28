package com.titaniumharmonics.bad.exercise

import android.content.res.AssetManager
import androidx.annotation.WorkerThread

class AssetExerciseLoader(
    private val assetManager: AssetManager,
) {
    @WorkerThread
    fun load(assetFileName: String): Exercise {
        require(ASSET_FILE_NAME_PATTERN.matches(assetFileName)) {
            "Invalid exercise asset file name: $assetFileName"
        }

        val jsonText = assetManager
            .open("$EXERCISE_ASSET_DIRECTORY/$assetFileName")
            .bufferedReader()
            .use { it.readText() }

        return ExerciseJsonCodec.decode(jsonText)
    }

    private companion object {
        const val EXERCISE_ASSET_DIRECTORY = "exercises"
        val ASSET_FILE_NAME_PATTERN = Regex("[a-z0-9][a-z0-9-]*\\.json")
    }
}
