package dev.pdflens.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import dev.pdflens.scan.PerspectiveTransform
import dev.pdflens.ui.CapturedPage
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Assembles a multi-page PDF from captured pages. Each page is rectified via
 * [PerspectiveTransform] and laid out at A4 (595x842 pt at 72dpi) preserving
 * aspect ratio.
 */
object PdfBuilder {

    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842

    fun build(context: Context, pages: List<CapturedPage>): File {
        val doc = PdfDocument()
        try {
            pages.forEachIndexed { index, page ->
                val bmp = PerspectiveTransform.warpAndEnhance(page.rawPath, page.quad)
                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, index + 1).create()
                val pdfPage = doc.startPage(pageInfo)
                drawFitted(pdfPage.canvas, bmp)
                doc.finishPage(pdfPage)
                bmp.recycle()
            }
            val outDir = File(context.filesDir, "pdfs").apply { mkdirs() }
            val file = File(outDir, "scan_${stamp()}.pdf")
            FileOutputStream(file).use { doc.writeTo(it) }
            return file
        } finally {
            doc.close()
        }
    }

    private fun drawFitted(canvas: Canvas, bmp: Bitmap) {
        val cw = canvas.width.toFloat()
        val ch = canvas.height.toFloat()
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        val scale = minOf(cw / bw, ch / bh)
        val drawW = bw * scale
        val drawH = bh * scale
        val left = ((cw - drawW) / 2).toInt()
        val top = ((ch - drawH) / 2).toInt()
        val dst = Rect(left, top, (left + drawW).toInt(), (top + drawH).toInt())
        canvas.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}
