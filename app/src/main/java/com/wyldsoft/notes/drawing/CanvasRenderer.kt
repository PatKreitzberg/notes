package com.wyldsoft.notes.classes.drawing

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.ui.unit.IntOffset
import com.wyldsoft.notes.classes.PageView
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

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

    /**
     * Wait for any ongoing drawing operations to complete
     */
    suspend fun waitForDrawing() {
        withTimeoutOrNull(3000) {
            // Just to make sure wait 1ms before checking lock.
            delay(1)
            // Wait until drawingInProgress is unlocked before proceeding
            while (DrawingManager.drawingInProgress.isLocked) {
                delay(5)
            }
        } ?: println("Timeout while waiting for drawing lock. Potential deadlock.")
    }
}