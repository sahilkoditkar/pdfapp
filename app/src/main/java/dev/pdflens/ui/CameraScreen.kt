package dev.pdflens.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import dev.pdflens.scan.EdgeDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.camera.core.Preview as CameraPreview

@Composable
fun CameraScreen(
    onCaptured: (CapturedPage) -> Unit,
    onDone: () -> Unit,
    pageCount: Int,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant camera permission")
            }
        }
        return
    }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val scope = rememberCoroutineScope()

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val page = withContext(Dispatchers.IO) { importImageAsCapturedPage(context, uri) }
                if (page != null) onCaptured(page)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = CameraPreview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture,
                    )
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
                icon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                text = { Text("Import") },
            )

            ExtendedFloatingActionButton(
                onClick = {
                    val outFile = File(
                        context.filesDir,
                        "scan_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg",
                    )
                    val opts = ImageCapture.OutputFileOptions.Builder(outFile).build()
                    imageCapture.takePicture(
                        opts,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onError(exc: ImageCaptureException) {}
                            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                val bounds = BitmapFactory.Options().apply {
                                    inJustDecodeBounds = true
                                }
                                BitmapFactory.decodeFile(outFile.absolutePath, bounds)
                                val w = bounds.outWidth
                                val h = bounds.outHeight
                                val quad = EdgeDetector.detect(outFile.absolutePath)
                                    ?: Quad.full(w, h)
                                onCaptured(
                                    CapturedPage(
                                        rawPath = outFile.absolutePath,
                                        width = w,
                                        height = h,
                                        quad = quad,
                                    )
                                )
                            }
                        },
                    )
                },
                icon = { Icon(Icons.Filled.Camera, contentDescription = null) },
                text = { Text("Capture") },
            )

            if (pageCount > 0) {
                ExtendedFloatingActionButton(
                    onClick = onDone,
                    icon = { Icon(Icons.Filled.Check, contentDescription = null) },
                    text = { Text("Done ($pageCount)") },
                )
            }
        }
    }
}

private fun importImageAsCapturedPage(context: Context, uri: Uri): CapturedPage? {
    val outFile = File(
        context.filesDir,
        "scan_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.jpg",
    )
    val copied = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        } != null
    }.getOrDefault(false)
    if (!copied) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(outFile.absolutePath, bounds)
    val w = bounds.outWidth
    val h = bounds.outHeight
    if (w <= 0 || h <= 0) {
        outFile.delete()
        return null
    }
    val quad = EdgeDetector.detect(outFile.absolutePath) ?: Quad.full(w, h)
    return CapturedPage(rawPath = outFile.absolutePath, width = w, height = h, quad = quad)
}
