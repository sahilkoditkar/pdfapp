package dev.pdflens.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.pdflens.data.PdfStorage
import dev.pdflens.data.SavedPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(pdf: SavedPdf, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pfd: ParcelFileDescriptor? = remember(pdf.uri) {
        runCatching { context.contentResolver.openFileDescriptor(pdf.uri, "r") }.getOrNull()
    }
    val renderer: PdfRenderer? = remember(pfd) {
        pfd?.let { runCatching { PdfRenderer(it) }.getOrNull() }
    }
    DisposableEffect(pdf.uri) {
        onDispose {
            runCatching { renderer?.close() }
            runCatching { pfd?.close() }
        }
    }

    val pageCount = renderer?.pageCount ?: 0
    val pagerState = rememberPagerState(pageCount = { pageCount })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pdf.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { sharePdf(context, pdf) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { openPdfExternally(context, pdf) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open with")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                PdfStorage.delete(context, pdf.uri)
                            }
                            if (ok) onBack()
                            else Toast.makeText(context, "Could not delete", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        bottomBar = {
            if (pageCount > 0) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Text(
                        "${pagerState.currentPage + 1} / $pageCount",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            when {
                renderer == null -> Text(
                    "Could not open PDF",
                    Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                pageCount == 0 -> Text(
                    "Empty document",
                    Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { pageIndex ->
                    PdfPage(renderer, pageIndex)
                }
            }
        }
    }
}

@Composable
private fun PdfPage(renderer: PdfRenderer, index: Int) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }

        // Render off the main thread; bitmap is recreated when the viewport
        // size or page index changes.
        val bitmap by produceState<Bitmap?>(initialValue = null, key1 = index, key2 = widthPx, key3 = heightPx) {
            value = withContext(Dispatchers.IO) {
                renderPage(renderer, index, widthPx, heightPx)
            }
        }

        DisposableEffect(bitmap) {
            onDispose { bitmap?.recycle() }
        }

        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        LaunchedEffect(index) {
            scale = 1f
            offset = Offset.Zero
        }

        val current = bitmap
        if (current == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "Page ${index + 1}",
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(index) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            )
        }
    }
}

// PdfRenderer is single-threaded: only one page may be open at a time.
private fun renderPage(renderer: PdfRenderer, index: Int, viewportW: Int, viewportH: Int): Bitmap? {
    if (viewportW <= 0 || viewportH <= 0) return null
    return synchronized(renderer) {
        if (index !in 0 until renderer.pageCount) return@synchronized null
        renderer.openPage(index).use { page ->
            val pageRatio = page.width.toFloat() / page.height
            val viewportRatio = viewportW.toFloat() / viewportH
            val (w, h) = if (pageRatio > viewportRatio) {
                viewportW to (viewportW / pageRatio).toInt().coerceAtLeast(1)
            } else {
                (viewportH * pageRatio).toInt().coerceAtLeast(1) to viewportH
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.WHITE)
            }
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        }
    }
}

private fun sharePdf(context: android.content.Context, pdf: SavedPdf) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, pdf.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Share PDF"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app to share with", Toast.LENGTH_LONG).show()
    }
}

private fun openPdfExternally(context: android.content.Context, pdf: SavedPdf) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(pdf.uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Open with"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No PDF viewer installed", Toast.LENGTH_LONG).show()
    }
}
