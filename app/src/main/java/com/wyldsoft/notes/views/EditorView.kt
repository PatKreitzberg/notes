package com.wyldsoft.notes.views

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.wyldsoft.notes.NotesApp
import com.wyldsoft.notes.classes.drawing.DrawingManager
import com.wyldsoft.notes.components.EditorSurface
import com.wyldsoft.notes.components.Toolbar
import com.wyldsoft.notes.ui.theme.NotesTheme
import com.wyldsoft.notes.utils.EditorState
import com.wyldsoft.notes.utils.convertDpToPixel
import com.wyldsoft.notes.components.ScrollIndicator
import com.wyldsoft.notes.components.TopBoundaryIndicator
import com.wyldsoft.notes.templates.TemplateRenderer
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EditorView(noteId: String? = null) {
    val context = LocalContext.current
    val app = NotesApp.getApp(context)
    val scope = rememberCoroutineScope()

    // Get repositories from app
    val settingsRepository = app.settingsRepository
    val noteRepository = app.noteRepository

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

        // Load strokes from database if this is an existing note
        LaunchedEffect(pageId) {
            if (noteId != null) {
                try {
                    val strokes = noteRepository.getStrokesForNote(noteId)
                    if (strokes.isNotEmpty()) {
                        page.addStrokes(strokes)
                    }
                } catch (e: Exception) {
                    println("Error loading strokes: ${e.message}")
                }
            } else {
                // Create a new note in the database
                noteRepository.createNote(
                    id = pageId,
                    title = "Note ${System.currentTimeMillis()}",
                    width = width,
                    height = height
                )
            }
        }

        // Dynamically update the page width when the Box constraints change
        LaunchedEffect(width, height) {
            if (page.width != width || page.viewHeight != height) {
                page.updateDimensions(width, height)
                DrawingManager.refreshUi.emit(Unit)
            }
        }

        val editorState = remember { EditorState(pageId = pageId, pageView = page) }

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

                Toolbar(
                    state = editorState,
                    settingsRepository = settingsRepository,
                    viewportTransformer = page.viewportTransformer,
                    templateRenderer = templateRenderer
                )
            }
        }
    }
}