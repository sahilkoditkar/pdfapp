package dev.pdflens.scan

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Offset
import dev.pdflens.ui.Quad
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * CameraX [ImageAnalysis.Analyzer] that runs a downscaled edge detection on
 * each preview frame and reports the detected document quad (or null) in
 * image-analysis-frame coordinates [0..1] x [0..1]. The caller maps to canvas
 * coordinates.
 *
 * Note: the camera sensor is rotated relative to the display, so the analyzer
 * accounts for [imageInfo.rotationDegrees] and emits coordinates in the
 * upright frame matching what the user sees.
 */
class LiveEdgeAnalyzer(
    private val onQuad: (NormalizedQuad?) -> Unit,
) : ImageAnalysis.Analyzer {

    data class NormalizedQuad(
        val tl: Offset,
        val tr: Offset,
        val br: Offset,
        val bl: Offset,
    ) {
        fun toQuad(width: Float, height: Float): Quad = Quad(
            Offset(tl.x * width, tl.y * height),
            Offset(tr.x * width, tr.y * height),
            Offset(br.x * width, br.y * height),
            Offset(bl.x * width, bl.y * height),
        )
    }

    private var lastEmitNs: Long = 0
    private val minIntervalNs = 120_000_000L // ~8 fps cap

    override fun analyze(image: ImageProxy) {
        val now = System.nanoTime()
        if (now - lastEmitNs < minIntervalNs) {
            image.close()
            return
        }
        lastEmitNs = now
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val width = image.width
            val height = image.height
            val rowStride = plane.rowStride

            val yBytes = ByteArray(rowStride * height)
            buffer.rewind()
            buffer.get(yBytes, 0, minOf(yBytes.size, buffer.remaining()))

            val full = Mat(height, width, org.opencv.core.CvType.CV_8UC1)
            // Copy row-by-row to handle rowStride != width.
            val rowBytes = ByteArray(width)
            for (r in 0 until height) {
                System.arraycopy(yBytes, r * rowStride, rowBytes, 0, width)
                full.put(r, 0, rowBytes)
            }

            // Rotate to display orientation so coords are in upright frame.
            val rotated = when (image.imageInfo.rotationDegrees) {
                90 -> Mat().also { Core_rotate(full, it, 0) }
                180 -> Mat().also { Core_rotate(full, it, 1) }
                270 -> Mat().also { Core_rotate(full, it, 2) }
                else -> full
            }
            if (rotated !== full) full.release()

            val outW = rotated.cols()
            val outH = rotated.rows()
            val targetW = 480
            val scale = if (outW > targetW) targetW.toDouble() / outW else 1.0
            val small = if (scale < 1.0) Mat().also {
                Imgproc.resize(rotated, it, Size(outW * scale, outH * scale))
            } else rotated

            Imgproc.GaussianBlur(small, small, Size(5.0, 5.0), 0.0)
            val edges = Mat()
            Imgproc.Canny(small, edges, 60.0, 180.0)

            val contours = mutableListOf<MatOfPoint>()
            Imgproc.findContours(
                edges, contours, Mat(),
                Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE,
            )

            val sw = small.cols().toDouble()
            val sh = small.rows().toDouble()
            val imgArea = sw * sh

            val candidate = contours
                .asSequence()
                .map { c ->
                    val c2f = MatOfPoint2f(*c.toArray())
                    val peri = Imgproc.arcLength(c2f, true)
                    val approx = MatOfPoint2f()
                    Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
                    approx
                }
                .filter { it.total() == 4L }
                .maxByOrNull { Imgproc.contourArea(MatOfPoint(*it.toArray())) }

            val area = candidate?.let { Imgproc.contourArea(MatOfPoint(*it.toArray())) } ?: 0.0
            if (candidate == null || area < imgArea * 0.18) {
                onQuad(null)
            } else {
                val pts = candidate.toArray().map {
                    Offset((it.x / sw).toFloat(), (it.y / sh).toFloat())
                }
                val tl = pts.minBy { it.x + it.y }
                val br = pts.maxBy { it.x + it.y }
                val tr = pts.minBy { it.y - it.x }
                val bl = pts.maxBy { it.y - it.x }
                onQuad(NormalizedQuad(tl, tr, br, bl))
            }

            edges.release()
            if (small !== rotated) small.release()
            rotated.release()
        } catch (_: Throwable) {
            // Best-effort: skip a frame on any decode/processing error.
        } finally {
            image.close()
        }
    }

    /** OpenCV Core.rotate wrapper; using the constant ints to avoid an extra import. */
    private fun Core_rotate(src: Mat, dst: Mat, code: Int) {
        org.opencv.core.Core.rotate(src, dst, code)
    }
}
