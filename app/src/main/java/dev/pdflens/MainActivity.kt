package dev.pdflens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.pdflens.ui.PdfLensApp
import dev.pdflens.ui.theme.PdfLensTheme

class MainActivity : ComponentActivity() {

    // Holds a PDF Uri that arrived via ACTION_SEND or ACTION_VIEW. Compose
    // observes it and the import flow consumes it via [onIncomingConsumed].
    private var pendingImport by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingImport = extractIncomingPdf(intent)
        setContent {
            PdfLensTheme {
                PdfLensApp(
                    incomingPdf = pendingImport,
                    onIncomingConsumed = { pendingImport = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractIncomingPdf(intent)?.let { pendingImport = it }
    }

    private fun extractIncomingPdf(intent: Intent?): Uri? {
        intent ?: return null
        return when (intent.action) {
            Intent.ACTION_SEND -> intent.streamUri()
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamUri(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
}
