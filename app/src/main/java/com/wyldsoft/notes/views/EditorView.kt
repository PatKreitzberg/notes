package com.wyldsoft.notes.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wyldsoft.notes.NotesApp
import com.wyldsoft.notes.classes.drawing.DrawingManager
import com.wyldsoft.notes.components.EditorSurface
import com.wyldsoft.notes.components.Toolbar
import com.wyldsoft.notes.database.entity.NotebookEntity
import com.wyldsoft.notes.ui.theme.NotesTheme
import com.wyldsoft.notes.utils.EditorState
import com.wyldsoft.notes.utils.convertDpToPixel
import com.wyldsoft.notes.components.ScrollIndicator
import com.wyldsoft.notes.components.TopBoundaryIndicator
import com.wyldsoft.notes.templates.TemplateRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import com.wyldsoft.notes.selection.SelectionHandler

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EditorView(noteId: String? = null) {
    val context = LocalContext.current
    val app = NotesApp.getApp(context)
    val scope = rememberCoroutineScope()

    // Get repositories from app
    val settingsRepository = app.settingsRepository
    val noteRepository = app.noteRepository
    val pageNotebookRepository = app.pageNotebookRepository
    val notebookRepository = app.notebookRepository

    var noteTitle by remember { mutableStateOf("New Note") }

    // State for notebook management
    var showNotebookDialog by remember { mutableStateOf(false) }
    val notebooks by notebookRepository.getAllNotebooks().collectAsState(initial = emptyList())
    val notebooksContainingPage = noteId?.let {
        pageNotebookRepository.getNotebooksContainingPage(it)
    }?.collectAsState(initial = emptyList())

    // Initialize template renderer
    val templateRenderer = remember { TemplateRenderer(context) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val height = convertDpToPixel(this.maxHeight, context).toInt()
        val width = convertDpToPixel(this.maxWidth, context).toInt()

        // Use provided noteId or generate a new one
        val pageId = remember { noteId ?: UUID.randomUUID().toString() }

        val page = remember {
            PageView(
                context = context,
                coroutineScope = scope,
                id = pageId,
                width = width,
                viewWidth = width,
                viewHeight = height
            ).apply {
                initializeViewportTransformer(context, scope, settingsRepository)
            }
        }

        val editorState = remember { EditorState(pageId = pageId, pageView = page) }

        val selectionHandler = remember {
            SelectionHandler(
                context,
                editorState,
                page,
                scope
            )
        }

        // Load strokes from database if this is an existing note
        LaunchedEffect(pageId) {
            if (noteId != null) {
                try {
                    println("DEBUG: Loading strokes for note $noteId")

                    val note = noteRepository.getNoteById(noteId)
                    if (note != null) {
                        noteTitle = note.title
                        val strokes = noteRepository.getStrokesForNote(noteId)
                        if (strokes.isNotEmpty()) {
                            println("DEBUG: Loaded ${strokes.size} strokes")
                            page.addStrokes(strokes)

                            // Force redraw of the entire viewport
                            val viewport =
                                page.viewportTransformer.getCurrentViewportInPageCoordinates()
                            val rect = android.graphics.Rect(
                                0,
                                viewport.top.toInt(),
                                viewport.right.toInt(),
                                viewport.bottom.toInt()
                            )
                            page.drawArea(rect)

                            // Trigger UI update through DrawingManager
                            DrawingManager.forceUpdate.emit(rect)
                            DrawingManager.refreshUi.emit(Unit)
                        }
                    }
                } catch (e: Exception) {
                    println("Error loading strokes: ${e.message}")
                }
            } else {
                // Create a new note in the database
                val defaultTitle = "Note ${System.currentTimeMillis()}"
                noteTitle = defaultTitle
                noteRepository.createNote(
                    id = pageId,
                    title = defaultTitle,
                    width = width,
                    height = height
                )

                // Create default notebook if non exists and associate the note with it
                scope.launch {
                    // Check if there are any notebooks
                    val allNotebooks = notebookRepository.getAllNotebooksSync()
                    val notebookId = if (allNotebooks.isEmpty()) {
                        // Create a default notebook if none exists
                        val defaultNotebook = notebookRepository.createNotebook("My Notes")
                        defaultNotebook.id
                    } else {
                        // Use the first notebook as default
                        allNotebooks.first().id
                    }

                    // Associate the note with the notebook
                    pageNotebookRepository.addPageToNotebook(pageId, notebookId)
                    println("DEBUG: Associated new note $pageId with notebook $notebookId")
                }
            }
        }

        // Additional LaunchedEffect to ensure rendering after initialization
        LaunchedEffect(Unit) {
            // Short delay to ensure the surface is ready
            delay(300)
            page.drawCanvasToViewport()
            DrawingManager.refreshUi.emit(Unit)
        }

        // Dynamically update the page width when the Box constraints change
        LaunchedEffect(width, height) {
            if (page.width != width || page.viewHeight != height) {
                page.updateDimensions(width, height)
                DrawingManager.refreshUi.emit(Unit)
            }
        }

        // Set up save functionality for strokes (ADDITIONS)
        LaunchedEffect(Unit) {
            scope.launch {
                page.strokesAdded.collect { strokes ->
                    if (strokes.isNotEmpty()) {
                        // Save new strokes to database
                        println("DEBUG: Saving ${strokes.size} strokes to database")
                        noteRepository.saveStrokes(pageId, strokes)
                    }
                }
            }
        }

        // Set up save functionality for strokes (DELETIONS)
        LaunchedEffect(Unit) {
            scope.launch {
                page.strokesRemoved.collect { strokeIds ->
                    if (strokeIds.isNotEmpty()) {
                        // Delete strokes from database
                        println("DEBUG: Deleting ${strokeIds.size} strokes from database")
                        noteRepository.deleteStrokes(pageId, strokeIds)
                    }
                }
            }
        }

        // Notebook selection dialog
        if (showNotebookDialog) {
            NotebookSelectionDialog(
                notebooks = notebooks,
                selectedNotebooks = notebooksContainingPage?.value ?: emptyList(),
                onDismiss = { showNotebookDialog = false },
                onSelectionChanged = { selectedIds ->
                    scope.launch {
                        // First, get current notebooks
                        val currentNotebooks = notebooksContainingPage?.value ?: emptyList()
                        val currentIds = currentNotebooks.map { it.id }

                        // Remove page from notebooks it's no longer in
                        val toRemove = currentIds.filter { it !in selectedIds }
                        for (notebookId in toRemove) {
                            pageNotebookRepository.removePageFromNotebook(pageId, notebookId)
                        }

                        // Add page to new notebooks
                        val toAdd = selectedIds.filter { it !in currentIds }
                        for (notebookId in toAdd) {
                            pageNotebookRepository.addPageToNotebook(pageId, notebookId)
                        }
                    }
                }
            )
        }

        NotesTheme {
            Box(modifier = Modifier.fillMaxSize()) {
                EditorSurface(
                    state = editorState,
                    page = page,
                    settingsRepository = settingsRepository,
                    templateRenderer = templateRenderer
                )

                // Add scroll indicator
                ScrollIndicator(
                    viewportTransformer = page.viewportTransformer,
                    modifier = Modifier.fillMaxSize()
                )

                // Add top boundary indicator
                TopBoundaryIndicator(
                    viewportTransformer = page.viewportTransformer,
                    modifier = Modifier.fillMaxSize()
                )

                // Editor tools
                Box(modifier = Modifier.fillMaxSize()) {
                    Toolbar(
                        state = editorState,
                        settingsRepository = settingsRepository,
                        viewportTransformer = page.viewportTransformer,
                        templateRenderer = templateRenderer,
                        selectionHandler = selectionHandler,
                        noteTitle = noteTitle,
                        onUpdateNoteName = { newName ->
                            // Handle the rename operation
                            scope.launch {
                                try {
                                    // Get current note
                                    val note = noteRepository.getNoteById(pageId)
                                    if (note != null) {
                                        // Update the note with new title
                                        val updatedNote = note.copy(title = newName, updatedAt = java.util.Date())
                                        noteRepository.updateNote(updatedNote)
                                        // Update local state
                                        noteTitle = newName
                                        println("DEBUG: Note renamed to $newName")

                                        // Register note change for syncing
                                        app.syncManager.changeTracker.registerNoteChanged(pageId)
                                    }
                                } catch (e: Exception) {
                                    println("Error renaming note: ${e.message}")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun NotebookSelectionDialog(
    notebooks: List<NotebookEntity>,
    selectedNotebooks: List<NotebookEntity>,
    onDismiss: () -> Unit,
    onSelectionChanged: (List<String>) -> Unit
) {
    val selectedIds = remember {
        mutableStateListOf<String>().apply {
            addAll(selectedNotebooks.map { it.id })
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(8.dp)),
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Select Notebooks",
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (notebooks.isEmpty()) {
                    Text(
                        text = "No notebooks available. Create notebooks in the home screen first.",
                        style = MaterialTheme.typography.body1,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .padding(vertical = 8.dp)
                    ) {
                        items(notebooks) { notebook ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = notebook.id in selectedIds,
                                    onCheckedChange = { isChecked ->
                                        if (isChecked) {
                                            selectedIds.add(notebook.id)
                                        } else {
                                            selectedIds.remove(notebook.id)
                                        }
                                    }
                                )

                                Text(
                                    text = notebook.title,
                                    style = MaterialTheme.typography.body1,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            onSelectionChanged(selectedIds.toList())
                            onDismiss()
                        },
                        enabled = notebooks.isNotEmpty()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}