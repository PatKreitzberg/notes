package com.wyldsoft.notes.classes.drawing

import android.graphics.RectF
import android.view.SurfaceView
import androidx.compose.ui.unit.IntOffset
import com.wyldsoft.notes.classes.PageView

/**
 * Handles rendering the canvas content to the screen.
 * Manages synchronization with drawing operations.
 */
class CanvasRenderer(
    private val surfaceView: SurfaceView,
    private val page: PageView
) {
    /**
     * Renders the current page state to the surface view
     */
    fun drawCanvasToView() {
        val canvas = surfaceView.holder.lockCanvas() ?: return

        // Clear the canvas
        canvas.drawColor(android.graphics.Color.WHITE)

        // Get the current viewport in page coordinates
        val viewport = page.viewportTransformer.getCurrentViewportInPageCoordinates()

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

            page.drawStroke(canvas, stroke, IntOffset(0, 0))
        }

        // Finish rendering
        surfaceView.holder.unlockCanvasAndPost(canvas)
    }
}