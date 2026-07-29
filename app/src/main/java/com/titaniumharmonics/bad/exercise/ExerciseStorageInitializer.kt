package com.titaniumharmonics.bad.exercise

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import java.io.File

class ExerciseStorageInitializer(
    private val context: Context,
) {
    @WorkerThread
    fun initialize(): Uri? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            initializeSharedDownloadsFolder() ?: initializePrivateFallback()
        } else {
            initializePrivateFallback()
        }
    }.getOrNull()

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun initializeSharedDownloadsFolder(): Uri? {
        return runCatching {
            val contentResolver = context.contentResolver
            val downloadsCollection = MediaStore.Downloads.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY,
            )
            val sampleExists = contentResolver.query(
                downloadsCollection,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME} = ? AND " +
                    "${MediaStore.Downloads.RELATIVE_PATH} = ?",
                arrayOf(SAMPLE_FILE_NAME, SHARED_RELATIVE_PATH),
                null,
            )?.use { cursor -> cursor.moveToFirst() } == true

            if (!sampleExists) {
                copySampleToSharedDownloads(downloadsCollection)
            }

            DocumentsContract.buildDocumentUri(
                EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY,
                "$PRIMARY_STORAGE_DOCUMENT_ID:$SHARED_FOLDER_DOCUMENT_PATH",
            )
        }.getOrNull()
    }

    private fun copySampleToSharedDownloads(downloadsCollection: Uri) {
        val contentResolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, SAMPLE_FILE_NAME)
            put(MediaStore.Downloads.MIME_TYPE, EXERCISE_MIME_TYPE)
            put(MediaStore.Downloads.RELATIVE_PATH, SHARED_RELATIVE_PATH)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val sampleUri = contentResolver.insert(downloadsCollection, values)
            ?: throw IllegalStateException("Unable to create the default exercise file.")

        try {
            contentResolver.openOutputStream(sampleUri, "w")?.use { outputStream ->
                context.assets.open("$ASSET_DIRECTORY/$SAMPLE_FILE_NAME").use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw IllegalStateException("Unable to write the default exercise file.")

            contentResolver.update(
                sampleUri,
                ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                },
                null,
                null,
            )
        } catch (exception: Exception) {
            contentResolver.delete(sampleUri, null, null)
            throw exception
        }
    }

    private fun initializePrivateFallback(): Uri? {
        val externalDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: return null
        val exerciseDirectory = File(externalDownloads, PRIVATE_RELATIVE_PATH)
        if (!exerciseDirectory.exists() && !exerciseDirectory.mkdirs()) {
            return null
        }

        val sampleFile = File(exerciseDirectory, SAMPLE_FILE_NAME)
        if (!sampleFile.exists()) {
            context.assets.open("$ASSET_DIRECTORY/$SAMPLE_FILE_NAME").use { inputStream ->
                sampleFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }

        val documentPath = exerciseDirectory.absolutePath
            .substringAfter("/storage/emulated/0/", missingDelimiterValue = "")
        if (documentPath.isBlank()) return null
        return DocumentsContract.buildDocumentUri(
            EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY,
            "$PRIMARY_STORAGE_DOCUMENT_ID:$documentPath",
        )
    }

    private companion object {
        const val ASSET_DIRECTORY = "exercises"
        const val SAMPLE_FILE_NAME = "basic-quarter-notes.json"
        const val EXERCISE_MIME_TYPE = "application/json"
        const val SHARED_RELATIVE_PATH = "Download/B.A.D/assets/"
        const val SHARED_FOLDER_DOCUMENT_PATH = "Download/B.A.D/assets"
        const val PRIVATE_RELATIVE_PATH = "B.A.D/assets"
        const val EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY =
            "com.android.externalstorage.documents"
        const val PRIMARY_STORAGE_DOCUMENT_ID = "primary"
    }
}
