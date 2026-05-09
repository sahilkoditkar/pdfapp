package dev.pdflens.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.pdflens.scan.EdgeDetector
import dev.pdflens.scan.LiveEdgeAnalyzer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
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
        Box(
            Modifier.fillMaxSize().systemBarsPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant camera permission")
            }
        }
        return
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()
            )
            .build()
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var liveQuad by remember { mutableStateOf<LiveEdgeAnalyzer.NormalizedQuad?>(null) }

    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()
            )
            .build()
            .apply {
                setAnalyzer(analysisExecutor, LiveEdgeAnalyzer { liveQuad = it })
            }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                }
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = CameraPreview.Builder()
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                                .build()
                        )
                        .build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis,
                        imageCapture,
                    )
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )

        // Live document edge overlay. Coordinates are normalized in the upright
        // analysis frame; we letterbox to 4:3 inside the canvas to match
        // PreviewView's FIT_CENTER scale type.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val q = liveQuad ?: return@Canvas
            val cw = size.width
            val ch = size.height
            // 4:3 portrait → 3:4 displayed; pick whichever fits.
            val displayAspect = 3f / 4f
            val canvasAspect = cw / ch
            val (drawW, drawH) = if (canvasAspect > displayAspect) {
                ch * displayAspect to ch
            } else {
                cw to cw / displayAspect
            }
            val left = (cw - drawW) / 2f
            val top = (ch - drawH) / 2f
            fun map(o: Offset) = Offset(left + o.x * drawW, top + o.y * drawH)
            val tl = map(q.tl); val tr = map(q.tr); val br = map(q.br); val bl = map(q.bl)
            val path = Path().apply {
                moveTo(tl.x, tl.y); lineTo(tr.x, tr.y)
                lineTo(br.x, br.y); lineTo(bl.x, bl.y); close()
            }
            drawPath(path, color = Color(0xCC4ADE80), style = Stroke(width = 6f))
            drawPath(path, color = Color(0x334ADE80))
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
