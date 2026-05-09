package dev.pdflens.scan

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import dev.pdflens.ui.Quad
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import kotlin.math.max

object PerspectiveTransform {

    /**
     * Warp the captured image to a rectified document, then apply a light
     * "scan look" (adaptive threshold + slight sharpening) similar to Lens.
     */
    fun warpAndEnhance(imagePath: String, quad: Quad): Bitmap {
        val src = Imgcodecs.imread(imagePath)
        try {
            val pts = quad.toList()
            val widthTop = EdgeDetector.distance(pts[0], pts[1])
            val widthBottom = EdgeDetector.distance(pts[3], pts[2])
            val heightLeft = EdgeDetector.distance(pts[0], pts[3])
            val heightRight = EdgeDetector.distance(pts[1], pts[2])
            val outW = max(widthTop, widthBottom).toInt().coerceAtLeast(1)
            val outH = max(heightLeft, heightRight).toInt().coerceAtLeast(1)

            val srcQuad = MatOfPoint2f(
                pts[0].toCv(), pts[1].toCv(), pts[2].toCv(), pts[3].toCv(),
            )
            val dstQuad = MatOfPoint2f(
                Point(0.0, 0.0),
                Point(outW.toDouble() - 1, 0.0),
                Point(outW.toDouble() - 1, outH.toDouble() - 1),
                Point(0.0, outH.toDouble() - 1),
            )

            val transform = Imgproc.getPerspectiveTransform(srcQuad, dstQuad)
            val warped = Mat(outH, outW, CvType.CV_8UC3)
            Imgproc.warpPerspective(src, warped, transform, Size(outW.toDouble(), outH.toDouble()))

            // Enhance: convert to grayscale-friendly readable scan.
            val gray = Mat()
            Imgproc.cvtColor(warped, gray, Imgproc.COLOR_BGR2GRAY)
            val enhanced = Mat()
            Imgproc.adaptiveThreshold(
                gray, enhanced, 255.0,
                Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY,
                15, 10.0,
            )
            // Convert back to RGB for the bitmap helper.
            val rgb = Mat()
            Imgproc.cvtColor(enhanced, rgb, Imgproc.COLOR_GRAY2RGB)

            val bmp = Bitmap.createBitmap(rgb.cols(), rgb.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(rgb, bmp)

            warped.release(); gray.release(); enhanced.release(); rgb.release()
            return bmp
        } finally {
            src.release()
        }
    }

    private fun Offset.toCv(): Point = Point(this.x.toDouble(), this.y.toDouble())
}
