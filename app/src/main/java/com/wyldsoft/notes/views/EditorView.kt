package com.wyldsoft.notes.views

import android.graphics.Rect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.wyldsoft.notes.NotesApp
import com.wyldsoft.notes.classes.DrawCanvas
import com.wyldsoft.notes.classes.PageView
import com.wyldsoft.notes.components.EditorSurface
import com.wyldsoft.notes.components.Toolbar
import com.wyldsoft.notes.ui.theme.NotesTheme
import com.wyldsoft.notes.utils.EditorState
import com.wyldsoft.notes.utils.Stroke
import com.wyldsoft.notes.utils.convertDpToPixel
import kotlinx.coroutines.launch

/**
 * Editor view for drawing on a page.
 *
 * @param navController Navigation controller for navigating between screens
 * @param pageId ID of the page to edit
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EditorView(
    navController: NavController,
    pageId: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = NotesApp.getInstance()

    // State for page loading
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // State for PageView and EditorState
    var pageView by remember { mutableStateOf<PageView?>(null) }
    var editorState by remember { mutableStateOf<EditorState?>(null) }

    // Load page data
    LaunchedEffect(pageId) {
        try {
            val page = app.pageRepository.getPageById(pageId)
            if (page == null) {
                error = "Page not found"
                return@LaunchedEffect
            }

            // Create PageView with the correct dimensions
            val metrics = context.resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels

            // Create a custom PageView subclass that overrides the methods
            val customPageView = object : PageView(
                context = context,
                coroutineScope = scope,
                id = pageId,
                width = width,
                viewWidth = width,
                viewHeight = height
            ) {
                // Override addStrokes method
                override fun addStrokes(strokesToAdd: List<Stroke>) {
                    // Call the parent implementation first
                    super.addStrokes(strokesToAdd)

                    // Then save to database
                    scope.launch {
                        try {
                            println("Saving ${strokesToAdd.size} strokes to database")
                            app.strokeRepository.addStrokes(strokesToAdd)
                            // Update the page's last modified timestamp
                            app.pageRepository.updateLastModifiedAt(pageId)
                        } catch (e: Exception) {
                            println("Error saving strokes: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                }

                // Override removeStrokes method
                override fun removeStrokes(strokeIds: List<String>) {
                    // Call the parent implementation first
                    super.removeStrokes(strokeIds)

                    // Then remove from database
                    scope.launch {
                        try {
                            println("Deleting ${strokeIds.size} strokes from database")
                            app.strokeRepository.deleteStrokes(strokeIds)
                            // Update the page's last modified timestamp
                            app.pageRepository.updateLastModifiedAt(pageId)
                        } catch (e: Exception) {
                            println("Error deleting strokes: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                }
            }

            pageView = customPageView
            editorState = EditorState(pageId = pageId, pageView = pageView!!)

            // Load strokes from database
            // Load strokes from database
            try {
                val strokes = app.strokeRepository.getDomainStrokesForPage(pageId)
                println("Loaded ${strokes.size} strokes from database")
                if (strokes.isNotEmpty()) {
                    // We need to avoid calling the overridden addStrokes method
                    // that would trigger another database save.
                    // Instead, access the original PageView implementation
                    (pageView as? PageView)?.let { view ->
                        // Add to the strokes list directly
                        view.strokes = view.strokes + strokes
                        // Update indexing and trigger redraw
                        view.indexStrokes()
                        view.computeHeight()
                        view.drawArea(Rect(0, 0, view.viewWidth, view.viewHeight))
                    }
                }
            } catch (e: Exception) {
                println("Error loading strokes: ${e.message}")
                e.printStackTrace()
            }

            isLoading = false
        } catch (e: Exception) {
            error = "Error loading page: ${e.message}"
            e.printStackTrace()
        }
    }

    // Handle error state
    if (error != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = error!!,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        return
    }

    // Handle loading state
    if (isLoading || pageView == null || editorState == null) {
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
        return
    }

    // Editor view when everything is loaded
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val height = convertDpToPixel(this.maxHeight, context).toInt()
        val width = convertDpToPixel(this.maxWidth, context).toInt()

        // Update dimensions when screen size changes
        LaunchedEffect(width, height) {
            if (pageView!!.viewWidth != width || pageView!!.viewHeight != height) {
                pageView!!.updateDimensions(width, height)
                DrawCanvas.refreshUi.emit(Unit)
            }
        }

        // Reset zoom when changing drawing mode
        LaunchedEffect(editorState!!.mode) {
            if (editorState!!.zoomScale != 1.0f) {
                editorState!!.resetZoom()
                DrawCanvas.refreshUi.emit(Unit)
            }
        }

        // Force a refresh to ensure strokes are displayed
        LaunchedEffect(Unit) {
            DrawCanvas.refreshUi.emit(Unit)
        }

        // Monitor changes that require saving to database
        DisposableEffect(pageView) {
            onDispose {
                // Make sure any pending changes are saved
                scope.launch {
                    app.pageRepository.updateLastModifiedAt(pageId)
                }
            }
        }

        // Render the editor
        NotesTheme {
            EditorSurface(
                state = editorState!!,
                page = pageView!!
            )

            Toolbar(
                state = editorState!!
            )
        }
    }
}