package com.wyldsoft.notes.touchHandling

import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wyldsoft.notes.gesture.DrawingGestureDetector
import com.wyldsoft.notes.selection.SelectionHandler
import com.wyldsoft.notes.strokeManagement.interfaces.IStrokeManager
import com.wyldsoft.notes.utils.EditorState
import com.wyldsoft.notes.utils.Mode
import kotlinx.coroutines.CoroutineScope

/**
 * Processes touch events and routes them to appropriate handlers.
 * Determines event type and delegates to drawing, gesture, or selection handlers.
 */
class TouchEventProcessor(
    private val state: EditorState,
    private val strokeManager: IStrokeManager,
    private val gestureDetector: DrawingGestureDetector,
    private val selectionHandler: SelectionHandler,
    private val coroutineScope: CoroutineScope
) {
    var currentlyErasing by mutableStateOf(false)

    /**
     * Processes incoming touch events and routes to appropriate handler
     */
    fun processTouchEvent(event: MotionEvent): Boolean {
        // Check if drawing/erasing is in progress
        if (isProcessingInProgress()) {
            return false
        }

        // Handle selection mode with stylus priority
        if (state.mode == Mode.Selection && isStylusEvent(event)) {
            selectionHandler.handleTouchEvent(event.action, event.x, event.y)
            return true
        }

        // For stylus events in other modes, let normal handling continue
        if (isStylusEvent(event)) {
            return false
        }

        // Handle finger gestures
        return gestureDetector.onTouchEvent(event)
    }

    /**
     * Checks if stylus is being used for input
     */
    private fun isStylusEvent(event: MotionEvent): Boolean {
        return event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
    }

    /**
     * Checks if drawing or erasing operations are currently in progress
     */
    private fun isProcessingInProgress(): Boolean {
        // This would need to be implemented with proper state management
        return currentlyErasing // Simplified for now
    }

    /**
     * Updates the erasing state
     */
    fun setErasingState(isErasing: Boolean) {
        currentlyErasing = isErasing
    }
}