package com.wyldsoft.notes.strokeManagement

import android.graphics.Rect
import com.wyldsoft.notes.history.HistoryManager
import com.wyldsoft.notes.strokeManagement.interfaces.IStrokeManager
import com.wyldsoft.notes.utils.Pen
import com.wyldsoft.notes.utils.SimplePointF
import com.wyldsoft.notes.views.PageView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Main stroke management coordinator.
 * Uses composition to delegate to specialized components for different operations.
 */
class DrawingManager(
    private val page: PageView,
    private val historyManager: HistoryManager? = null
) : IStrokeManager {

    // Component dependencies
    private val strokeCreator = StrokeCreator(page.viewportTransformer)
    private val strokeEraser = StrokeEraser(page.viewportTransformer)
    private val historyOperations = HistoryOperations(page, historyManager)
    private val pageOperations = PageOperations(page, historyOperations)

    // Shared flow events for UI coordination
    companion object {
        val forceUpdate = MutableSharedFlow<Rect?>()
        val refreshUi = MutableSharedFlow<Unit>()
        val isDrawing = MutableSharedFlow<Boolean>()
        val restartAfterConfChange = MutableSharedFlow<Unit>()
        val drawingInProgress = Mutex()
        val isStrokeOptionsOpen = MutableSharedFlow<Boolean>()
        val strokeStyleChanged = MutableSharedFlow<Unit>()
        val undoRedoPerformed = MutableSharedFlow<Unit>()
    }

    private val strokeHistoryBatch = mutableListOf<String>()
    private val tag = "DrawingManager:"

    /**
     * Handles drawing operations with touch points
     */
    override fun handleDraw(
        strokeSize: Float,
        color: Int,
        pen: Pen,
        touchPoints: List<com.onyx.android.sdk.data.note.TouchPoint>
    ) {
        try {
            println("DEBUG: handleDraw called with ${touchPoints.size} points")
            
            val stroke = strokeCreator.createStroke(strokeSize, color, pen, page.id, touchPoints)
                ?: return

            // Add stroke to page
            val strokes = listOf(stroke)
            page.addStrokes(strokes)

            // Draw the stroke on the page
            val rect = Rect(
                stroke.left.toInt(),
                stroke.top.toInt(),
                stroke.right.toInt(),
                stroke.bottom.toInt()
            )

            page.drawArea(rect)
            strokeHistoryBatch.add(stroke.id)

            // Record in history for undo/redo
            historyOperations.recordStrokeAddition(strokes)

            // Force refresh
            GlobalScope.launch {
                refreshUi.emit(Unit)
            }
        } catch (e: Exception) {
            println("DEBUG ERROR: Handle Draw: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Handles erasing operations
     */
    override fun handleErase(
        points: List<SimplePointF>,
        eraser: com.wyldsoft.notes.utils.Eraser
    ) {
        println("erase: handleErase start")
        
        // Find strokes to delete
        val deletedStrokes = strokeEraser.findStrokesToErase(page.visibleStrokes, points, eraser)
        
        if (deletedStrokes.isEmpty()) return

        val deletedStrokeIds = deletedStrokes.map { it.id }

        // Record in history before removing
        historyOperations.recordStrokeDeletion(deletedStrokes)

        // Remove the strokes
        page.removeStrokes(deletedStrokeIds)

        // Redraw the affected area
        val bounds = strokeEraser.getStrokeBounds(deletedStrokes)
        page.drawArea(
            Rect(
                bounds.left.toInt(),
                bounds.top.toInt(),
                bounds.right.toInt(),
                bounds.bottom.toInt()
            )
        )
        
        println("erase: handleErase end")
    }

    /**
     * Performs undo operation
     */
    override fun undo(): Boolean {
        return historyOperations.undo()
    }

    /**
     * Performs redo operation
     */
    override fun redo(): Boolean {
        return historyOperations.redo()
    }

    /**
     * Inserts a new page at specified position
     */
    override fun insertPage(pageNumber: Int): Boolean {
        return pageOperations.insertPage(pageNumber)
    }

    /**
     * Gets stroke count on a specific page
     */
    fun getStrokeCountOnPage(pageNumber: Int): Int {
        return pageOperations.getStrokeCountOnPage(pageNumber)
    }

    /**
     * Gets all strokes on a specific page
     */
    fun getStrokesOnPage(pageNumber: Int): List<com.wyldsoft.notes.utils.Stroke> {
        return pageOperations.getStrokesOnPage(pageNumber)
    }

    /**
     * Gets the page number for a given Y coordinate
     */
    fun getPageNumberForY(y: Float): Int {
        return pageOperations.getPageNumberForY(y)
    }
}