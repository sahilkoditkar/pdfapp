package dev.pdflens.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.pdflens.data.PdfStorage
import dev.pdflens.data.PdfThumbnailCache
import dev.pdflens.data.SavedPdf
import dev.pdflens.data.SortOrder
import dev.pdflens.data.listSavedPdfs
import dev.pdflens.data.sortedBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DocumentListScreen(
    refreshKey: Int = 0,
    onNewScan: () -> Unit,
    onOpen: (SavedPdf) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var pdfs by remember { mutableStateOf<List<SavedPdf>>(emptyList()) }
    var sortOrder by remember { mutableStateOf(SortOrder.DATE_DESC) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(setOf<Uri>()) }
    var renameTarget by remember { mutableStateOf<SavedPdf?>(null) }
    var deleteTarget by remember { mutableStateOf<SavedPdf?>(null) }
    var localRefresh by remember { mutableStateOf(0) }

    fun refresh() {
        scope.launch {
            pdfs = withContext(Dispatchers.IO) { listSavedPdfs(context) }
        }
    }

    LaunchedEffect(refreshKey, localRefresh) { refresh() }
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val filtered by remember(pdfs, query, sortOrder) {
        derivedStateOf {
            val q = query.trim()
            val matches = if (q.isEmpty()) pdfs
            else pdfs.filter { it.name.contains(q, ignoreCase = true) }
            matches.sortedBy(sortOrder)
        }
    }

    val inSelectionMode = selection.isNotEmpty()
    BackHandler(enabled = inSelectionMode || searching) {
        when {
            inSelectionMode -> selection = emptySet()
            searching -> {
                searching = false
                query = ""
            }
        }
    }

    Scaffold(
        topBar = {
            when {
                inSelectionMode -> SelectionAppBar(
                    count = selection.size,
                    onCancel = { selection = emptySet() },
                    onShare = {
                        shareMany(context, pdfs.filter { it.uri in selection }.map { it.uri })
                    },
                    onDelete = {
                        scope.launch {
                            val toDelete = pdfs.filter { it.uri in selection }
                            withContext(Dispatchers.IO) {
                                toDelete.forEach {
                                    PdfStorage.delete(context, it.uri)
                                    PdfThumbnailCache.invalidate(it.uri)
                                }
                            }
                            selection = emptySet()
                            localRefresh++
                        }
                    },
                )
                searching -> SearchAppBar(
                    query = query,
                    onQueryChange = { query = it },
                    onClose = {
                        searching = false
                        query = ""
                    },
                )
                else -> DefaultAppBar(
                    sortOrder = sortOrder,
                    sortMenuOpen = sortMenuOpen,
                    onSortMenu = { sortMenuOpen = it },
                    onSortChange = { sortOrder = it; sortMenuOpen = false },
                    onSearch = { searching = true },
                )
            }
        },
        floatingActionButton = {
            if (!inSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = onNewScan,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New scan") },
                )
            }
        },
    ) { padding ->
        if (filtered.isEmpty()) {
            EmptyState(
                modifier = Modifier.fillMaxSize().padding(padding),
                hasQuery = query.isNotBlank(),
                libraryEmpty = pdfs.isEmpty(),
            )
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(filtered, key = { it.uri.toString() }) { pdf ->
                    DocumentRow(
                        pdf = pdf,
                        selected = pdf.uri in selection,
                        selectionMode = inSelectionMode,
                        onClick = {
                            if (inSelectionMode) selection = selection.toggle(pdf.uri)
                            else onOpen(pdf)
                        },
                        onLongClick = { selection = selection.toggle(pdf.uri) },
                        onRename = { renameTarget = pdf },
                        onDelete = { deleteTarget = pdf },
                    )
                }
            }
        }
    }

    renameTarget?.let { target ->
        RenameDialog(
            initialName = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                scope.launch {
                    val newUri = withContext(Dispatchers.IO) {
                        PdfStorage.rename(context, target.uri, newName)
                    }
                    if (newUri != null) {
                        PdfThumbnailCache.invalidate(target.uri)
                        renameTarget = null
                        localRefresh++
                    } else {
                        Toast.makeText(context, "Could not rename", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${target.name}\"?") },
            text = { Text("The file will be removed from your device.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            PdfStorage.delete(context, target.uri)
                        }
                        if (ok) {
                            PdfThumbnailCache.invalidate(target.uri)
                            localRefresh++
                        } else {
                            Toast.makeText(context, "Could not delete", Toast.LENGTH_SHORT).show()
                        }
                        deleteTarget = null
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultAppBar(
    sortOrder: SortOrder,
    sortMenuOpen: Boolean,
    onSortMenu: (Boolean) -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onSearch: () -> Unit,
) {
    TopAppBar(
        title = { Text("PDF Lens") },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
            Box {
                IconButton(onClick = { onSortMenu(true) }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { onSortMenu(false) }) {
                    sortOptions.forEach { (order, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { onSortChange(order) },
                            trailingIcon = {
                                if (order == sortOrder) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                                }
                            },
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

private val sortOptions = listOf(
    SortOrder.DATE_DESC to "Newest first",
    SortOrder.DATE_ASC to "Oldest first",
    SortOrder.NAME_ASC to "Name A-Z",
    SortOrder.NAME_DESC to "Name Z-A",
    SortOrder.SIZE_DESC to "Largest first",
    SortOrder.SIZE_ASC to "Smallest first",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchAppBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    TopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search documents") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions.Default,
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                } else null,
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionAppBar(
    count: Int,
    onCancel: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
            }
        },
        actions = {
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = "Share selected")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DocumentRow(
    pdf: SavedPdf,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val container =
        if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface

    ListItem(
        leadingContent = {
            Box(
                Modifier.size(width = 56.dp, height = 72.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                } else if (selectionMode) {
                    Icon(
                        Icons.Filled.RadioButtonUnchecked,
                        contentDescription = "Not selected",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(40.dp),
                    )
                } else {
                    Thumbnail(pdf)
                }
            }
        },
        headlineContent = { Text(pdf.name, maxLines = 1) },
        supportingContent = {
            Text(supportingLine(pdf))
        },
        trailingContent = if (!selectionMode) {
            {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, null) },
                            onClick = { menuOpen = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
        } else null,
        colors = ListItemDefaults.colors(containerColor = container),
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        ),
    )
}

@Composable
private fun Thumbnail(pdf: SavedPdf) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthPx = with(density) { 56.dp.roundToPx() }
    val bitmap by produceState<android.graphics.Bitmap?>(null, pdf.uri, widthPx) {
        value = PdfThumbnailCache.get(context, pdf.uri, widthPx)
    }
    Box(
        Modifier
            .size(width = 56.dp, height = 72.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Filled.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier, hasQuery: Boolean, libraryEmpty: Boolean) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (libraryEmpty) {
                Text("No documents yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tap \"New scan\" to start.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (hasQuery) {
                Text("No matches", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Try a different search term.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename document") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank() && text != initialName,
                onClick = { onConfirm(text) },
            ) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun Set<Uri>.toggle(uri: Uri): Set<Uri> =
    if (uri in this) this - uri else this + uri

private fun supportingLine(pdf: SavedPdf): String {
    val pageText = if (pdf.pageCount == 1) "1 page" else "${pdf.pageCount} pages"
    return "$pageText  •  ${formatSize(pdf.sizeBytes)}"
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    val df = DecimalFormat("#.#")
    return "${df.format(value)} ${units[unit]}"
}

private fun shareMany(context: android.content.Context, uris: List<Uri>) {
    if (uris.isEmpty()) return
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uris.first())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/pdf"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Share PDFs"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app to share with", Toast.LENGTH_LONG).show()
    }
}
