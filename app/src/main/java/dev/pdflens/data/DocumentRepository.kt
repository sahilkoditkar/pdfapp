package dev.pdflens.data

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri

data class SavedPdf(
    val name: String,
    val uri: Uri,
    val pageCount: Int,
    val sizeBytes: Long,
    val dateModifiedMs: Long,
)

enum class SortOrder { DATE_DESC, DATE_ASC, NAME_ASC, NAME_DESC, SIZE_DESC, SIZE_ASC }

fun listSavedPdfs(context: Context): List<SavedPdf> = PdfStorage.list(context)

fun List<SavedPdf>.sortedBy(order: SortOrder): List<SavedPdf> = when (order) {
    SortOrder.DATE_DESC -> sortedByDescending { it.dateModifiedMs }
    SortOrder.DATE_ASC -> sortedBy { it.dateModifiedMs }
    SortOrder.NAME_ASC -> sortedBy { it.name.lowercase() }
    SortOrder.NAME_DESC -> sortedByDescending { it.name.lowercase() }
    SortOrder.SIZE_DESC -> sortedByDescending { it.sizeBytes }
    SortOrder.SIZE_ASC -> sortedBy { it.sizeBytes }
}

internal fun readPageCount(context: Context, uri: Uri): Int = try {
    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
        PdfRenderer(pfd).use { it.pageCount }
    } ?: 0
} catch (_: Throwable) {
    0
}
