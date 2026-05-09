package dev.pdflens.data

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri

data class SavedPdf(
    val name: String,
    val uri: Uri,
    val pageCount: Int,
)

fun listSavedPdfs(context: Context): List<SavedPdf> = PdfStorage.list(context)

internal fun readPageCount(context: Context, uri: Uri): Int = try {
    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
        PdfRenderer(pfd).use { it.pageCount }
    } ?: 0
} catch (_: Throwable) {
    0
}
