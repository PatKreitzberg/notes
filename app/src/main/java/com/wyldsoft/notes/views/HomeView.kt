package com.wyldsoft.notes.views

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.wyldsoft.notes.NotesApp
import com.wyldsoft.notes.components.AddItemDialog
import com.wyldsoft.notes.components.HomeSettingsDialog
import com.wyldsoft.notes.components.SyncStatusIndicator
import com.wyldsoft.notes.database.entity.FolderEntity
import com.wyldsoft.notes.database.entity.NoteEntity
import com.wyldsoft.notes.database.entity.NotebookEntity
import com.wyldsoft.notes.models.FolderModel
import com.wyldsoft.notes.sync.SyncState
import com.wyldsoft.notes.utils.noRippleClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@ExperimentalFoundationApi
@Composable
fun HomeView(
    navController: NavController,
    initialFolderId: String? = null,
    initialNotebookId: String? = null
) {
    val context = LocalContext.current
    val app = NotesApp.getApp(context)
    val scope = rememberCoroutineScope()

    // Get repositories
    val folderRepository = app.folderRepository
    val notebookRepository = app.notebookRepository
    val noteRepository = app.noteRepository
    val pageNotebookRepository = app.pageNotebookRepository

    // State
    var currentPath by remember { mutableStateOf("/") }
    var selectedFolderId by remember { mutableStateOf(initialFolderId) }
    var selectedNotebookId by remember { mutableStateOf(initialNotebookId) }

    // Dialog states
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var showAddNotebookDialog by remember { mutableStateOf(false) }
    var showAddPageDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<Any?>(null) }

    // Collect data
    val rootFolders by folderRepository.getRootFolders().collectAsState(initial = emptyList())
    val subFolders by selectedFolderId?.let {
        folderRepository.getSubFolders(it)
    }?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }

    val notebooksInFolder by selectedFolderId?.let {
        notebookRepository.getNotebooksInFolder(it)
    }?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }

    val notebooksWithoutFolder by notebookRepository.getNotebooksWithoutFolder()
        .collectAsState(initial = emptyList())

    val pagesInNotebook by selectedNotebookId?.let {
        pageNotebookRepository.getPagesInNotebook(it)
    }?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }

    // Expanded folder states
    val expandedFolderIds = remember { mutableStateListOf<String>() }

    // Show settings dialog if requested
    if (showSettingsDialog) {
        HomeSettingsDialog(
            onDismiss = { showSettingsDialog = false }
        )
    }

    // Add folder dialog
    if (showAddFolderDialog) {
        AddItemDialog(
            title = "Create New Folder",
            nameLabel = "Folder Name",
            onConfirm = { name ->
                scope.launch {
                    folderRepository.createFolder(name, selectedFolderId)
                }
                showAddFolderDialog = false
            },
            onDismiss = { showAddFolderDialog = false }
        )
    }

    // Add notebook dialog
    if (showAddNotebookDialog) {
        AddItemDialog(
            title = "Create New Notebook",
            nameLabel = "Notebook Title",
            onConfirm = { title ->
                scope.launch {
                    notebookRepository.createNotebook(title, selectedFolderId)
                }
                showAddNotebookDialog = false
            },
            onDismiss = { showAddNotebookDialog = false }
        )
    }

    // Add page dialog
    if (showAddPageDialog && selectedNotebookId != null) {
        AddItemDialog(
            title = "Create New Page",
            nameLabel = "Page Title",
            onConfirm = { title ->
                scope.launch {
                    // Create a new page first
                    val pageId = java.util.UUID.randomUUID().toString()

                    noteRepository.createNote(pageId, title, 1000, 1500)

                    // Add the page to the selected notebook
                    pageNotebookRepository.addPageToNotebook(pageId, selectedNotebookId!!)

                    // Log for debugging
                    println("DEBUG: Created new page $pageId and added it to notebook $selectedNotebookId")

                    // Navigate to the editor for the new page
                    navController.navigate("editor/$pageId")
                }
                showAddPageDialog = false
            },
            onDismiss = { showAddPageDialog = false }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirmDialog && itemToDelete != null) {
        // Capture the current itemToDelete in a local val
        val currentItemToDelete = itemToDelete
        val itemType = when (currentItemToDelete) {
            is FolderEntity -> "folder"
            is NotebookEntity -> "notebook"
            is NoteEntity -> "page"
            else -> "item"
        }

        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                itemToDelete = null
            },
            title = { Text("Confirm Delete") },
            text = { Text("Are you sure you want to delete this $itemType?") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            when (val item = currentItemToDelete) {  // Use captured value here
                                is FolderEntity -> folderRepository.deleteFolder(item.id)
                                is NotebookEntity -> notebookRepository.deleteNotebook(item.id)
                                is NoteEntity -> {
                                    if (selectedNotebookId != null) {
                                        // Check if page is in multiple notebooks
                                        val notebookCount = pageNotebookRepository.getNotebookCountForPage(item.id)
                                        if (notebookCount > 1) {
                                            // Only remove from current notebook
                                            pageNotebookRepository.removePageFromNotebook(item.id, selectedNotebookId!!)
                                        } else {
                                            // Delete the page completely
                                            noteRepository.deleteNote(item.id)
                                        }
                                    } else {
                                        // No notebook context, just delete the page
                                        noteRepository.deleteNote(item.id)
                                    }
                                }
                            }
                        }
                        showDeleteConfirmDialog = false
                        itemToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        itemToDelete = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Optional: Separate dialog for pages in multiple notebooks
    var showMultipleNotebooksDialog by remember { mutableStateOf(false) }
    var noteToHandle by remember { mutableStateOf<NoteEntity?>(null) }
    var notebookCount by remember { mutableStateOf(0) }

    // Check for pages in multiple notebooks
    LaunchedEffect(itemToDelete) {
        if (itemToDelete is NoteEntity && selectedNotebookId != null) {
            val count = pageNotebookRepository.getNotebookCountForPage((itemToDelete as NoteEntity).id)
            if (count > 1) {
                notebookCount = count
                noteToHandle = itemToDelete as NoteEntity
                showMultipleNotebooksDialog = true
                showDeleteConfirmDialog = false
                itemToDelete = null
            }
        }
    }

    // Show special dialog for pages in multiple notebooks
    if (showMultipleNotebooksDialog && noteToHandle != null) {
        val note = noteToHandle
        AlertDialog(
            onDismissRequest = {
                showMultipleNotebooksDialog = false
                noteToHandle = null
            },
            title = { Text("Page in Multiple Notebooks") },
            text = { Text("This page is in $notebookCount notebooks. What would you like to do?") },
            buttons = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                if (note != null && selectedNotebookId != null) {
                                    // Remove only from current notebook
                                    pageNotebookRepository.removePageFromNotebook(
                                        note.id,
                                        selectedNotebookId!!
                                    )
                                }
                            }
                            showMultipleNotebooksDialog = false
                            noteToHandle = null
                        }
                    ) {
                        Text("Remove from this notebook only")
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                if (note != null) {
                                    // Delete page completely
                                    noteRepository.deleteNote(note.id)
                                }
                            }
                            showMultipleNotebooksDialog = false
                            noteToHandle = null
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error)
                    ) {
                        Text("Delete from all notebooks")
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Notes") },
                actions = {
                    val app = NotesApp.getApp(LocalContext.current)
                    val syncState by app.syncManager.syncState.collectAsState()
                    val lastSyncTime by app.syncManager.lastSyncTime.collectAsState()

                    SyncStatusIndicator(
                        syncState = syncState,
                        lastSyncTime = lastSyncTime,
                        onClick = {
                            // Show sync dialog on click
                            if (syncState == SyncState.ERROR) {
                                // Reset error state
                                app.syncManager.resetErrorState()
                            } else {
                                var showSyncDialog = true // todo what is this for?
                            }
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(24.dp)
                            .noRippleClickable { showSettingsDialog = true }
                    )
                }
            )
        },
        floatingActionButton = {
            Column {
                // Show FAB for adding page only when a notebook is selected
                if (selectedNotebookId != null) {
                    FloatingActionButton(
                        onClick = { showAddPageDialog = true },
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.NoteAdd, contentDescription = "Add page")
                    }
                }

                // Always show FAB for adding notebook
                FloatingActionButton(
                    onClick = { showAddNotebookDialog = true },
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Book, contentDescription = "Add notebook")
                }

                // Always show FAB for adding folder
                FloatingActionButton(
                    onClick = { showAddFolderDialog = true },
                    backgroundColor = MaterialTheme.colors.primary
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "Add folder")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Breadcrumb navigation
                BreadcrumbNavigation(
                    path = currentPath,
                    onNavigate = { path ->
                        currentPath = path
                        // TODO: Navigate to the folder corresponding to this path
                    }
                )

                // Main content
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    // Show root folders
                    if (selectedFolderId == null) {
                        item {
                            Text(
                                text = "Folders",
                                style = MaterialTheme.typography.h6,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        items(rootFolders) { folder ->
                            FolderItem(
                                folder = folder,
                                isExpanded = folder.id in expandedFolderIds,
                                onToggleExpand = {
                                    if (folder.id in expandedFolderIds) {
                                        expandedFolderIds.remove(folder.id)
                                    } else {
                                        expandedFolderIds.add(folder.id)
                                    }
                                },
                                onClick = {
                                    selectedFolderId = folder.id
                                    currentPath = folder.path
                                },
                                onDelete = {
                                    itemToDelete = folder
                                    showDeleteConfirmDialog = true
                                }
                            )
                        }

                        // Show notebooks without folder
                        item {
                            Text(
                                text = "Notebooks",
                                style = MaterialTheme.typography.h6,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        items(notebooksWithoutFolder) { notebook ->
                            NotebookItem(
                                notebook = notebook,
                                onClick = {
                                    selectedNotebookId = notebook.id
                                    selectedFolderId = null
                                },
                                onDelete = {
                                    itemToDelete = notebook
                                    showDeleteConfirmDialog = true
                                }
                            )
                        }
                    }
                    // Show sub-folders and notebooks within the selected folder
                    else {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Go back to parent folder
                                        scope.launch {
                                            val currentFolder = folderRepository.getFolderById(selectedFolderId!!)
                                            if (currentFolder != null && currentFolder.parentId != null) {
                                                selectedFolderId = currentFolder.parentId
                                                val parentFolder = folderRepository.getFolderById(currentFolder.parentId)
                                                if (parentFolder != null) {
                                                    currentPath = parentFolder.path
                                                }
                                            } else {
                                                selectedFolderId = null
                                                currentPath = "/"
                                            }
                                        }
                                    }
                                    .padding(8.dp)
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Back")
                            }
                        }

                        item {
                            Text(
                                text = "Sub-Folders",
                                style = MaterialTheme.typography.h6,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        items(subFolders) { folder ->
                            FolderItem(
                                folder = folder,
                                isExpanded = folder.id in expandedFolderIds,
                                onToggleExpand = {
                                    if (folder.id in expandedFolderIds) {
                                        expandedFolderIds.remove(folder.id)
                                    } else {
                                        expandedFolderIds.add(folder.id)
                                    }
                                },
                                onClick = {
                                    selectedFolderId = folder.id
                                    currentPath = folder.path
                                },
                                onDelete = {
                                    itemToDelete = folder
                                    showDeleteConfirmDialog = true
                                }
                            )
                        }

                        item {
                            Text(
                                text = "Notebooks",
                                style = MaterialTheme.typography.h6,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        items(notebooksInFolder) { notebook ->
                            NotebookItem(
                                notebook = notebook,
                                onClick = {
                                    selectedNotebookId = notebook.id
                                },
                                onDelete = {
                                    itemToDelete = notebook
                                    showDeleteConfirmDialog = true
                                }
                            )
                        }
                    }

                    // Show pages within the selected notebook
                    if (selectedNotebookId != null) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedNotebookId = null
                                    }
                                    .padding(8.dp)
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back to notebooks")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Back to notebooks")
                            }
                        }

                        item {
                            Text(
                                text = "Pages",
                                style = MaterialTheme.typography.h6,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        items(pagesInNotebook) { page ->
                            PageItem(
                                page = page,
                                onClick = {
                                    navController.navigate("editor/${page.id}")
                                },
                                onDelete = {
                                    itemToDelete = page
                                    showDeleteConfirmDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BreadcrumbNavigation(
    path: String,
    onNavigate: (String) -> Unit
) {
    val parts = path.split("/").filter { it.isNotEmpty() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Home,
            contentDescription = "Home",
            modifier = Modifier
                .clickable { onNavigate("/") }
                .padding(4.dp)
        )

        var currentPath = ""
        parts.forEachIndexed { index, part ->
            Text(" / ")
            currentPath += "/$part"
            val pathToNavigate = currentPath
            Text(
                text = part,
                modifier = Modifier
                    .clickable { onNavigate(pathToNavigate) }
                    .padding(4.dp),
                color = if (index == parts.size - 1) MaterialTheme.colors.primary else Color.Gray
            )
        }
    }
}

@Composable
fun FolderItem(
    folder: FolderEntity,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .clickable(onClick = onClick),
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "Folder",
                tint = Color(0xFFFFB74D)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.subtitle1
                )
                Text(
                    text = "Created: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(folder.createdAt)}",
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete folder",
                    tint = Color.Gray
                )
            }
        }
    }
}

@Composable
fun NotebookItem(
    notebook: NotebookEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .clickable(onClick = onClick),
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Book,
                contentDescription = "Notebook",
                tint = Color(0xFF7986CB)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = notebook.title,
                    style = MaterialTheme.typography.subtitle1
                )
                Text(
                    text = "Last edited: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(notebook.updatedAt)}",
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete notebook",
                    tint = Color.Gray
                )
            }
        }
    }
}

@Composable
fun PageItem(
    page: NoteEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .clickable(onClick = onClick),
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = "Page",
                tint = Color(0xFF4DB6AC)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.subtitle1
                )
                Text(
                    text = "Last edited: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(page.updatedAt)}",
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete page",
                    tint = Color.Gray
                )
            }
        }
    }
}