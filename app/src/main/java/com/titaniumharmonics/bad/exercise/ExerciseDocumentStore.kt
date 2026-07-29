package com.titaniumharmonics.bad.exercise

import android.content.ContentResolver
import android.net.Uri
import androidx.annotation.WorkerThread

interface ExerciseDocumentStore {
    @WorkerThread
    fun read(documentUri: String): Exercise

    @WorkerThread
    fun write(documentUri: String, exercise: Exercise)
}

class ContentResolverExerciseDocumentStore(
    private val contentResolver: ContentResolver,
) : ExerciseDocumentStore {
    override fun read(documentUri: String): Exercise {
        val uri = Uri.parse(documentUri)
        val jsonText = contentResolver
            .openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw IllegalArgumentException("Unable to open the selected exercise file.")

        return ExerciseJsonCodec.decode(jsonText)
    }

    override fun write(documentUri: String, exercise: Exercise) {
        val uri = Uri.parse(documentUri)
        val outputStream = contentResolver.openOutputStream(uri, "wt")
            ?: throw IllegalArgumentException("Unable to write the selected exercise file.")

        outputStream.bufferedWriter().use { writer ->
            writer.write(ExerciseJsonCodec.encode(exercise))
        }
    }
}
