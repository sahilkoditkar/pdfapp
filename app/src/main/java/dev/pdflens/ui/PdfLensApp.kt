package dev.pdflens.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import dev.pdflens.data.PdfStorage
import dev.pdflens.data.SavedPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PdfLensApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshKey by remember { mutableIntStateOf(0) }
    var viewing by remember { mutableStateOf<SavedPdf?>(null) }

    val scanner = remember {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(20)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val pdfUri = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pdf?.uri
            ?: return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) { saveScannedPdf(context, pdfUri) }
            if (saved != null) refreshKey++
            else Toast.makeText(context, "Could not save scan", Toast.LENGTH_SHORT).show()
        }
    }

    fun startScan() {
        val activity = context.findActivity()
        if (activity == null) {
            Toast.makeText(context, "Cannot start scanner", Toast.LENGTH_SHORT).show()
            return
        }
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { sender ->
                launcher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    context,
                    "Scanner unavailable: ${e.localizedMessage ?: "Play Services required"}",
                    Toast.LENGTH_LONG,
                ).show()
            }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startScan()
        else Toast.makeText(
            context,
            "Storage permission is required to save scans",
            Toast.LENGTH_LONG,
        ).show()
    }

    val onNewScan: () -> Unit = {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            startScan()
        }
    }

    val current = viewing
    if (current != null) {
        val close: () -> Unit = {
            viewing = null
            refreshKey++
        }
        BackHandler(onBack = close)
        PdfViewerScreen(pdf = current, onBack = close)
    } else {
        DocumentListScreen(
            refreshKey = refreshKey,
            onNewScan = onNewScan,
            onOpen = { viewing = it },
        )
    }
}

private fun saveScannedPdf(context: Context, sourceUri: Uri): Uri? {
    val name = "scan_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.pdf"
    return PdfStorage.create(context, name) { out ->
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            input.copyTo(out)
        } ?: error("Could not open scanned PDF")
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
