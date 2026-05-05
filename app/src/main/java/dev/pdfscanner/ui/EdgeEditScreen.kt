package dev.pdfscanner.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Composable
fun EdgeEditScreen(
    page: CapturedPage?,
    onAccept: (CapturedPage) -> Unit,
    onCancel: () -> Unit,
) {
    if (page == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No page") }
        return
    }

    val bitmap = remember(page.rawPath) {
        BitmapFactory.decodeFile(page.rawPath)?.asImageBitmap()
    }
    var quad by remember(page) { mutableStateOf(page.quad) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (bitmap != null) {
            EditableQuadCanvas(
                bitmap = bitmap,
                imageWidth = page.width,
                imageHeight = page.height,
                quad = quad,
                onQuadChange = { quad = it },
                onSizeChanged = { canvasSize = it },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            OutlinedButton(onClick = onCancel) { Text("Discard") }
            Spacer(Modifier.size(16.dp))
            Button(onClick = { onAccept(page.copy(quad = quad)) }) { Text("Accept") }
        }
    }
}

@Composable
private fun EditableQuadCanvas(
    bitmap: ImageBitmap,
    imageWidth: Int,
    imageHeight: Int,
    quad: Quad,
    onQuadChange: (Quad) -> Unit,
    onSizeChanged: (IntSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val handleRadiusPx = 36f

    Canvas(
        modifier = modifier
            .pointerInput(quad, size) {
                detectDragGestures(
                    onDragStart = { /* selection happens per-event in onDrag */ },
                    onDrag = { change, drag ->
                        if (size.width == 0 || size.height == 0) return@detectDragGestures
                        val scaleX = size.width.toFloat() / imageWidth
                        val scaleY = size.height.toFloat() / imageHeight
                        // Convert pointer (canvas coords) to image coords.
                        val pointer = Offset(change.position.x / scaleX, change.position.y / scaleY)
                        val handles = quad.toList()
                        val nearest = handles.indices.minBy { i ->
                            (handles[i] - pointer).getDistanceSquared()
                        }
                        // Reject if too far from any handle (in canvas px).
                        val nearestCanvas = Offset(handles[nearest].x * scaleX, handles[nearest].y * scaleY)
                        if ((nearestCanvas - change.position).getDistance() > handleRadiusPx * 4) return@detectDragGestures
                        val moved = handles.toMutableList()
                        moved[nearest] = Offset(
                            (handles[nearest].x + drag.x / scaleX).coerceIn(0f, imageWidth.toFloat()),
                            (handles[nearest].y + drag.y / scaleY).coerceIn(0f, imageHeight.toFloat()),
                        )
                        onQuadChange(
                            Quad(moved[0], moved[1], moved[2], moved[3])
                        )
                    },
                )
            },
    ) {
        size = IntSize(this.size.width.toInt(), this.size.height.toInt())
        onSizeChanged(size)

        // Draw the captured photo, fit-to-canvas (assumes same aspect for scaffold simplicity).
        drawImage(
            image = bitmap,
            dstSize = androidx.compose.ui.unit.IntSize(this.size.width.toInt(), this.size.height.toInt()),
        )

        val scaleX = this.size.width / imageWidth
        val scaleY = this.size.height / imageHeight
        val pts = quad.toList().map { Offset(it.x * scaleX, it.y * scaleY) }

        // Edges
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            drawLine(Color.Yellow, a, b, strokeWidth = 4f)
        }
        // Handles
        for (p in pts) {
            drawCircle(Color.Yellow, radius = handleRadiusPx, center = p, style = Stroke(width = 4f))
            drawCircle(Color.White.copy(alpha = 0.4f), radius = handleRadiusPx, center = p)
        }
    }
}
