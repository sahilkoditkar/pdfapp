package dev.pdflens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.pdflens.ui.PdfLensApp
import dev.pdflens.ui.theme.PdfLensTheme
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        OpenCVLoader.initLocal()
        setContent {
            PdfLensTheme {
                PdfLensApp()
            }
        }
    }
}
