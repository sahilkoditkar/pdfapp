package dev.pdflens.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-memory cache of first-page thumbnails for the document list. Bitmaps are
 * keyed by content URI; the cache holds at most [MAX_ENTRIES] entries to bound
 * memory regardless of library size.
 */
object PdfThumbnailCache {

    private const val MAX_ENTRIES = 64
    private val cache = object : LruCache<String, Bitmap>(MAX_ENTRIES) {}

    suspend fun get(context: Context, uri: Uri, widthPx: Int): Bitmap? {
        val key = "${uri}@${widthPx}"
        cache.get(key)?.let { return it }
        val bmp = withContext(Dispatchers.IO) { renderFirstPage(context, uri, widthPx) }
            ?: return null
        cache.put(key, bmp)
        return bmp
    }

    fun invalidate(uri: Uri) {
        val prefix = uri.toString()
        // Snapshot keys to avoid concurrent-modification while we walk.
        cache.snapshot().keys.filter { it.startsWith("$prefix@") }.forEach(cache::remove)
    }

    private fun renderFirstPage(context: Context, uri: Uri, widthPx: Int): Bitmap? {
        if (widthPx <= 0) return null
        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount == 0) return@runCatching null
                    renderer.openPage(0).use { page ->
                        val ratio = page.height.toFloat() / page.width
                        val h = (widthPx * ratio).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888).apply {
                            eraseColor(Color.WHITE)
                        }
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        }.getOrNull()
    }
}
