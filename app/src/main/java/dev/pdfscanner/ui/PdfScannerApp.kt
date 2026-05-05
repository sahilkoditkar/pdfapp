package dev.pdfscanner.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.pdfscanner.pdf.PdfBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PdfScannerApp() {
    val nav = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // In-memory page list for the current scan session. Persisted to PDF on save.
    val pages = remember { mutableStateListOf<CapturedPage>() }

    NavHost(navController = nav, startDestination = "library") {
        composable("library") {
            DocumentListScreen(
                onNewScan = {
                    pages.clear()
                    nav.navigate("camera")
                },
            )
        }
        composable("camera") {
            CameraScreen(
                onCaptured = { page ->
                    pages.add(page)
                    nav.navigate("edit/${pages.lastIndex}")
                },
                onDone = {
                    val snapshot = pages.toList()
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            PdfBuilder.build(context, snapshot)
                        }
                        pages.clear()
                        nav.popBackStack("library", inclusive = false)
                    }
                },
                pageCount = pages.size,
            )
        }
        composable("edit/{index}") { backStack ->
            val index = backStack.arguments?.getString("index")?.toIntOrNull() ?: 0
            EdgeEditScreen(
                page = pages.getOrNull(index),
                onAccept = { updated ->
                    if (index in pages.indices) pages[index] = updated
                    nav.popBackStack()
                },
                onCancel = {
                    if (index in pages.indices) pages.removeAt(index)
                    nav.popBackStack()
                },
            )
        }
    }
}
