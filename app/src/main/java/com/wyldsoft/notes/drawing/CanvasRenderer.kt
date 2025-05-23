package com.wyldsoft.notes.classes.drawing

import android.graphics.RectF
import android.view.SurfaceView
import com.wyldsoft.notes.views.PageView
import com.wyldsoft.notes.pagination.PageRenderer
import com.wyldsoft.notes.selection.SelectionHandler
import com.wyldsoft.notes.settings.SettingsRepository
import com.wyldsoft.notes.templates.TemplateRenderer
import com.wyldsoft.notes.utils.EditorState
import com.wyldsoft.notes.utils.Mode
import com.wyldsoft.notes.search.SearchHighlighter
import com.wyldsoft.notes.search.SearchManager


/**
 * Handles rendering the canvas content to the screen.
 * Manages synchronization with drawing operations.
 */
class CanvasRenderer(
    private val surfaceView: SurfaceView,
    private val page: PageView,
    private val settingsRepository: SettingsRepository,
    private val templateRenderer: TemplateRenderer,
    private val editorState: EditorState,
    private val selectionHandler: SelectionHandler? = null,
    private val searchManager: SearchManager? = null
) {
    private val pageRenderer: PageRenderer = PageRenderer(
        page.viewportTransformer,
        settingsRepository,
        templateRenderer
    )

    private val searchHighlighter: SearchHighlighter? = searchManager?.let {
        SearchHighlighter(it, page.viewportTransformer)
    }

    /**
     * Initializes the renderer and draws initial content
     */
    fun initialize() {
        // Draw initial content when the renderer is created
        drawCanvasToView()
    }

    /**
     * Renders the current page state to the surface view
     */
    fun drawCanvasToView() {
        if (!surfaceView.holder.surface.isValid) {
            println("DEBUG: Surface not valid, skipping draw")
            return
        }

        val canvas = surfaceView.holder.lockCanvas() ?: return

        // Clear the canvas
        canvas.drawColor(android.graphics.Color.WHITE)

        // Render pagination elements - now with viewportTransformer for zoom support
        pageRenderer.renderPaginationElements(canvas)

        // Draw only visible strokes
        for (stroke in page.strokes) {
            val strokeBounds = RectF(
                stroke.left,
                stroke.top,
                stroke.right,
                stroke.bottom
            )

            // Skip strokes that are not in the viewport
            if (!page.viewportTransformer.isRectVisible(strokeBounds)) {
                continue
            }

            page.drawStroke(canvas, stroke)
        }

        searchHighlighter?.drawHighlights(canvas)

        // Render selection if in selection mode and handler exists
        if (editorState.mode == Mode.Selection && selectionHandler != null) {
            selectionHandler.renderSelection(canvas)
        }

        // Finish rendering
        surfaceView.holder.unlockCanvasAndPost(canvas)
    }
}