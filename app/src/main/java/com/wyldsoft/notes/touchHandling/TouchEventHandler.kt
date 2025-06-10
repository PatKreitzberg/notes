package com.wyldsoft.notes.touchHandling

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.view.SurfaceView
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.pen.TouchHelper
import com.wyldsoft.notes.gesture.DrawingGestureDetector
import com.wyldsoft.notes.rendering.interfaces.ICanvasRenderer
import com.wyldsoft.notes.selection.SelectionHandler
import com.wyldsoft.notes.strokeManagement.interfaces.IStrokeManager
import com.wyldsoft.notes.touchHandling.interfaces.ITouchEventHandler
import com.wyldsoft.notes.refreshingScreen.interfaces.IViewportTransformer
import com.wyldsoft.notes.utils.EditorState
import com.wyldsoft.notes.utils.Mode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Main touch event handler that coordinates all touch-related operations.
 * Uses composition to delegate to specialized handlers for different input types.
 */
class TouchEventHandler(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val coroutineScope: CoroutineScope,
    private val state: EditorState,
    private val strokeManager: IStrokeManager,
    private val canvasRenderer: ICanvasRenderer,
    private val viewportTransformer: IViewportTransformer
) : ITouchEventHandler {

    // Component dependencies
    private val selectionHandler by lazy {
        SelectionHandler(context, state, state.pageView, coroutineScope)
    }

    private val gestureDetector = DrawingGestureDetector(
        context = context,
        viewportTransformer = viewportTransformer as com.wyldsoft.notes.refreshingScreen.ViewportTransformer,
        coroutineScope = coroutineScope,
        onGestureDetected = { gesture -> println("Gesture detected: $gesture") }
    )

    private val touchEventProcessor = TouchEventProcessor(
        state, strokeManager, gestureDetector, selectionHandler, coroutineScope
    )

    private val rawInputProcessor = RawInputProcessor(
        state, strokeManager, canvasRenderer, coroutineScope, touchEventProcessor
    )

    private val exclusionZoneManager = ExclusionZoneManager(
        context, surfaceView, state, viewportTransformer
    )

    private val penConfigurationManager = PenConfigurationManager(state)

    // Touch helper setup
    private val pressure = EpdController.getMaxTouchPressure()
    private var referencedSurfaceView: String = ""

    private val touchHelper by lazy {
        referencedSurfaceView = surfaceView.hashCode().toString()
        TouchHelper.create(surfaceView, rawInputProcessor)
    }

    /**
     * Sets up touch interception for surface view
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun setupTouchInterception() {
        println("DEBUG: Setting up touch interception")
        surfaceView.setOnTouchListener { _, event ->
            touchEventProcessor.processTouchEvent(event)
        }
    }

    /**
     * Updates the active drawing surface with exclusion zones
     */
    override fun updateActiveSurface() {
        println("Update editable surface is state.stateExcludeRects.size ${state.stateExcludeRects.size}")
        
        // Disable and close current drawing
        setRawDrawingEnabled(false)
        closeRawDrawing()

        // Calculate all exclusion zones
        val excludeRects = exclusionZoneManager.calculateExclusionZones()

        // Set up touch helper with limit and exclusion rects
        touchHelper.setLimitRect(
            mutableListOf(Rect(0, 0, surfaceView.width, surfaceView.height))
        ).setExcludeRect(excludeRects)
        .openRawDrawing()

        // Re-enable drawing and update pen settings
        setRawDrawingEnabled(true)
        updatePenAndStroke()
    }

    /**
     * Updates pen and stroke settings
     */
    override fun updatePenAndStroke() {
        println("DEBUG: Update pen and stroke")
        
        try {
            val config = penConfigurationManager.getPenConfiguration(state.mode)
            config?.let { (strokeStyle, strokeWidth, strokeColor) ->
                touchHelper.setStrokeStyle(strokeStyle)
                    ?.setStrokeWidth(strokeWidth)
                    ?.setStrokeColor(strokeColor)
            }

            // Force UI refresh
            coroutineScope.launch {
                com.wyldsoft.notes.strokeManagement.DrawingManager.strokeStyleChanged.emit(Unit)
            }
        } catch (e: Exception) {
            println("DEBUG ERROR: Error in updatePenAndStroke: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Enables or disables raw drawing mode
     */
    override fun setRawDrawingEnabled(enabled: Boolean) {
        touchHelper.setRawDrawingEnabled(enabled)
    }

    /**
     * Closes raw drawing mode
     */
    override fun closeRawDrawing() {
        touchHelper.closeRawDrawing()
    }

    /**
     * Updates stroke options panel state
     */
    fun setStrokeOptionsPanelOpen(isOpen: Boolean) {
        exclusionZoneManager.setStrokeOptionsPanelOpen(isOpen)
    }
}