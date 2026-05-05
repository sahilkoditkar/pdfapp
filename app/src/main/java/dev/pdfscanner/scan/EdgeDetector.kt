package dev.pdfscanner.scan

import androidx.compose.ui.geometry.Offset
import dev.pdfscanner.ui.Quad
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import kotlin.math.sqrt

/**
 * Best-effort document edge detection. Returns null if no plausible quad is found
 * — caller should fall back to the full frame so the user can adjust manually.
 */
object EdgeDetector {

    fun detect(imagePath: String): Quad? {
        val src = Imgcodecs.imread(imagePath) ?: return null
        if (src.empty()) return null
        try {
            val gray = Mat()
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
            val edges = Mat()
            Imgproc.Canny(gray, edges, 75.0, 200.0)

            val contours = mutableListOf<MatOfPoint>()
            Imgproc.findContours(
                edges, contours, Mat(),
                Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE,
            )

            val imgArea = src.width().toDouble() * src.height()
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
                ?: return null

            val area = Imgproc.contourArea(MatOfPoint(*candidate.toArray()))
            if (area < imgArea * 0.15) return null // too small to be a document

            val pts = candidate.toArray().map { Offset(it.x.toFloat(), it.y.toFloat()) }
            return orderClockwise(pts)
        } finally {
            src.release()
        }
    }

    private fun orderClockwise(pts: List<Offset>): Quad {
        // Sum: smallest = top-left, largest = bottom-right.
        // Diff (y - x): smallest = top-right, largest = bottom-left.
        val tl = pts.minBy { it.x + it.y }
        val br = pts.maxBy { it.x + it.y }
        val tr = pts.minBy { it.y - it.x }
        val bl = pts.maxBy { it.y - it.x }
        return Quad(tl, tr, br, bl)
    }

    fun loadGrayscale(path: String): Mat = Imgcodecs.imread(path, Imgcodecs.IMREAD_GRAYSCALE)

    fun distance(a: Offset, b: Offset): Double {
        val dx = (a.x - b.x).toDouble()
        val dy = (a.y - b.y).toDouble()
        return sqrt(dx * dx + dy * dy)
    }

    fun bitmapFromMat(mat: Mat): android.graphics.Bitmap {
        val bmp = android.graphics.Bitmap.createBitmap(
            mat.cols(), mat.rows(), android.graphics.Bitmap.Config.ARGB_8888,
        )
        Utils.matToBitmap(mat, bmp)
        return bmp
    }
}
