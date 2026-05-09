package dev.pdflens.ui

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import dev.pdflens.data.PdfThumbnailCache
import dev.pdflens.data.SavedPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

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
                    IconButton(onClick = { printPdf(context, pdf) }) {
                        Icon(Icons.Filled.Print, contentDescription = "Print")
                    }
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Open with…") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                                onClick = {
                                    menuOpen = false
                                    openPdfExternally(context, pdf)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Save as images") },
                                leadingIcon = { Icon(Icons.Filled.Image, null) },
                                onClick = {
                                    menuOpen = false
                                    scope.launch {
                                        val count = withContext(Dispatchers.IO) {
                                            exportAsImages(context, pdf)
                                        }
                                        Toast.makeText(
                                            context,
                                            if (count > 0) "Saved $count image${if (count == 1) "" else "s"} to Pictures/PDF Lens"
                                            else "Could not export images",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                onClick = {
                                    menuOpen = false
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            PdfStorage.delete(context, pdf.uri)
                                        }
                                        if (ok) {
                                            PdfThumbnailCache.invalidate(pdf.uri)
                                            onBack()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Could not delete",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                },
                            )
                        }
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
        // size or page index changes. Failures (e.g. the renderer being
        // closed mid-render after the user navigates away) collapse to null
        // rather than propagating up and crashing the app.
        val bitmap by produceState<Bitmap?>(initialValue = null, key1 = index, key2 = widthPx, key3 = heightPx) {
            value = runCatching {
                withContext(Dispatchers.IO) {
                    renderPage(renderer, index, widthPx, heightPx)
                }
            }.getOrNull()
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
// Returns null if the renderer was closed (e.g. the viewer was disposed
// while a background render was still in flight) or anything else fails.
private fun renderPage(renderer: PdfRenderer, index: Int, viewportW: Int, viewportH: Int): Bitmap? {
    if (viewportW <= 0 || viewportH <= 0) return null
    return runCatching {
        synchronized(renderer) {
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
    }.getOrNull()
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

private fun printPdf(context: android.content.Context, pdf: SavedPdf) {
    val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as? PrintManager
    if (printManager == null) {
        Toast.makeText(context, "Printing is not available", Toast.LENGTH_SHORT).show()
        return
    }
    val attrs = PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.UNKNOWN_PORTRAIT)
        .build()
    printManager.print(pdf.name, PdfStreamPrintAdapter(context, pdf), attrs)
}

/**
 * Bridges a content-resolvable PDF to the Android print framework by streaming
 * its bytes into the print job's destination file descriptor. Avoids loading
 * the whole PDF in memory.
 */
private class PdfStreamPrintAdapter(
    private val context: android.content.Context,
    private val pdf: SavedPdf,
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder("${pdf.name}.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(pdf.pageCount.takeIf { it > 0 } ?: PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        try {
            context.contentResolver.openInputStream(pdf.uri)?.use { input ->
                FileOutputStream(destination.fileDescriptor).use { output ->
                    input.copyTo(output)
                }
            } ?: run {
                callback.onWriteFailed("Could not read PDF")
                return
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Throwable) {
            callback.onWriteFailed(e.message)
        }
    }
}

/**
 * Renders every page of [pdf] as a JPEG into the public `Pictures/PDF Lens/`
 * folder via MediaStore (or the legacy public-storage path on API ≤ 28).
 * Returns the number of pages successfully exported.
 */
private fun exportAsImages(context: android.content.Context, pdf: SavedPdf): Int {
    var saved = 0
    runCatching {
        context.contentResolver.openFileDescriptor(pdf.uri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    val name = "${pdf.name}_page_${"%03d".format(i + 1)}.jpg"
                    val ok = renderer.openPage(i).use { page ->
                        val targetWidth = page.width * 2  // ~144 DPI for screen-sized pages
                        val ratio = page.height.toFloat() / page.width
                        val w = targetWidth.coerceAtLeast(1)
                        val h = (w * ratio).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                            eraseColor(Color.WHITE)
                        }
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        try {
                            writeJpegToPictures(context, name, bmp)
                        } finally {
                            bmp.recycle()
                        }
                    }
                    if (ok) saved++
                }
            }
        }
    }
    return saved
}

private const val PICTURES_SUBDIR = "PDF Lens"

private fun writeJpegToPictures(context: android.content.Context, displayName: String, bitmap: Bitmap): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$PICTURES_SUBDIR",
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return false
        val ok = runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            } ?: error("openOutputStream returned null")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        }.getOrElse {
            resolver.delete(uri, null, null)
            false
        }
        ok
    } else {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            PICTURES_SUBDIR,
        )
        if (!dir.exists() && !dir.mkdirs()) return false
        val file = File(dir, displayName)
        runCatching {
            file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("image/jpeg"),
                null,
            )
            true
        }.getOrDefault(false)
    }
}
