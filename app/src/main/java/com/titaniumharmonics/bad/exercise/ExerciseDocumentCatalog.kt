package com.titaniumharmonics.bad.exercise

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

class ExerciseDocumentCatalog(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun rememberDefaultFolderDocument(documentUri: Uri) {
        if (!documentUri.isInsideDefaultExerciseFolder()) return
        runCatching {
            applicationContext.contentResolver.takePersistableUriPermission(
                documentUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val updatedUris = documentUris() + documentUri.toString()
        preferences.edit()
            .putStringSet(DOCUMENT_URIS_KEY, updatedUris)
            .apply()
    }

    fun forgetDocument(documentUri: String) {
        preferences.edit()
            .putStringSet(DOCUMENT_URIS_KEY, documentUris() - documentUri)
            .apply()
    }

    fun documentUris(): Set<String> =
        preferences.getStringSet(DOCUMENT_URIS_KEY, emptySet())
            ?.toSet()
            .orEmpty()

    private fun Uri.isInsideDefaultExerciseFolder(): Boolean {
        val documentId = runCatching {
            DocumentsContract.getDocumentId(this)
        }.getOrNull() ?: return false
        return documentId.substringBeforeLast('/') == DEFAULT_FOLDER_DOCUMENT_ID
    }

    private companion object {
        const val PREFERENCES_NAME = "exercise_document_catalog"
        const val DOCUMENT_URIS_KEY = "document_uris"
        const val DEFAULT_FOLDER_DOCUMENT_ID =
            "primary:${ExerciseStorageInitializer.SHARED_FOLDER_DOCUMENT_PATH}"
    }
}
