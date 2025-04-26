package com.wyldsoft.notes.classes

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
import com.wyldsoft.notes.utils.convertDpToPixel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.thread

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
            val startTime = System.currentTimeMillis()

            val points = plist

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
                                points.points
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

                    val erasePoints = plist.points.map { SimplePointF(it.x, it.y + page.scroll) }
                    handleErase(
                        this@DrawCanvas.page,
                        erasePoints,
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
            val points = plist.points.map { SimplePointF(it.x, it.y + page.scroll) }
            handleErase(
                this@DrawCanvas.page,
                points,
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

        // Draw strokes
        for (stroke in page.strokes) {
            page.drawStroke(canvas, stroke, IntOffset(0, -page.scroll))
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
        val exclusionHeight = if (state.isToolbarOpen) convertDpToPixel(40.dp, context).toInt() else 0

        touchHelper.setRawDrawingEnabled(false)
        touchHelper.closeRawDrawing()

        // Set exclude rect for toolbar
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