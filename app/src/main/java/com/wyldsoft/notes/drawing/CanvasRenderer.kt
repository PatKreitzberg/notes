package com.wyldsoft.notes.classes.drawing

import android.graphics.RectF
import android.view.SurfaceView
import com.wyldsoft.notes.views.PageView
import com.wyldsoft.notes.pagination.PageRenderer
import com.wyldsoft.notes.settings.SettingsRepository
import com.wyldsoft.notes.templates.TemplateRenderer

/**
 * Handles rendering the canvas content to the screen.
 * Manages synchronization with drawing operations.
 */
class CanvasRenderer(
    private val surfaceView: SurfaceView,
    private val page: PageView,
    private val settingsRepository: SettingsRepository,
    private val templateRenderer: TemplateRenderer
) {
    private val pageRenderer: PageRenderer = PageRenderer(
        page.viewportTransformer,
        settingsRepository,
        templateRenderer
    )
    /**
     * Renders the current page state to the surface view
     */
    fun drawCanvasToView() {
        val canvas = surfaceView.holder.lockCanvas() ?: return

        // Clear the canvas
        canvas.drawColor(android.graphics.Color.WHITE)

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
            println("scroll drawCanvasToView drawStroke")
            page.drawStroke(canvas, stroke)
        }

        // Render pagination elements (page numbers and exclusion zones)
        pageRenderer.renderPaginationElements(canvas)

        // Finish rendering
        surfaceView.holder.unlockCanvasAndPost(canvas)
    }
}