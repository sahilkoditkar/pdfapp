package dev.pdflens.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream

/**
 * Saves and enumerates PDFs in the user's public `Documents/` folder so they're
 * visible from any file manager and survive uninstalls. Uses MediaStore on
 * API 29+ and the legacy public-storage path on API 24-28.
 */
object PdfStorage {

    private const val FILENAME_PREFIX = "scan_"

    /**
     * Creates a new PDF in `Documents/` and lets [write] stream bytes into it.
     * Returns a content-resolvable [Uri] for the saved file. The caller is
     * responsible for ensuring [WRITE_EXTERNAL_STORAGE] is granted on API ≤ 28.
     */
    fun create(
        context: Context,
        displayName: String,
        write: (OutputStream) -> Unit,
    ): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createViaMediaStore(context, displayName, write)
        } else {
            createViaLegacyFile(context, displayName, write)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createViaMediaStore(
        context: Context,
        displayName: String,
        write: (OutputStream) -> Unit,
    ): Uri? {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return null
        return runCatching {
            resolver.openOutputStream(uri)?.use(write)
                ?: error("openOutputStream returned null")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        }.getOrElse {
            resolver.delete(uri, null, null)
            null
        }
    }

    private fun createViaLegacyFile(
        context: Context,
        displayName: String,
        write: (OutputStream) -> Unit,
    ): Uri? {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = File(dir, displayName)
        return runCatching {
            file.outputStream().use(write)
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("application/pdf"), null)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrElse {
            file.delete()
            null
        }
    }

    /** Lists scans this app produced (filename starts with [FILENAME_PREFIX]). */
    fun list(context: Context): List<SavedPdf> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listViaMediaStore(context)
        } else {
            listViaLegacyFile(context)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun listViaMediaStore(context: Context): List<SavedPdf> {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
        )
        val selection =
            "${MediaStore.MediaColumns.MIME_TYPE} = ? AND " +
                "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND " +
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = arrayOf(
            "application/pdf",
            "$FILENAME_PREFIX%",
            "${Environment.DIRECTORY_DOCUMENTS}/%",
        )
        val sort = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        val out = mutableListOf<SavedPdf>()
        resolver.query(collection, projection, selection, args, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val name = c.getString(nameCol)
                val uri = Uri.withAppendedPath(collection, id.toString())
                out += SavedPdf(
                    name = name.removeSuffix(".pdf"),
                    uri = uri,
                    pageCount = readPageCount(context, uri),
                )
            }
        }
        return out
    }

    private fun listViaLegacyFile(context: Context): List<SavedPdf> {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f ->
            f.isFile &&
                f.extension.equals("pdf", ignoreCase = true) &&
                f.name.startsWith(FILENAME_PREFIX)
        }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
                SavedPdf(
                    name = file.nameWithoutExtension,
                    uri = uri,
                    pageCount = readPageCount(context, uri),
                )
            }
            ?: emptyList()
    }

    fun delete(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.delete(uri, null, null) > 0
    }.getOrDefault(false)
}
