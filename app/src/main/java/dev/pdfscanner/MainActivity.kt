package dev.pdfscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.pdfscanner.ui.PdfScannerApp
import dev.pdfscanner.ui.theme.PdfScannerTheme
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        OpenCVLoader.initLocal()
        setContent {
            PdfScannerTheme {
                PdfScannerApp()
            }
        }
    }
}
