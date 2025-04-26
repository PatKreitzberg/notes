package com.wyldsoft.notes.classes

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.snapshotFlow
import com.wyldsoft.notes.classes.drawing.CanvasRenderer
import com.wyldsoft.notes.classes.drawing.DrawingManager
import com.wyldsoft.notes.classes.drawing.TouchEventHandler
import com.wyldsoft.notes.utils.EditorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The main canvas component that coordinates all drawing operations.
 * Acts as a façade for the underlying drawing system components.
 */
class DrawCanvas(
    context: Context,
    val coroutineScope: CoroutineScope,
    val state: EditorState,
    val page: PageView
) : SurfaceView(context) {

    private val canvasRenderer = CanvasRenderer(this, page)
    private val drawingManager = DrawingManager(page)
    private val touchEventHandler = TouchEventHandler(
        context,
        this,
        coroutineScope,
        state,
        drawingManager,
        canvasRenderer
    )

    fun init() {
        println("Initializing Canvas")

        val surfaceCallback: SurfaceHolder.Callback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                println("Surface created $holder")
                touchEventHandler.updateActiveSurface()
                coroutineScope.launch {
                    DrawingManager.forceUpdate.emit(null)
                }
            }

            override fun surfaceChanged(
                holder: SurfaceHolder, format: Int, width: Int, height: Int
            ) {
                println("Surface changed $holder")
                drawCanvasToView()
                touchEventHandler.updatePenAndStroke()
                refreshUi()
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                println("Surface destroyed ${this@DrawCanvas.hashCode()}")
                holder.removeCallback(this)
                touchEventHandler.closeRawDrawing()
            }
        }
        this.holder.addCallback(surfaceCallback)
    }

    fun registerObservers() {
        // observe forceUpdate
        coroutineScope.launch {
            DrawingManager.forceUpdate.collect { zoneAffected ->
                println("Force update zone $zoneAffected")

                if (zoneAffected != null) page.drawArea(
                    area = android.graphics.Rect(
                        zoneAffected.left,
                        zoneAffected.top,
                        zoneAffected.right,
                        zoneAffected.bottom
                    ),
                )
                refreshUiSuspend()
            }
        }

        // observe refreshUi
        coroutineScope.launch {
            DrawingManager.refreshUi.collect {
                println("Refreshing UI!")
                refreshUiSuspend()
            }
        }

        coroutineScope.launch {
            DrawingManager.isDrawing.collect {
                println("Drawing state changed!")
                state.isDrawing = it
            }
        }

        // observe restartcount
        coroutineScope.launch {
            DrawingManager.restartAfterConfChange.collect {
                println("Configuration changed!")
                init()
                drawCanvasToView()
            }
        }

        // observe pen and stroke size
        coroutineScope.launch {
            snapshotFlow { state.pen }.drop(1).collect {
                println("Pen change: ${state.pen}")
                touchEventHandler.updatePenAndStroke()
                refreshUiSuspend()
            }
        }

        coroutineScope.launch {
            snapshotFlow { state.penSettings.toMap() }.drop(1).collect {
                println("Pen settings change: ${state.penSettings}")
                touchEventHandler.updatePenAndStroke()
                refreshUiSuspend()
            }
        }

        coroutineScope.launch {
            snapshotFlow { state.eraser }.drop(1).collect {
                println("Eraser change: ${state.eraser}")
                touchEventHandler.updatePenAndStroke()
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
                touchEventHandler.updateActiveSurface()
            }
        }

        // observe mode
        coroutineScope.launch {
            snapshotFlow { state.mode }.drop(1).collect {
                println("Mode change: ${state.mode}")
                touchEventHandler.updatePenAndStroke()
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
        if (DrawingManager.drawingInProgress.isLocked) {
            println("Drawing is in progress, deferring UI refresh")
            return
        }

        drawCanvasToView()

        // Reset screen freeze
        touchEventHandler.setRawDrawingEnabled(false)
        touchEventHandler.setRawDrawingEnabled(true)
    }

    suspend fun refreshUiSuspend() {
        if (!state.isDrawing) {
            waitForDrawing()
            drawCanvasToView()
            println("Not in drawing mode -- refreshUi ")
            return
        }

        if (android.os.Looper.getMainLooper().isCurrentThread) {
            println("refreshUiSuspend() is called from the main thread, it might not be a good idea.")
        }

        waitForDrawing()
        drawCanvasToView()
        touchEventHandler.setRawDrawingEnabled(false)

        if (DrawingManager.drawingInProgress.isLocked)
            println("Lock was acquired during refreshing UI. It might cause errors.")

        touchEventHandler.setRawDrawingEnabled(true)
    }

    private suspend fun waitForDrawing() {
        withTimeoutOrNull(3000) {
            // Just to make sure wait 1ms before checking lock.
            delay(1)
            // Wait until drawingInProgress is unlocked before proceeding
            while (DrawingManager.drawingInProgress.isLocked) {
                delay(5)
            }
        } ?: println("Timeout while waiting for drawing lock. Potential deadlock.")
    }

    fun drawCanvasToView() {
        canvasRenderer.drawCanvasToView()
    }

    private suspend fun updateIsDrawing() {
        println("Update is drawing: ${state.isDrawing}")
        if (state.isDrawing) {
            touchEventHandler.setRawDrawingEnabled(true)
        } else {
            // Check if drawing is completed
            waitForDrawing()
            // Draw to view, before showing drawing, avoid stutter
            drawCanvasToView()
            touchEventHandler.setRawDrawingEnabled(false)
        }
    }
}