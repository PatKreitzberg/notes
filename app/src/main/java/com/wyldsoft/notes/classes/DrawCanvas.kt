package com.wyldsoft.notes.classes

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Looper
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.utils.DrawingGestureDetector
import com.wyldsoft.notes.utils.Eraser
import com.wyldsoft.notes.utils.Mode
import com.wyldsoft.notes.utils.Pen
import com.wyldsoft.notes.utils.SimplePointF
import com.wyldsoft.notes.utils.Stroke
import com.wyldsoft.notes.utils.StrokePoint
import com.wyldsoft.notes.utils.EditorState
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList
import com.wyldsoft.notes.utils.GestureAction
import com.wyldsoft.notes.utils.GestureSettingsManager
import com.wyldsoft.notes.utils.GestureType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.thread
import com.wyldsoft.notes.utils.PageTemplate
import com.wyldsoft.notes.utils.convertDpToPixel

// Get maximum pressure from the device
val pressure = EpdController.getMaxTouchPressure()

// Keep reference of the currently associated surface view
var referencedSurfaceView: String = ""

class DrawCanvas(
    context: Context,
    val coroutineScope: CoroutineScope,
    val state: EditorState,
    val page: PageView
) : SurfaceView(context) {
    private val strokeHistoryBatch = mutableListOf<String>()

    // List of drawable areas
    private var drawableRects = mutableListOf<Rect>()

    // Gesture detector for the drawing surface
    private lateinit var gestureDetector: DrawingGestureDetector

    companion object {
        var forceUpdate = MutableSharedFlow<Rect?>()
        var refreshUi = MutableSharedFlow<Unit>()
        var isDrawing = MutableSharedFlow<Boolean>()
        var restartAfterConfChange = MutableSharedFlow<Unit>()
        var drawingInProgress = Mutex()
    }

    fun getActualState(): EditorState {
        return this.state
    }

    private val inputCallback: RawInputCallback = object : RawInputCallback() {
        override fun onBeginRawDrawing(p0: Boolean, p1: com.onyx.android.sdk.data.note.TouchPoint?) {
            println("DEBUG: onBeginRawDrawing called")
        }

        override fun onEndRawDrawing(p0: Boolean, p1: com.onyx.android.sdk.data.note.TouchPoint?) {
            println("DEBUG: onEndRawDrawing called")
        }

        override fun onRawDrawingTouchPointMoveReceived(p0: com.onyx.android.sdk.data.note.TouchPoint?) {
            println("DEBUG: onRawDrawingTouchPointMoveReceived called")
        }

        override fun onRawDrawingTouchPointListReceived(plist: TouchPointList) {
            println("DEBUG: onRawDrawingTouchPointListReceived with ${plist.size()} points")

            // dont draw if settings is open
            if (getActualState().isSettingsDialogOpen) {
                return
            }

            val startTime = System.currentTimeMillis()

            // When zoomed, we need to transform the touch points
            val adjustedPoints = if (state.zoomScale != 1.0f) {
                // Create a new touch point list with transformed coordinates
                TouchPointList().apply {
                    val centerX = this@DrawCanvas.width / 2f
                    val centerY = this@DrawCanvas.height / 2f

                    for (point in plist.points) {
                        val relativeX = point.x - centerX
                        val relativeY = point.y - centerY
                        val scaledX = relativeX / state.zoomScale
                        val scaledY = relativeY / state.zoomScale
                        val adjustedX = scaledX + centerX
                        val adjustedY = scaledY + centerY

                        val adjustedPoint = com.onyx.android.sdk.data.note.TouchPoint(
                            adjustedX,
                            adjustedY,
                            point.pressure,
                            point.size,
                            point.tiltX,
                            point.tiltY,
                            point.timestamp
                        )
                        this.add(adjustedPoint)
                    }
                }
            } else {
                // When not zoomed, use the original points
                plist
            }

            // Now use the adjusted touch points for drawing operations
            if (getActualState().mode == Mode.Draw) {
                println("DEBUG: Processing touch for DRAW mode")
                coroutineScope.launch(Dispatchers.Main.immediate) {
                    if (lockDrawingWithTimeout()) {
                        try {
                            handleDraw(
                                this@DrawCanvas.page,
                                strokeHistoryBatch,
                                getActualState().penSettings[getActualState().pen.penName]!!.strokeSize,
                                getActualState().penSettings[getActualState().pen.penName]!!.color,
                                getActualState().pen,
                                adjustedPoints.points
                            )
                            println("DEBUG: handleDraw completed")
                        } finally {
                            drawingInProgress.unlock()
                        }
                    } else {
                        println("DEBUG: Could not acquire drawing lock, skipping stroke")
                    }
                }
            } else thread {
                if (getActualState().mode == Mode.Erase) {
                    println("DEBUG: Processing touch for ERASE mode")
                    // For eraser, also adjust touch points for zoom
                    val adjustedErasePoints = if (state.zoomScale != 1.0f) {
                        val centerX = this@DrawCanvas.width / 2f
                        val centerY = this@DrawCanvas.height / 2f

                        adjustedPoints.points.map { point ->
                            SimplePointF(point.x, point.y + page.scroll)
                        }
                    } else {
                        plist.points.map { SimplePointF(it.x, it.y + page.scroll) }
                    }

                    handleErase(
                        this@DrawCanvas.page,
                        adjustedErasePoints,
                        eraser = getActualState().eraser
                    )
                    drawCanvasToView()
                    refreshUi()
                }
            }
        }

        override fun onBeginRawErasing(p0: Boolean, p1: com.onyx.android.sdk.data.note.TouchPoint?) {}
        override fun onEndRawErasing(p0: Boolean, p1: com.onyx.android.sdk.data.note.TouchPoint?) {}

        override fun onRawErasingTouchPointListReceived(plist: TouchPointList?) {
            if (plist == null) return

            // Skip erasing if settings dialog is open
            if (getActualState().isSettingsDialogOpen) {
                return
            }

            // Apply the same coordinate transformation for eraser that we do for pen
            val adjustedPoints = if (state.zoomScale != 1.0f) {
                val centerX = page.viewWidth / 2f
                val centerY = page.viewHeight / 2f

                plist.points.map { point ->
                    val relativeX = point.x - centerX
                    val relativeY = point.y - centerY
                    val scaledX = relativeX / state.zoomScale
                    val scaledY = relativeY / state.zoomScale
                    val adjustedX = scaledX + centerX
                    val adjustedY = scaledY + centerY
                    SimplePointF(adjustedX, adjustedY + page.scroll)
                }
            } else {
                plist.points.map { SimplePointF(it.x, it.y + page.scroll) }
            }

            handleErase(
                this@DrawCanvas.page,
                adjustedPoints,
                eraser = getActualState().eraser
            )
            drawCanvasToView()
            refreshUi()
        }

        override fun onRawErasingTouchPointMoveReceived(p0: com.onyx.android.sdk.data.note.TouchPoint?) {}
        override fun onPenUpRefresh(refreshRect: RectF?) {
            super.onPenUpRefresh(refreshRect)
        }
        override fun onPenActive(point: com.onyx.android.sdk.data.note.TouchPoint?) {
            super.onPenActive(point)
        }
    }

    private val touchHelper by lazy {
        referencedSurfaceView = this.hashCode().toString()
        TouchHelper.create(this, inputCallback)
    }

    /**
     * Override the onTouchEvent method to better handle stylus vs finger input
     * This helps ensure that the gesture detector only processes finger events
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // First determine if this is a stylus event
        val isStylusInput = isStylusEvent(event)

        // If it's a stylus event, handle normally without passing to gesture detector
        if (isStylusInput) {
            // Let the parent class handle it for drawing
            return super.onTouchEvent(event)
        }

        // Only process finger gestures when the settings dialog is not open
        if (!state.isSettingsDialogOpen) {
            // If not a stylus, let the gesture detector process it first
            if (::gestureDetector.isInitialized) {
                val gestureHandled = gestureDetector.onTouchEvent(event)
                if (gestureHandled) {
                    return true
                }
            }
        }

        // If the gesture was not handled or gesture detector not initialized,
        // pass it to the parent
        return super.onTouchEvent(event)
    }

    /**
     * Determine if an event is from a stylus
     * Uses both Android standard detection and Onyx-specific logic
     */
    private fun isStylusEvent(event: MotionEvent): Boolean {
        // Check standard Android stylus detection
        for (i in 0 until event.pointerCount) {
            if (event.getToolType(i) == MotionEvent.TOOL_TYPE_STYLUS) {
                return true
            }
        }

        // For Onyx devices, we can use a heuristic based on the drawing state
        // If we're currently in drawing mode with the pen, and raw drawing is enabled
        // (which we can check via drawingInProgress state), consider it a stylus event
        if (state.isDrawing && state.mode == Mode.Draw) {
            if (drawingInProgress.isLocked) {
                // If drawing is actively in progress, it's likely the stylus
                return true
            }
        }

        return false
    }


    fun init() {
        println("Initializing Canvas")

        val surfaceCallback: SurfaceHolder.Callback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                println("Surface created $holder")
                updateActiveSurface()
                coroutineScope.launch {
                    forceUpdate.emit(null)
                }
            }

            override fun surfaceChanged(
                holder: SurfaceHolder, format: Int, width: Int, height: Int
            ) {
                println("Surface changed $holder")
                drawCanvasToView()
                updatePenAndStroke()
                refreshUi()
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                println("Surface destroyed ${this@DrawCanvas.hashCode()}")
                holder.removeCallback(this)
                if (referencedSurfaceView == this@DrawCanvas.hashCode().toString()) {
                    touchHelper.closeRawDrawing()
                }
            }
        }
        this.holder.addCallback(surfaceCallback)
    }

    /**
     * Initialize the gesture detector for handling drawing surface gestures.
     */
    fun initGestureDetector() {
        gestureDetector = DrawingGestureDetector(
            context,
            coroutineScope
        ) { message ->
            // This callback will be invoked when a gesture is detected
            coroutineScope.launch {
                // Show notification for the detected gesture
                showNotification(message)

                // Handle the gesture by performing the associated action
                handleGesture(message)
            }
        }
    }

    // Helper method to show notifications
    private fun showNotification(message: String) {
        coroutineScope.launch {
            com.wyldsoft.notes.classes.SnackState.globalSnackFlow.emit(
                com.wyldsoft.notes.classes.SnackConf(
                    text = message,
                    duration = 2000
                )
            )
        }
    }

    fun registerObservers() {
        // observe forceUpdate
        coroutineScope.launch {
            forceUpdate.collect { zoneAffected ->
                println("Force update zone $zoneAffected")

                if (zoneAffected != null) page.drawArea(
                    area = Rect(
                        zoneAffected.left,
                        zoneAffected.top - page.scroll,
                        zoneAffected.right,
                        zoneAffected.bottom - page.scroll
                    ),
                )
                refreshUiSuspend()
            }
        }

        // dont draw if settings is open
        coroutineScope.launch {
            snapshotFlow { state.isSettingsDialogOpen }.collect {
                println("Settings dialog state change: ${state.isSettingsDialogOpen}")
                updateActiveSurface()
            }
        }

        // observe refreshUi
        coroutineScope.launch {
            refreshUi.collect {
                println("Refreshing UI!")
                refreshUiSuspend()
            }
        }

        coroutineScope.launch {
            isDrawing.collect {
                println("Drawing state changed!")
                state.isDrawing = it
            }
        }

        // observe restartcount
        coroutineScope.launch {
            restartAfterConfChange.collect {
                println("Configuration changed!")
                init()
                drawCanvasToView()
            }
        }

        // observe pen and stroke size
        coroutineScope.launch {
            snapshotFlow { state.pen }.drop(1).collect {
                println("Pen change: ${state.pen}")
                updatePenAndStroke()
                refreshUiSuspend()
            }
        }

        coroutineScope.launch {
            snapshotFlow { state.penSettings.toMap() }.drop(1).collect {
                println("Pen settings change: ${state.penSettings}")
                updatePenAndStroke()
                refreshUiSuspend()
            }
        }

        coroutineScope.launch {
            snapshotFlow { state.eraser }.drop(1).collect {
                println("Eraser change: ${state.eraser}")
                updatePenAndStroke()
                refreshUiSuspend()
            }
        }

        // observe is drawing
        coroutineScope.launch {
            snapshotFlow { state.isDrawing }.drop(1).collect {
                println("isDrawing change: ${state.isDrawing}")
                updateIsDrawing()
            }
        }

        // observe toolbar open
        coroutineScope.launch {
            snapshotFlow { state.isToolbarOpen }.drop(1).collect {
                println("isToolbarOpen change: ${state.isToolbarOpen}")
                updateActiveSurface()
            }
        }

        // observe mode
        coroutineScope.launch {
            snapshotFlow { getActualState().mode }.drop(1).collect {
                println("Mode change: ${getActualState().mode}")
                updatePenAndStroke()
                refreshUiSuspend()
            }
        }
    }

    private fun refreshUi() {
        if (!state.isDrawing) {
            println("Not in drawing mode, skipping refreshUI")
            return
        }

        // Check if we're actively drawing before refreshing UI
        if (drawingInProgress.isLocked) {
            println("Drawing is in progress, deferring UI refresh")
            return
        }

        drawCanvasToView()

        // Reset screen freeze
        touchHelper.setRawDrawingEnabled(false)
        touchHelper.setRawDrawingEnabled(true)
    }

    suspend fun refreshUiSuspend() {
        if (!state.isDrawing) {
            waitForDrawing()
            drawCanvasToView()
            println("Not in drawing mode -- refreshUi ")
            return
        }

        if (Looper.getMainLooper().isCurrentThread) {
            println("refreshUiSuspend() is called from the main thread, it might not be a good idea.")
        }

        waitForDrawing()
        drawCanvasToView()
        touchHelper.setRawDrawingEnabled(false)

        if (drawingInProgress.isLocked)
            println("Lock was acquired during refreshing UI. It might cause errors.")

        touchHelper.setRawDrawingEnabled(true)
    }

    private suspend fun waitForDrawing() {
        withTimeoutOrNull(3000) {
            // Just to make sure wait 1ms before checking lock.
            delay(1)
            // Wait until drawingInProgress is unlocked before proceeding
            while (drawingInProgress.isLocked) {
                delay(5)
            }
        } ?: println("Timeout while waiting for drawing lock. Potential deadlock.")
    }

    fun drawCanvasToView() {
        val canvas = this.holder.lockCanvas() ?: return

        // Clear the canvas
        canvas.drawColor(Color.WHITE)

        // draw template
        if (state.pageTemplate != PageTemplate.BLANK) {
            // This draws the template directly
            when (state.pageTemplate) {
                PageTemplate.BLANK -> {
                    // Do nothing for blank template
                }
                PageTemplate.RULED -> {
                    // Draw ruled lines
                    val paint = Paint().apply {
                        color = Color.parseColor("#2986cc") // Light gray
                        strokeWidth = 1f
                        style = Paint.Style.STROKE
                    }

                    // Draw horizontal lines every 30 pixels
                    val lineSpacing = 30
                    var y = lineSpacing
                    while (y < page.height) {
                        canvas.drawLine(0f, y.toFloat(), page.width.toFloat(), y.toFloat(), paint)
                        y += lineSpacing
                    }
                }
            }
        }

        // Calculate visible area based on zoom level
        val visibleRect = if (state.zoomScale != 1.0f) {
            val centerX = page.viewWidth / 2f
            val centerY = page.viewHeight / 2f
            val visibleWidth = page.viewWidth / state.zoomScale
            val visibleHeight = page.viewHeight / state.zoomScale
            val left = centerX - (visibleWidth / 2f)
            val top = centerY - (visibleHeight / 2f) + page.scroll
            RectF(left, top, left + visibleWidth, top + visibleHeight)
        } else {
            RectF(0f, page.scroll.toFloat(), page.viewWidth.toFloat(), (page.scroll + page.viewHeight).toFloat())
        }

        // Apply zoom transformation
        if (state.zoomScale != 1.0f) {
            canvas.save()
            val centerX = page.viewWidth / 2f
            val centerY = page.viewHeight / 2f
            canvas.translate(centerX, centerY)
            canvas.scale(state.zoomScale, state.zoomScale)
            canvas.translate(-centerX, -centerY)
        }

        // Draw strokes
        for (stroke in page.strokes) {
            val strokeBounds = RectF(
                stroke.left, stroke.top, stroke.right, stroke.bottom
            )

            // Check if stroke is in visible area before drawing
            if (strokeBounds.intersect(visibleRect)) {
                page.drawStroke(canvas, stroke, IntOffset(0, -page.scroll))
            }
        }

        // Restore canvas state if zoom was applied
        if (state.zoomScale != 1.0f) {
            canvas.restore()

            // Show zoom indicator in top-right corner
            val zoomText = "${(state.zoomScale * 100).toInt()}%"
            val textPaint = Paint().apply {
                color = Color.BLACK
                textSize = 28f
                textAlign = Paint.Align.RIGHT
            }

            // Draw zoom indicator background
            val textX = canvas.width - 20f
            val textY = 40f
            val textWidth = textPaint.measureText(zoomText)
            val textBgPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }

            canvas.drawRect(
                textX - textWidth - 10,
                textY - 30,
                textX + 10,
                textY + 10,
                textBgPaint
            )

            // Draw zoom text
            canvas.drawText(zoomText, textX, textY, textPaint)
        }

        // Finish rendering
        this.holder.unlockCanvasAndPost(canvas)
    }

    private suspend fun updateIsDrawing() {
        println("Update is drawing: ${state.isDrawing}")
        if (state.isDrawing) {
            touchHelper.setRawDrawingEnabled(true)
        } else {
            // Check if drawing is completed
            waitForDrawing()
            // Draw to view, before showing drawing, avoid stutter
            drawCanvasToView()
            touchHelper.setRawDrawingEnabled(false)
        }
    }

    fun updatePenAndStroke() {
        println("DEBUG: Update pen and stroke")
        val state = getActualState()
        try {
            when (state.mode) {
                Mode.Draw -> {
                    println("DEBUG: Mode is DRAW, pen=${state.pen.penName}")
                    val strokeStyle = penToStroke(state.pen)
                    val strokeWidth = state.penSettings[state.pen.penName]!!.strokeSize
                    val strokeColor = state.penSettings[state.pen.penName]!!.color

                    println("DEBUG: Setting stroke style=$strokeStyle, width=$strokeWidth, color=$strokeColor")
                    touchHelper.setStrokeStyle(strokeStyle)
                        ?.setStrokeWidth(strokeWidth)
                        ?.setStrokeColor(strokeColor)
                }
                Mode.Erase -> {
                    println("DEBUG: Mode is ERASE, eraser=${state.eraser}")
                    when (state.eraser) {
                        Eraser.PEN -> {
                            touchHelper.setStrokeStyle(penToStroke(Pen.MARKER))
                                ?.setStrokeWidth(30f)
                                ?.setStrokeColor(Color.GRAY)
                        }
                        Eraser.SELECT -> {
                            touchHelper.setStrokeStyle(penToStroke(Pen.BALLPEN))
                                ?.setStrokeWidth(3f)
                                ?.setStrokeColor(Color.GRAY)
                        }
                    }
                }
            }
            println("DEBUG: updatePenAndStroke completed successfully")
        } catch (e: Exception) {
            println("DEBUG ERROR: Error in updatePenAndStroke: ${e.message}")
            e.printStackTrace()
        }
    }

    fun updateActiveSurface() {
        println("Update editable surface")
        touchHelper.setRawDrawingEnabled(false)
        touchHelper.closeRawDrawing()

        if (state.isSettingsDialogOpen) {
            touchHelper.setRawDrawingEnabled(false)
            return
        }

        // Set exclude rect for toolbar
        val exclusionHeight =
            if (state.isToolbarOpen) convertDpToPixel(40.dp, context).toInt() else 0

        val toolbarExcludeRect = Rect(0, 0, page.viewWidth, exclusionHeight)

        touchHelper.setLimitRect(
            mutableListOf(
                Rect(
                    0, 0, page.viewWidth, page.viewHeight
                )
            )
        ).setExcludeRect(listOf(toolbarExcludeRect))
            .openRawDrawing()

        touchHelper.setRawDrawingEnabled(true)
        updatePenAndStroke()

        refreshUi()
    }

    private fun penToStroke(pen: Pen): Int {
        println("DEBUG: Converting pen ${pen.penName} to stroke style")
        val result = when (pen) {
            Pen.BALLPEN -> com.onyx.android.sdk.pen.style.StrokeStyle.PENCIL
            Pen.MARKER -> com.onyx.android.sdk.pen.style.StrokeStyle.MARKER
            Pen.FOUNTAIN -> com.onyx.android.sdk.pen.style.StrokeStyle.FOUNTAIN
        }
        println("DEBUG: Pen ${pen.penName} converted to stroke style $result")
        return result
    }



    // Add this to the DrawCanvas class to handle navigation
    fun handleNavigation(action: GestureAction) {
        when (action) {
            GestureAction.NEXT_PAGE -> {
                // Implementation for navigating to the next page
                coroutineScope.launch {
                    // This will be caught by the SnackState observer in EditorView
                    com.wyldsoft.notes.classes.SnackState.globalSnackFlow.emit(
                        com.wyldsoft.notes.classes.SnackConf(
                            text = "navigate:next_page",
                            duration = 100 // Short duration as this is just for signaling
                        )
                    )
                }
            }
            GestureAction.PREVIOUS_PAGE -> {
                // Implementation for navigating to the previous page
                coroutineScope.launch {
                    // This will be caught by the SnackState observer in EditorView
                    com.wyldsoft.notes.classes.SnackState.globalSnackFlow.emit(
                        com.wyldsoft.notes.classes.SnackConf(
                            text = "navigate:previous_page",
                            duration = 100 // Short duration as this is just for signaling
                        )
                    )
                }
            }
            else -> {
                // Handle other actions in the performAction method
                performAction(action)
            }
        }
    }

    // Modify the handleGesture method to use handleNavigation for navigation actions
    fun handleGesture(gesture: String) {
        println("Detected gesture: $gesture")
        val context = this.context
        val gestureSettingsManager = GestureSettingsManager(context)

        // Map the gesture string to a GestureType
        val gestureType = when (gesture) {
            "Double tap detected" -> GestureType.SINGLE_FINGER_DOUBLE_TAP
            "Two-finger double tap detected" -> GestureType.TWO_FINGER_DOUBLE_TAP
            "Three-finger double tap detected" -> GestureType.THREE_FINGER_DOUBLE_TAP
            "Four-finger double tap detected" -> GestureType.FOUR_FINGER_DOUBLE_TAP
            "Swipe up detected" -> GestureType.SWIPE_UP
            "Swipe down detected" -> GestureType.SWIPE_DOWN
            "Swipe left detected" -> GestureType.SWIPE_LEFT
            "Swipe right detected" -> GestureType.SWIPE_RIGHT
            else -> null
        }

        // If the gesture is recognized, get and perform the associated action
        if (gestureType != null) {
            val action = gestureSettingsManager.getActionForGesture(gestureType)

            // Handle navigation actions separately
            if (action == GestureAction.NEXT_PAGE || action == GestureAction.PREVIOUS_PAGE) {
                handleNavigation(action)
            } else {
                performAction(action)
            }
        }
    }

    /**
     * Perform the specified action.
     *
     * @param action The action to perform
     */
    private fun performAction(action: GestureAction) {
        coroutineScope.launch {
            when (action) {
                GestureAction.UNDO -> {
                    page.handleUndo(state)
                    refreshUi.emit(Unit)
                }
                GestureAction.REDO -> {
                    page.handleRedo(state)
                    refreshUi.emit(Unit)
                }
                GestureAction.TOGGLE_TOOLBAR -> {
                    state.isToolbarOpen = !state.isToolbarOpen
                    updateActiveSurface()
                }
                GestureAction.CLEAR_PAGE -> {
                    // Save the current strokes to undo stack before clearing
                    if (page.strokes.isNotEmpty()) {
                        state.addToUndoStack(page.strokes)
                        // Clear all strokes
                        page.removeStrokes(page.strokes.map { it.id })
                        // Redraw the page
                        page.drawArea(android.graphics.Rect(0, 0, page.viewWidth, page.viewHeight))
                        refreshUi.emit(Unit)
                    }
                }
                GestureAction.NEXT_PAGE -> {
                    // This would require navigation capability
                    showNotification("Next page action - Implement in EditorView")
                }
                GestureAction.PREVIOUS_PAGE -> {
                    // This would require navigation capability
                    showNotification("Previous page action - Implement in EditorView")
                }
                GestureAction.CHANGE_PEN_COLOR -> {
                    // Cycle through some common pen colors
                    val currentColor = state.penSettings[state.pen.penName]?.color ?: android.graphics.Color.BLACK
                    val newColor = when (currentColor) {
                        android.graphics.Color.BLACK -> android.graphics.Color.BLUE
                        android.graphics.Color.BLUE -> android.graphics.Color.RED
                        android.graphics.Color.RED -> android.graphics.Color.GREEN
                        android.graphics.Color.GREEN -> android.graphics.Color.BLACK
                        else -> android.graphics.Color.BLACK
                    }

                    // Update the pen color
                    val settings = state.penSettings.toMutableMap()
                    settings[state.pen.penName] = settings[state.pen.penName]!!.copy(color = newColor)
                    state.penSettings = settings

                    // Show notification about color change
                    val colorName = when (newColor) {
                        android.graphics.Color.BLACK -> "Black"
                        android.graphics.Color.BLUE -> "Blue"
                        android.graphics.Color.RED -> "Red"
                        android.graphics.Color.GREEN -> "Green"
                        else -> "Unknown"
                    }
                    showNotification("Pen color changed to $colorName")
                }
                GestureAction.TOGGLE_ERASER -> {
                    // Toggle between drawing and erasing
                    state.mode = if (state.mode == Mode.Draw) Mode.Erase else Mode.Draw
                    updatePenAndStroke()
                    refreshUi.emit(Unit)

                    showNotification(if (state.mode == Mode.Erase) "Eraser enabled" else "Pen enabled")
                }
                GestureAction.NONE -> {
                    // Do nothing
                }
            }
        }
    }

    private fun handleDraw(
        page: PageView,
        historyBucket: MutableList<String>,
        strokeSize: Float,
        color: Int,
        pen: Pen,
        touchPoints: List<com.onyx.android.sdk.data.note.TouchPoint>
    ) {
        try {
            println("DEBUG: handleDraw called with ${touchPoints.size} points")
            if (touchPoints.isEmpty()) {
                println("DEBUG: No touch points to process")
                return
            }

            // Create the bounding box once
            val initialPoint = touchPoints[0]
            val boundingBox = RectF(
                initialPoint.x,
                initialPoint.y + page.scroll,
                initialPoint.x,
                initialPoint.y + page.scroll
            )

            // Process all points at once
            val points = touchPoints.map {
                boundingBox.union(it.x, it.y + page.scroll)
                StrokePoint(
                    x = it.x,
                    y = it.y + page.scroll,
                    pressure = it.pressure,
                    size = it.size,
                    tiltX = it.tiltX,
                    tiltY = it.tiltY,
                    timestamp = it.timestamp,
                )
            }

            // Only apply the inset once after all points are processed
            boundingBox.inset(-strokeSize, -strokeSize)

            // Create stroke with all points
            val stroke = Stroke(
                size = strokeSize,
                pen = pen,
                pageId = page.id,
                top = boundingBox.top,
                bottom = boundingBox.bottom,
                left = boundingBox.left,
                right = boundingBox.right,
                points = points,
                color = color
            )
            state.addToUndoStack(listOf(stroke))
            page.addStrokes(listOf(stroke))

            // Draw the stroke on the page all at once
            val rect = Rect(
                boundingBox.left.toInt(),
                boundingBox.top.toInt() - page.scroll,
                boundingBox.right.toInt(),
                boundingBox.bottom.toInt() - page.scroll
            )

            page.drawArea(rect)
            historyBucket.add(stroke.id)

            // Only refresh UI once per stroke
            drawCanvasToView()

            // Avoid refreshing UI during active drawing
            if (!drawingInProgress.isLocked) {
                refreshUi()
            }
        } catch (e: Exception) {
            println("DEBUG ERROR: Handle Draw: ${e.message}")
            e.printStackTrace()
        }
    }

    // Add a timeout to the mutex to prevent deadlocks
    suspend fun lockDrawingWithTimeout(): Boolean {
        return withTimeoutOrNull(500) {
            drawingInProgress.tryLock(500)
            true
        } ?: false
    }

    private fun handleErase(
        page: PageView,
        points: List<SimplePointF>,
        eraser: Eraser
    ) {
        val path = android.graphics.Path()
        if (points.isEmpty()) return

        path.moveTo(points[0].x, points[0].y)

        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
        }

        var outPath = android.graphics.Path()

        if (eraser == Eraser.SELECT) {
            path.close()
            outPath = path
        } else {
            val paint = Paint().apply {
                this.strokeWidth = 30f
                this.style = Paint.Style.STROKE
                this.strokeCap = Paint.Cap.ROUND
                this.strokeJoin = Paint.Join.ROUND
                this.isAntiAlias = true
            }
            paint.getFillPath(path, outPath)
        }

        val deletedStrokes = selectStrokesFromPath(page.strokes, outPath)
        val deletedStrokeIds = deletedStrokes.map { it.id }
        page.removeStrokes(deletedStrokeIds)

        page.drawArea(
            area = Rect(
                getStrokeBounds(deletedStrokes).left.toInt(),
                getStrokeBounds(deletedStrokes).top.toInt() - page.scroll,
                getStrokeBounds(deletedStrokes).right.toInt(),
                getStrokeBounds(deletedStrokes).bottom.toInt() - page.scroll
            )
        )
    }

    private fun selectStrokesFromPath(strokes: List<Stroke>, path: android.graphics.Path): List<Stroke> {
        val bounds = RectF()
        path.computeBounds(bounds, true)

        // Create region from path
        val region = android.graphics.Region()
        region.setPath(
            path,
            android.graphics.Region(
                bounds.left.toInt(),
                bounds.top.toInt(),
                bounds.right.toInt(),
                bounds.bottom.toInt()
            )
        )

        return strokes.filter {
            val strokeBounds = RectF(it.left, it.top, it.right, it.bottom)
            strokeBounds.intersect(bounds)
        }.filter {
            it.points.any { point ->
                region.contains(point.x.toInt(), point.y.toInt())
            }
        }
    }

    private fun getStrokeBounds(strokes: List<Stroke>): RectF {
        if (strokes.isEmpty()) return RectF()

        val result = RectF(
            strokes[0].left,
            strokes[0].top,
            strokes[0].right,
            strokes[0].bottom
        )

        for (stroke in strokes) {
            result.union(stroke.left, stroke.top)
            result.union(stroke.right, stroke.bottom)
        }

        return result
    }
}