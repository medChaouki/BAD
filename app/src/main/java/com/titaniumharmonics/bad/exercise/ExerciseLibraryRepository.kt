package com.titaniumharmonics.bad.exercise

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import java.io.File

data class ExerciseLibraryItem(
    val documentUri: String,
    val fileName: String,
    val exerciseName: String,
    val tempoBpm: Double,
    val patternCount: Int,
    val expandedMeasureCount: Int,
)

interface ExerciseLibraryRepository {
    @WorkerThread
    fun loadExercises(): List<ExerciseLibraryItem>

    @WorkerThread
    fun deleteExercise(documentUri: String)
}

class DefaultExerciseLibraryRepository(
    private val context: Context,
) : ExerciseLibraryRepository {
    private val documentStore: ExerciseDocumentStore =
        ContentResolverExerciseDocumentStore(context.contentResolver)
    private val documentCatalog = ExerciseDocumentCatalog(context)

    override fun loadExercises(): List<ExerciseLibraryItem> {
        ExerciseStorageInitializer(context).initialize()
        val items = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            loadSharedDownloadsExercises()
        } else {
            loadPrivateFallbackExercises()
        }
        val catalogItems = documentCatalog.documentUris().mapNotNull { documentUri ->
            val uri = Uri.parse(documentUri)
            val fileName = runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()
            if (fileName == null) {
                documentCatalog.forgetDocument(documentUri)
                null
            } else {
                validatedLibraryItem(uri, fileName).also { item ->
                    if (item == null) {
                        documentCatalog.forgetDocument(documentUri)
                    }
                }
            }
        }
        return (items + catalogItems)
            .distinctBy { it.fileName.lowercase() }
            .sortedBy { it.fileName.lowercase() }
    }

    override fun deleteExercise(documentUri: String) {
        documentStore.read(documentUri)
        val uri = Uri.parse(documentUri)
        val deleted = when (uri.scheme) {
            "file" -> uri.path?.let(::File)?.delete() == true
            else -> {
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    DocumentsContract.deleteDocument(context.contentResolver, uri)
                } else {
                    context.contentResolver.delete(uri, null, null) > 0
                }
            }
        }
        check(deleted) {
            "The storage provider did not delete the exercise file."
        }
        documentCatalog.forgetDocument(documentUri)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun loadSharedDownloadsExercises(): List<ExerciseLibraryItem> {
        val contentResolver = context.contentResolver
        val downloadsCollection = MediaStore.Downloads.getContentUri(
            MediaStore.VOLUME_EXTERNAL_PRIMARY,
        )
        return contentResolver.query(
            downloadsCollection,
            arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
            ),
            "${MediaStore.Downloads.RELATIVE_PATH} = ?",
            arrayOf(ExerciseStorageInitializer.SHARED_RELATIVE_PATH),
            "${MediaStore.Downloads.DISPLAY_NAME} COLLATE NOCASE ASC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Downloads.DISPLAY_NAME,
            )
            buildList {
                while (cursor.moveToNext()) {
                    val documentUri = ContentUris.withAppendedId(
                        downloadsCollection,
                        cursor.getLong(idColumn),
                    )
                    validatedLibraryItem(
                        documentUri = documentUri,
                        fileName = cursor.getString(nameColumn),
                    )?.let(::add)
                }
            }
        } ?: emptyList()
    }

    private fun loadPrivateFallbackExercises(): List<ExerciseLibraryItem> {
        val externalDownloads = context.getExternalFilesDir(
            Environment.DIRECTORY_DOWNLOADS,
        ) ?: return emptyList()
        val exerciseDirectory = File(
            externalDownloads,
            ExerciseStorageInitializer.PRIVATE_RELATIVE_PATH,
        )
        return exerciseDirectory.listFiles()
            .orEmpty()
            .asSequence()
            .filter(File::isFile)
            .mapNotNull { file ->
                validatedLibraryItem(
                    documentUri = Uri.fromFile(file),
                    fileName = file.name,
                )
            }
            .toList()
    }

    private fun validatedLibraryItem(
        documentUri: Uri,
        fileName: String,
    ): ExerciseLibraryItem? {
        val exercise = runCatching {
            documentStore.read(documentUri.toString())
        }.getOrNull() ?: return null
        return exercise.toLibraryItem(
            documentUri = documentUri.toString(),
            fileName = fileName,
        )
    }
}

internal fun EditableExercise.toLibraryItem(
    documentUri: String,
    fileName: String,
): ExerciseLibraryItem = ExerciseLibraryItem(
    documentUri = documentUri,
    fileName = fileName,
    exerciseName = name,
    tempoBpm = tempoBpm,
    patternCount = measureCount,
    expandedMeasureCount = expandedMeasureCount,
)
