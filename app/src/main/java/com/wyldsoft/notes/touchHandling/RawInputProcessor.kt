package com.wyldsoft.notes.touchHandling

import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.rendering.interfaces.ICanvasRenderer
import com.wyldsoft.notes.strokeManagement.interfaces.IStrokeManager
import com.wyldsoft.notes.utils.EditorState
import com.wyldsoft.notes.utils.Mode
import com.wyldsoft.notes.utils.SimplePointF
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.thread

/**
 * Processes raw input from Onyx SDK.
 * Handles touch point data and converts it to drawing/erasing operations.
 */
class RawInputProcessor(
    private val state: EditorState,
    private val strokeManager: IStrokeManager,
    private val canvasRenderer: ICanvasRenderer,
    private val coroutineScope: CoroutineScope,
    private val touchEventProcessor: TouchEventProcessor
) : RawInputCallback() {

    override fun onBeginRawDrawing(p0: Boolean, p1: com.onyx.android.sdk.data.note.TouchPoint?) {
        // Begin drawing operation
    }

    override fun onEndRawDrawing(p0: Boolean, p1: com.onyx.android.sdk.data.note.TouchPoint?) {
        // End drawing operation
    }

    override fun onRawDrawingTouchPointMoveReceived(p0: com.onyx.android.sdk.data.note.TouchPoint?) {
        // Handle individual point moves
    }

    override fun onRawDrawingTouchPointListReceived(plist: TouchPointList) {
        println("DEBUG: onRawDrawingTouchPointListReceived with ${plist.size()} points")
        
        if (state.mode == Mode.Draw) {
            handleDrawingPoints(plist)
        } else if (state.mode == Mode.Erase) {
            handleErasingPoints(plist)
        }
    }

    override fun onBeginRawErasing(p0: Boolean, p1: com.onyx.android.sdk.data.note.TouchPoint?) {
        touchEventProcessor.setErasingState(true)
    }

    override fun onEndRawErasing(p0: Boolean, p1: com.onyx.android.sdk.data.note.TouchPoint?) {
        touchEventProcessor.setErasingState(false)
    }

    override fun onRawErasingTouchPointListReceived(plist: TouchPointList?) {
        if (plist == null) return
        
        val points = plist.points.map { SimplePointF(it.x, it.y) }
        strokeManager.handleErase(points, state.eraser)
        
        // Ensure immediate visual feedback
        canvasRenderer.drawCanvasToView()
        refreshUI()
    }

    override fun onRawErasingTouchPointMoveReceived(p0: com.onyx.android.sdk.data.note.TouchPoint?) {
        // Handle individual erase point moves
    }

    override fun onPenUpRefresh(refreshRect: android.graphics.RectF?) {
        super.onPenUpRefresh(refreshRect)
        canvasRenderer.drawCanvasToView()
        refreshUI()
    }

    override fun onPenActive(point: com.onyx.android.sdk.data.note.TouchPoint?) {
        super.onPenActive(point)
    }

    /**
     * Handles drawing mode touch points
     */
    private fun handleDrawingPoints(plist: TouchPointList) {
        coroutineScope.launch(Dispatchers.Main.immediate) {
            if (lockDrawingWithTimeout()) {
                try {
                    strokeManager.handleDraw(
                        state.penSettings[state.pen.penName]!!.strokeSize,
                        state.penSettings[state.pen.penName]!!.color,
                        state.pen,
                        plist.points
                    )
                    canvasRenderer.drawCanvasToView()
                } finally {
                    // Release drawing lock
                }
            }
        }
    }

    /**
     * Handles erasing mode touch points
     */
    private fun handleErasingPoints(plist: TouchPointList) {
        thread {
            val erasePoints = plist.points.map { SimplePointF(it.x, it.y) }
            strokeManager.handleErase(erasePoints, state.eraser)
            
            canvasRenderer.drawCanvasToView()
            refreshUI()
        }
    }

    /**
     * Attempts to lock drawing operations with timeout
     */
    private suspend fun lockDrawingWithTimeout(): Boolean {
        return withTimeoutOrNull(500) {
            // Implementation would depend on stroke manager's locking mechanism
            true
        } ?: false
    }

    /**
     * Refreshes the UI
     */
    private fun refreshUI() {
        coroutineScope.launch {
            // Emit refresh signal - implementation depends on UI framework
        }
    }
}