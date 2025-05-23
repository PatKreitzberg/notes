package com.wyldsoft.notes.classes.drawing

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.view.SurfaceView
import androidx.compose.ui.unit.dp
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.utils.EditorState
import com.wyldsoft.notes.utils.Mode
import com.wyldsoft.notes.utils.Pen
import com.wyldsoft.notes.utils.SimplePointF
import com.wyldsoft.notes.utils.convertDpToPixel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.thread
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.wyldsoft.notes.gesture.DrawingGestureDetector
import com.wyldsoft.notes.pagination.PaginationManager
import com.wyldsoft.notes.transform.ViewportTransformer
import com.wyldsoft.notes.selection.SelectionHandler
import com.wyldsoft.notes.views.PageView

class PenStyleConverter {
    companion object {
        // Map each pen type to the corresponding Onyx SDK stroke style
        private val penStyleMap = mapOf(
            Pen.BALLPEN to com.onyx.android.sdk.pen.style.StrokeStyle.PENCIL,
            Pen.MARKER to com.onyx.android.sdk.pen.style.StrokeStyle.MARKER,
            Pen.FOUNTAIN to com.onyx.android.sdk.pen.style.StrokeStyle.FOUNTAIN
        )

        fun convertPenToStrokeStyle(pen: Pen): Int {
            return penStyleMap[pen] ?: com.onyx.android.sdk.pen.style.StrokeStyle.PENCIL
        }
    }
}

/**
 * Handles touch events and delegates them to appropriate handlers.
 * Manages the Onyx TouchHelper and input callbacks.
 */
