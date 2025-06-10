package com.wyldsoft.notes.rendering

import android.graphics.RectF
import android.view.SurfaceView
import com.wyldsoft.notes.views.PageView
import com.wyldsoft.notes.rendering.interfaces.ICanvasRenderer
import com.wyldsoft.notes.rendering.interfaces.IPageRenderer
import com.wyldsoft.notes.selection.SelectionHandler
import com.wyldsoft.notes.settings.SettingsRepository
import com.wyldsoft.notes.templates.TemplateRenderer
import com.wyldsoft.notes.utils.EditorState
import com.wyldsoft.notes.utils.Mode
import com.wyldsoft.notes.search.SearchHighlighter
import com.wyldsoft.notes.search.SearchManager

/**
 * Main canvas renderer that coordinates rendering operations.
 * Implements the facade pattern to orchestrate different rendering components.
 */
class CanvasRenderer(
    private val surfaceView: SurfaceView,
    private val page: PageView,
    private val settingsRepository: SettingsRepository,
    private val templateRenderer: TemplateRenderer,
    private val editorState: EditorState,
    private val selectionHandler: SelectionHandler? = null,
    private val searchManager: SearchManager? = null
) : ICanvasRenderer {
    
    private val pageRenderer: IPageRenderer = PageRenderer(
        page.viewportTransformer,
        settingsRepository,
        templateRenderer
    )

    private val searchHighlighter: SearchHighlighter? = searchManager?.let {
        SearchHighlighter(it, page.viewportTransformer)
    }
    
    private val strokeRenderer = StrokeRenderer(page.viewportTransformer)

    /**
     * Initializes the renderer and draws initial content
     */
    override fun initialize() {
        drawCanvasToView()
    }

    /**
     * Renders the current page state to the surface view
     */
    override fun drawCanvasToView() {
        if (!surfaceView.holder.surface.isValid) {
            println("DEBUG: Surface not valid, skipping draw")
            return
        }

        val canvas = surfaceView.holder.lockCanvas() ?: return

        try {
            // Clear the canvas
            canvas.drawColor(android.graphics.Color.WHITE)

            // Render pagination elements
            pageRenderer.renderPaginationElements(canvas)

            // Render strokes
            strokeRenderer.renderVisibleStrokes(canvas, page.strokes)

            // Render search highlights
            searchHighlighter?.drawHighlights(canvas)

            // Render selection if in selection mode
            if (editorState.mode == Mode.Selection && selectionHandler != null) {
                selectionHandler.renderSelection(canvas)
            }
        } finally {
            surfaceView.holder.unlockCanvasAndPost(canvas)
        }
    }
}