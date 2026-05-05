package dev.pdfscanner.ui

import androidx.compose.ui.geometry.Offset

data class Quad(
    val topLeft: Offset,
    val topRight: Offset,
    val bottomRight: Offset,
    val bottomLeft: Offset,
) {
    fun toList(): List<Offset> = listOf(topLeft, topRight, bottomRight, bottomLeft)
    companion object {
        fun full(width: Int, height: Int): Quad = Quad(
            Offset(0f, 0f),
            Offset(width.toFloat(), 0f),
            Offset(width.toFloat(), height.toFloat()),
            Offset(0f, height.toFloat()),
        )
    }
}

data class CapturedPage(
    val rawPath: String,
    val width: Int,
    val height: Int,
    val quad: Quad,
)
