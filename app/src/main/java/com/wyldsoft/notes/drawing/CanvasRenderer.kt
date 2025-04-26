package com.wyldsoft.notes.classes.drawing

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

        // Draw strokes
        for (stroke in page.strokes) {
            page.drawStroke(canvas, stroke, IntOffset(0, 0))
        }

        // Finish rendering
        surfaceView.holder.unlockCanvasAndPost(canvas)
    }
}