package dev.pdflens.data

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

data class SavedPdf(
    val name: String,
    val path: String,
    val pageCount: Int,
)

fun listSavedPdfs(context: Context): List<SavedPdf> {
    val dir = File(context.filesDir, "pdfs")
    if (!dir.exists()) return emptyList()
    return dir.listFiles { f -> f.extension.equals("pdf", ignoreCase = true) }
        ?.sortedByDescending { it.lastModified() }
        ?.map { file ->
            SavedPdf(
                name = file.nameWithoutExtension,
                path = file.absolutePath,
                pageCount = readPageCount(file),
            )
        }
        ?: emptyList()
}

private fun readPageCount(file: File): Int = try {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { it.pageCount }
    }
} catch (_: Throwable) {
    0
}
