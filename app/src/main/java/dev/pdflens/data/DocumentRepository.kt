package dev.pdflens.data

import android.content.Context
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
        ?.map {
            // Page count from the PDF would require ParcelFileDescriptor + PdfRenderer;
            // skipped here for the scaffold — show the filename only.
            SavedPdf(name = it.nameWithoutExtension, path = it.absolutePath, pageCount = 0)
        }
        ?: emptyList()
}
