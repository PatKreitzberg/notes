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
import com.wyldsoft.notes.classes.DrawCanvas
import com.wyldsoft.notes.classes.PageView
import com.wyldsoft.notes.classes.drawing.DrawingManager
import com.wyldsoft.notes.components.EditorSurface
import com.wyldsoft.notes.components.Toolbar
import com.wyldsoft.notes.ui.theme.NotesTheme
import com.wyldsoft.notes.utils.EditorState
import com.wyldsoft.notes.utils.convertDpToPixel
import com.wyldsoft.notes.components.ScrollIndicator
import com.wyldsoft.notes.components.TopBoundaryIndicator
import java.util.UUID
import com.wyldsoft.notes.settings.SettingsRepository
import com.wyldsoft.notes.templates.TemplateRenderer

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EditorView() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Initialize settings repository
    val settingsRepository = remember { SettingsRepository(context) }

    // Initialize template renderer
    val templateRenderer = remember { TemplateRenderer(context) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val height = convertDpToPixel(this.maxHeight, context).toInt()
        val width = convertDpToPixel(this.maxWidth, context).toInt()

        val pageId = remember { UUID.randomUUID().toString() }

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

        // Dynamically update the page width when the Box constraints change
        LaunchedEffect(width, height) {
            if (page.width != width || page.viewHeight != height) {
                page.updateDimensions(width, height)
                DrawingManager.refreshUi.emit(Unit)
            }
        }

        val editorState = remember { EditorState(pageId = pageId, pageView = page) }

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