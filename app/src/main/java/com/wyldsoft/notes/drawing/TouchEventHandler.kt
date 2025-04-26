package com.wyldsoft.notes.classes.drawing

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
import com.wyldsoft.notes.utils.SimplePointF
import com.wyldsoft.notes.utils.convertDpToPixel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.thread

/**
 * Handles touch events and delegates them to appropriate handlers.
 * Manages the Onyx TouchHelper and input callbacks.
 */
class TouchEventHandler(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val coroutineScope: CoroutineScope,
    private val state: EditorState,
    private val drawingManager: DrawingManager
) {
    // Get maximum pressure from the device
    private val pressure = EpdController.getMaxTouchPressure()
    private var referencedSurfaceView: String = ""

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
            if (state.mode == Mode.Draw) {
                println("DEBUG: Processing touch for DRAW mode")
                coroutineScope.launch(Dispatchers.Main.immediate) {
                    if (lockDrawingWithTimeout()) {
                        try {
                            drawingManager.handleDraw(
                                state.penSettings[state.pen.penName]!!.strokeSize,
                                state.penSettings[state.pen.penName]!!.color,
                                state.pen,
                                points.points
                            )
                            println("DEBUG: handleDraw completed")
                        } finally {
                            DrawingManager.drawingInProgress.unlock()
                        }
                    } else {
                        println("DEBUG: Could not acquire drawing lock, skipping stroke")
                    }
                }
            } else thread {
                if (state.mode == Mode.Erase) {
                    println("DEBUG: Processing touch for ERASE mode")

                    val erasePoints = plist.points.map { SimplePointF(it.x, it.y) }
                    drawingManager.handleErase(
                        erasePoints,
                        eraser = state.eraser
                    )
                    onDrawingCompleted()
                }
            }
        }

        override fun onBeginRawErasing(p0: Boolean, p1: com.onyx.android.sdk.data.note.TouchPoint?) {}
        override fun onEndRawErasing(p0: Boolean, p1: com.onyx.android.sdk.data.note.TouchPoint?) {}

        override fun onRawErasingTouchPointListReceived(plist: TouchPointList?) {
            if (plist == null) return
            val points = plist.points.map { SimplePointF(it.x, it.y) }
            drawingManager.handleErase(
                points,
                eraser = state.eraser
            )
            onDrawingCompleted()
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
        referencedSurfaceView = surfaceView.hashCode().toString()
        TouchHelper.create(surfaceView, inputCallback)
    }

    private fun onDrawingCompleted() {
        // Let the canvas know to refresh the view
        coroutineScope.launch {
            DrawingManager.refreshUi.emit(Unit)
        }
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

    fun openRawDrawing() {
        touchHelper.openRawDrawing()
    }

    fun updateActiveSurface() {
        println("Update editable surface")
        val exclusionHeight = if (state.isToolbarOpen) convertDpToPixel(40.dp, context).toInt() else 0

        setRawDrawingEnabled(false)
        closeRawDrawing()

        // Set exclude rect for toolbar
        val toolbarExcludeRect = Rect(0, 0, surfaceView.width, exclusionHeight)

        touchHelper.setLimitRect(
            mutableListOf(
                Rect(
                    0, 0, surfaceView.width, surfaceView.height
                )
            )
        ).setExcludeRect(listOf(toolbarExcludeRect))
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
                    touchHelper.setStrokeStyle(strokeStyle)
                        ?.setStrokeWidth(strokeWidth)
                        ?.setStrokeColor(strokeColor)
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
            }
            println("DEBUG: updatePenAndStroke completed successfully")
        } catch (e: Exception) {
            println("DEBUG ERROR: Error in updatePenAndStroke: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun penToStroke(pen: com.wyldsoft.notes.utils.Pen): Int {
        println("DEBUG: Converting pen ${pen.penName} to stroke style")
        val result = when (pen) {
            com.wyldsoft.notes.utils.Pen.BALLPEN -> com.onyx.android.sdk.pen.style.StrokeStyle.PENCIL
            com.wyldsoft.notes.utils.Pen.MARKER -> com.onyx.android.sdk.pen.style.StrokeStyle.MARKER
            com.wyldsoft.notes.utils.Pen.FOUNTAIN -> com.onyx.android.sdk.pen.style.StrokeStyle.FOUNTAIN
        }
        println("DEBUG: Pen ${pen.penName} converted to stroke style $result")
        return result
    }
}