class TouchEventHandler(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val coroutineScope: CoroutineScope,
    private val state: EditorState,
    private val drawingManager: DrawingManager,
    private val canvasRenderer: CanvasRenderer,
    private val viewportTransformer: ViewportTransformer
) {
    private var isStrokeOptionsPanelOpen = false


    // Get maximum pressure from the device
    private val pressure = EpdController.getMaxTouchPressure()
    private var referencedSurfaceView: String = ""
    var currentlyErasing by mutableStateOf(false)
    private val paginationManager: PaginationManager
        get() = viewportTransformer.getPaginationManager()

    private val selectionHandler by lazy {
        SelectionHandler(
            context,
            state,
            state.pageView,
            coroutineScope
        )
    }

    /*
      start gesture detection
    */
    val gestureDetector = DrawingGestureDetector(
        context = context,
        viewportTransformer = viewportTransformer,
        coroutineScope = coroutineScope,
        onGestureDetected = { gesture -> println("Gesture detected: $gesture") }
    )

    private fun isStylusEvent(event: MotionEvent): Boolean {
        return event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setupTouchInterception() {
        println("DEBUG: Setting up touch interception")
        surfaceView.setOnTouchListener { _, event ->
            if (DrawingManager.drawingInProgress.isLocked || currentlyErasing) {
                // If we're in drawing or erasing mode and already processing inputs, don't interrupt
                return@setOnTouchListener false
            }

            if (state.mode == Mode.Selection && isStylusEvent(event)) {
                // Handle selection mode first - it has priority for stylus events
                selectionHandler.handleTouchEvent(event.action, event.x, event.y)
                return@setOnTouchListener true
            }

            if (isStylusEvent(event)) {
                // For other modes, let the normal handling continue
                return@setOnTouchListener false
            }

            return@setOnTouchListener gestureDetector.onTouchEvent(event)
        }

        // Get the gesture handler for this detector
        val gestureHandler = gestureDetector.getGestureHandler()

        // We can now access and configure the gesture handler if needed
        // For example, we could override specific gestures for the current state
    }

    /*
      End of gesture detection
    */

    private val inputCallback: RawInputCallback = object : RawInputCallback() {
        override fun onBeginRawDrawing(p0: Boolean, p1: com.onyx.android.sdk.data.note.TouchPoint?) {
            //println("DEBUG: onBeginRawDrawing called")
        }

        override fun onEndRawDrawing(p0: Boolean, p1: com.onyx.android.sdk.data.note.TouchPoint?) {
            //println("DEBUG: onEndRawDrawing called")
        }

        override fun onRawDrawingTouchPointMoveReceived(p0: com.onyx.android.sdk.data.note.TouchPoint?) {
            //println("DEBUG: onRawDrawingTouchPointMoveReceived called")
        }

        override fun onRawDrawingTouchPointListReceived(plist: TouchPointList) {
            println("DEBUG: onRawDrawingTouchPointListReceived with ${plist.size()} points---------------------------------------------")
            val points = plist

            // Now use the adjusted touch points for drawing operations
            if (state.mode == Mode.Draw) {
                //println("DEBUG: Processing touch for DRAW mode")
                coroutineScope.launch(Dispatchers.Main.immediate) {
                    if (lockDrawingWithTimeout()) {
                        try {
                            drawingManager.handleDraw(
                                state.penSettings[state.pen.penName]!!.strokeSize,
                                state.penSettings[state.pen.penName]!!.color,
                                state.pen,
                                points.points
                            )
                            //println("DEBUG: handleDraw completed")

                            // Ensure we render the result immediately
                            canvasRenderer.drawCanvasToView()
                        } finally {
                            DrawingManager.drawingInProgress.unlock()
                        }
                    } else {
                        //println("DEBUG: Could not acquire drawing lock, skipping stroke")
                    }
                }
            } else thread {
                if (state.mode == Mode.Erase) {
                    //println("DEBUG: Processing touch for ERASE mode")

                    val erasePoints = plist.points.map { SimplePointF(it.x, it.y) }
                    drawingManager.handleErase(
                        erasePoints,
                        eraser = state.eraser
                    )

                    // Important: Immediately render the erasing results
                    canvasRenderer.drawCanvasToView()

                    // Force a UI refresh to ensure changes are visible
                    coroutineScope.launch {
                        DrawingManager.refreshUi.emit(Unit)
                    }
                }
            }
            println("DEBUG: onRawDrawingTouchPointListReceived End=================================================================\n\n")
        }

        override fun onBeginRawErasing(p0: Boolean, p1: com.onyx.android.sdk.data.note.TouchPoint?) {
            currentlyErasing = true
        }
        override fun onEndRawErasing(p0: Boolean, p1: com.onyx.android.sdk.data.note.TouchPoint?) {
            currentlyErasing = false
        }

        override fun onRawErasingTouchPointListReceived(plist: TouchPointList?) {
            if (plist == null) return
            val points = plist.points.map { SimplePointF(it.x, it.y) }

            drawingManager.handleErase(
                points,
                eraser = state.eraser
            )

            // Ensure immediate visual feedback
            canvasRenderer.drawCanvasToView()

            // Force UI refresh
            coroutineScope.launch {
                DrawingManager.refreshUi.emit(Unit)
            }
        }

        override fun onRawErasingTouchPointMoveReceived(p0: com.onyx.android.sdk.data.note.TouchPoint?) {}

        override fun onPenUpRefresh(refreshRect: RectF?) {
            super.onPenUpRefresh(refreshRect)

            // Ensure UI is refreshed when pen is lifted
            canvasRenderer.drawCanvasToView()
            coroutineScope.launch {
                DrawingManager.refreshUi.emit(Unit)
            }
        }

        override fun onPenActive(point: com.onyx.android.sdk.data.note.TouchPoint?) {
            super.onPenActive(point)
        }
    }

    private val touchHelper by lazy {
        referencedSurfaceView = surfaceView.hashCode().toString()
        TouchHelper.create(surfaceView, inputCallback)
    }

    suspend fun lockDrawingWithTimeout(): Boolean {
        return withTimeoutOrNull(500) {
            DrawingManager.drawingInProgress.tryLock(500)
            true
        } ?: false
    }

    fun setRawDrawingEnabled(enabled: Boolean) {
        touchHelper.setRawDrawingEnabled(enabled)
    }

    fun closeRawDrawing() {
        touchHelper.closeRawDrawing()
    }

    fun updateActiveSurface() {
        println("Update editable surface is state.stateExcludeRects.size  ${state.stateExcludeRects.size} ")
        state.stateExcludeRectsModified = false
        val toolbarHeight = if (state.isToolbarOpen) convertDpToPixel(40.dp, context).toInt() else 0

        // Calculate the height of the stroke options panel if it's open
        val optionsPanelHeight = if (isStrokeOptionsPanelOpen) {
            // Use our estimated height for the options panel
            convertDpToPixel(310.dp, context).toInt()
        } else {
            0
        }

        setRawDrawingEnabled(false)
        closeRawDrawing()

        // Create combined exclusion height for toolbar + options panel
        val topExclusionHeight = toolbarHeight + optionsPanelHeight

        // Set exclude rect for toolbar + options panel if open
        val topExcludeRect = Rect(0, 0, surfaceView.width, topExclusionHeight)

        // Create a list of exclusion zones if pagination is enabled
        val excludeRects = mutableListOf(topExcludeRect)
        for ((key, rect) in state.stateExcludeRects) {
            println("Adding exclusion rect for $key: $rect")
            excludeRects.add(rect)
        }

        println("exclusion paginationManager.isPaginationEnabled=${paginationManager.isPaginationEnabled}")

        // Add pagination exclusion zones if enabled
        if (state.allowDrawingOnCanvas && paginationManager.isPaginationEnabled) {
            val viewportTop = viewportTransformer.scrollY
            val viewportHeight = surfaceView.height.toFloat()

            // Get all exclusion zones visible in the current viewport
            val exclusionZones = paginationManager.getExclusionZonesInViewport(viewportTop, viewportHeight)
            println("exclusion exclusionZones=$exclusionZones")

            // Transform to view coordinates and add to exclude rects
            exclusionZones.forEach { rect ->
                val top = rect.top - viewportTop.toInt()
                val bottom = rect.bottom - viewportTop.toInt()

                println("exclusion check")
                // Only add if visible in the viewport
                if (bottom >= 0 && top <= viewportHeight) {
                    println("exclusion is in viewable area")
                    excludeRects.add(
                        Rect(
                            0,
                            top.coerceAtLeast(0),
                            surfaceView.width,
                            bottom.coerceAtMost(surfaceView.height)
                        )
                    )
                }
            }
        }

        if (!state.allowDrawingOnCanvas) {
            println("Excluding everything")
            excludeRects.add(Rect(0, 0, surfaceView.width, surfaceView.height))
        }

        touchHelper.setLimitRect(
            mutableListOf(
                Rect(
                    0, 0, surfaceView.width, surfaceView.height
                )
            )
        ).setExcludeRect(excludeRects)
            .openRawDrawing()

        setRawDrawingEnabled(true)
        updatePenAndStroke()
    }

    fun updatePenAndStroke() {
        println("DEBUG: Update pen and stroke")
        try {
            when (state.mode) {
                Mode.Draw -> {
                    println("DEBUG: Mode is DRAW, pen=${state.pen.penName}")
                    val strokeStyle = penToStroke(state.pen)
                    val strokeWidth = state.penSettings[state.pen.penName]!!.strokeSize
                    val strokeColor = state.penSettings[state.pen.penName]!!.color

                    println("DEBUG: Setting stroke style=$strokeStyle, width=$strokeWidth, color=$strokeColor")
                    // Apply the settings to the touch helper
                    touchHelper.setStrokeStyle(strokeStyle)
                        ?.setStrokeWidth(strokeWidth)
                        ?.setStrokeColor(strokeColor)

                    // Force a refresh to show the changes immediately
                    coroutineScope.launch {
                        DrawingManager.refreshUi.emit(Unit)
                    }
                }
                Mode.Erase -> {
                    println("DEBUG: Mode is ERASE, eraser=${state.eraser}")
                    when (state.eraser) {
                        com.wyldsoft.notes.utils.Eraser.PEN -> {
                            touchHelper.setStrokeStyle(penToStroke(com.wyldsoft.notes.utils.Pen.MARKER))
                                ?.setStrokeWidth(30f)
                                ?.setStrokeColor(android.graphics.Color.GRAY)
                        }
                        com.wyldsoft.notes.utils.Eraser.SELECT -> {
                            touchHelper.setStrokeStyle(penToStroke(com.wyldsoft.notes.utils.Pen.BALLPEN))
                                ?.setStrokeWidth(3f)
                                ?.setStrokeColor(android.graphics.Color.GRAY)
                        }
                    }
                }
                Mode.Selection -> {
                    println("DEBUG: Mode is SELECTION")
                    // For selection mode, use a thin pen style for drawing the selection area
                    touchHelper.setStrokeStyle(penToStroke(com.wyldsoft.notes.utils.Pen.BALLPEN))
                        ?.setStrokeWidth(2f)
                        ?.setStrokeColor(android.graphics.Color.BLUE)
                }
            }
            println("DEBUG: updatePenAndStroke completed successfully")
        } catch (e: Exception) {
            println("DEBUG ERROR: Error in updatePenAndStroke: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun penToStroke(pen: Pen): Int {
        println("DEBUG: Converting pen ${pen.penName} to stroke style")
        val result = PenStyleConverter.convertPenToStrokeStyle(pen)
        println("DEBUG: Pen ${pen.penName} converted to stroke style $result")
        return result
    }
}