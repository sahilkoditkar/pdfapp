package dev.pdfscanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.pdfscanner.data.SavedPdf
import dev.pdfscanner.data.listSavedPdfs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentListScreen(onNewScan: () -> Unit) {
    val context = LocalContext.current
    val pdfs by produceState(initialValue = emptyList<SavedPdf>(), context) {
        value = listSavedPdfs(context)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("PDF Scanner") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewScan,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New scan") },
            )
        },
    ) { padding ->
        if (pdfs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No documents yet")
                    Text("Tap \"New scan\" to start.")
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(pdfs, key = { it.path }) { pdf ->
                    ListItem(
                        headlineContent = { Text(pdf.name) },
                        supportingContent = { Text("${pdf.pageCount} page(s)") },
                    )
                }
            }
        }
    }
}
