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

    const val SCAN_PREFIX = "scan_"
    const val IMPORT_PREFIX = "imported_"
    private val PREFIXES = listOf(SCAN_PREFIX, IMPORT_PREFIX)

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

    /** Lists scans this app produced or imported (filename starts with one of [PREFIXES]). */
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
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
        val nameClause = PREFIXES.joinToString(" OR ") {
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        }
        val selection =
            "${MediaStore.MediaColumns.MIME_TYPE} = ? AND " +
                "($nameClause) AND " +
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val args = buildList {
            add("application/pdf")
            PREFIXES.forEach { add("$it%") }
            add("${Environment.DIRECTORY_DOCUMENTS}/%")
        }.toTypedArray()
        val sort = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        val out = mutableListOf<SavedPdf>()
        resolver.query(collection, projection, selection, args, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val name = c.getString(nameCol)
                val uri = Uri.withAppendedPath(collection, id.toString())
                out += SavedPdf(
                    name = name.removeSuffix(".pdf"),
                    uri = uri,
                    pageCount = readPageCount(context, uri),
                    sizeBytes = c.getLong(sizeCol),
                    // MediaStore stores DATE_MODIFIED in seconds since epoch.
                    dateModifiedMs = c.getLong(dateCol) * 1000L,
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
                PREFIXES.any { f.name.startsWith(it) }
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
                    sizeBytes = file.length(),
                    dateModifiedMs = file.lastModified(),
                )
            }
            ?: emptyList()
    }

    fun delete(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.delete(uri, null, null) > 0
    }.getOrDefault(false)

    /**
     * Renames the file at [uri] to [newDisplayName] (without the `.pdf` suffix —
     * this function appends it). Returns the new content URI on success.
     */
    fun rename(context: Context, uri: Uri, newDisplayName: String): Uri? {
        val sanitized = sanitizeFileName(newDisplayName).ifEmpty { return null }
        val newFileName = ensurePrefix(sanitized) + ".pdf"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            renameViaMediaStore(context, uri, newFileName)
        } else {
            renameViaLegacyFile(context, uri, newFileName)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun renameViaMediaStore(context: Context, uri: Uri, newFileName: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, newFileName)
        }
        val rows = runCatching { context.contentResolver.update(uri, values, null, null) }
            .getOrDefault(0)
        return if (rows > 0) uri else null
    }

    private fun renameViaLegacyFile(context: Context, uri: Uri, newFileName: String): Uri? {
        // FileProvider URIs map back to a file under Documents/. Since we own
        // the path config we can locate it by display name.
        val resolver = context.contentResolver
        val cursor = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
        val oldName = cursor?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: return null
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val oldFile = File(dir, oldName)
        val newFile = File(dir, newFileName)
        if (newFile.exists()) return null
        if (!oldFile.renameTo(newFile)) return null
        MediaScannerConnection.scanFile(
            context,
            arrayOf(oldFile.absolutePath, newFile.absolutePath),
            arrayOf("application/pdf", "application/pdf"),
            null,
        )
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            newFile,
        )
    }

    private fun sanitizeFileName(input: String): String =
        input.trim().replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_")

    private fun ensurePrefix(name: String): String =
        if (PREFIXES.any { name.startsWith(it) }) name else "$SCAN_PREFIX$name"
}
