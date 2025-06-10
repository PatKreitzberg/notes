package com.wyldsoft.notes.drawing

import android.content.Context
import android.view.SurfaceView
import com.wyldsoft.notes.rendering.interfaces.ICanvasRenderer
import com.wyldsoft.notes.strokeManagement.interfaces.IStrokeManager
import com.wyldsoft.notes.touchHandling.TouchEventHandler as NewTouchEventHandler
import com.wyldsoft.notes.touchHandling.interfaces.ITouchEventHandler
import com.wyldsoft.notes.refreshingScreen.interfaces.IViewportTransformer
import com.wyldsoft.notes.utils.EditorState
import kotlinx.coroutines.CoroutineScope

/**
 * Bridge class that wraps the new TouchEventHandler to maintain compatibility
 * with existing code while using the new architecture.
 */
class TouchEventHandlerBridge(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val coroutineScope: CoroutineScope,
    private val state: EditorState,
    private val strokeManager: IStrokeManager,
    private val canvasRenderer: ICanvasRenderer,
    private val viewportTransformer: IViewportTransformer
) : ITouchEventHandler {

    private val newTouchEventHandler = NewTouchEventHandler(
        context = context,
        surfaceView = surfaceView,
        coroutineScope = coroutineScope,
        state = state,
        strokeManager = strokeManager,
        canvasRenderer = canvasRenderer,
        viewportTransformer = viewportTransformer
    )

    override fun setupTouchInterception() {
        newTouchEventHandler.setupTouchInterception()
    }

    override fun updateActiveSurface() {
        newTouchEventHandler.updateActiveSurface()
    }

    override fun updatePenAndStroke() {
        newTouchEventHandler.updatePenAndStroke()
    }

    override fun setRawDrawingEnabled(enabled: Boolean) {
        newTouchEventHandler.setRawDrawingEnabled(enabled)
    }

    override fun closeRawDrawing() {
        newTouchEventHandler.closeRawDrawing()
    }

    /**
     * Updates stroke options panel state
     */
    fun setStrokeOptionsPanelOpen(isOpen: Boolean) {
        newTouchEventHandler.setStrokeOptionsPanelOpen(isOpen)
    }
}