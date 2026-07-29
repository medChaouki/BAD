package com.titaniumharmonics.bad.exercise

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract

data class OpenExerciseDocumentRequest(
    val mimeTypes: Array<String>,
    val initialFolderUri: Uri?,
)

data class CreateExerciseDocumentRequest(
    val fileName: String,
    val initialFolderUri: Uri?,
)

class OpenExerciseDocumentContract :
    ActivityResultContract<OpenExerciseDocumentRequest, Uri?>() {
    override fun createIntent(
        context: Context,
        input: OpenExerciseDocumentRequest,
    ): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = if (input.mimeTypes.size == 1) input.mimeTypes[0] else "*/*"
        if (input.mimeTypes.size > 1) {
            putExtra(Intent.EXTRA_MIME_TYPES, input.mimeTypes)
        }
        input.initialFolderUri?.let { initialFolderUri ->
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialFolderUri)
        }
        addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
        )
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
}

class CreateExerciseDocumentContract :
    ActivityResultContract<CreateExerciseDocumentRequest, Uri?>() {
    override fun createIntent(
        context: Context,
        input: CreateExerciseDocumentRequest,
    ): Intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = EXERCISE_MIME_TYPE
        putExtra(Intent.EXTRA_TITLE, input.fileName)
        input.initialFolderUri?.let { initialFolderUri ->
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialFolderUri)
        }
        addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
        )
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data?.takeIf { resultCode == Activity.RESULT_OK }

    private companion object {
        const val EXERCISE_MIME_TYPE = "application/json"
    }
}
