package com.wyldsoft.notes.strokeManagement

import com.wyldsoft.notes.history.ActionType
import com.wyldsoft.notes.history.HistoryAction
import com.wyldsoft.notes.history.HistoryManager
import com.wyldsoft.notes.history.InsertPageActionData
import com.wyldsoft.notes.history.MoveActionData
import com.wyldsoft.notes.history.SerializableStroke
import com.wyldsoft.notes.history.StrokeActionData
import com.wyldsoft.notes.utils.Stroke
import com.wyldsoft.notes.views.PageView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Handles undo/redo operations for stroke management.
 * Manages history actions and their reversal.
 */
class HistoryOperations(
    private val page: PageView,
    private val historyManager: HistoryManager?
) {

    /**
     * Records stroke addition in history
     */
    fun recordStrokeAddition(strokes: List<Stroke>) {
        historyManager?.addAction(
            HistoryAction(
                type = ActionType.ADD_STROKES,
                data = StrokeActionData(
                    strokeIds = strokes.map { it.id },
                    strokes = strokes.map { SerializableStroke.fromStroke(it) }
                )
            )
        )
    }

    /**
     * Records stroke deletion in history
     */
    fun recordStrokeDeletion(strokes: List<Stroke>) {
        if (strokes.isNotEmpty()) {
            historyManager?.addAction(
                HistoryAction(
                    type = ActionType.DELETE_STROKES,
                    data = StrokeActionData(
                        strokeIds = strokes.map { it.id },
                        strokes = strokes.map { SerializableStroke.fromStroke(it) }
                    )
                )
            )
        }
    }

    /**
     * Records page insertion in history
     */
    fun recordPageInsertion(pageNumber: Int, affectedStrokeIds: List<String>, pageOffset: Float) {
        historyManager?.addAction(
            HistoryAction(
                type = ActionType.INSERT_PAGE,
                data = InsertPageActionData(
                    pageNumber = pageNumber,
                    affectedStrokeIds = affectedStrokeIds,
                    pageOffset = pageOffset
                )
            )
        )
    }

    /**
     * Performs an undo operation
     */
    fun undo(): Boolean {
        if (historyManager == null) return false

        val action = historyManager.undo() ?: return false

        when (action.type) {
            ActionType.ADD_STROKES -> undoStrokeAddition(action.data as StrokeActionData)
            ActionType.DELETE_STROKES -> undoStrokeDeletion(action.data as StrokeActionData)
            ActionType.MOVE_STROKES -> undoStrokeMove(action.data as MoveActionData)
            ActionType.INSERT_PAGE -> undoPageInsertion(action.data as InsertPageActionData)
        }

        notifyHistoryChange()
        return true
    }

    /**
     * Performs a redo operation
     */
    fun redo(): Boolean {
        if (historyManager == null) return false

        val action = historyManager.redo() ?: return false

        when (action.type) {
            ActionType.ADD_STROKES -> redoStrokeAddition(action.data as StrokeActionData)
            ActionType.DELETE_STROKES -> redoStrokeDeletion(action.data as StrokeActionData)
            ActionType.MOVE_STROKES -> redoStrokeMove(action.data as MoveActionData)
            ActionType.INSERT_PAGE -> redoPageInsertion(action.data as InsertPageActionData)
        }

        notifyHistoryChange()
        return true
    }

    /**
     * Undoes stroke addition by removing strokes
     */
    private fun undoStrokeAddition(data: StrokeActionData) {
        page.removeStrokes(data.strokeIds)
    }

    /**
     * Undoes stroke deletion by adding strokes back
     */
    private fun undoStrokeDeletion(data: StrokeActionData) {
        val strokes = data.strokes.map { it.toStroke() }
        page.addStrokes(strokes)
    }

    /**
     * Undoes stroke movement by restoring original positions
     */
    private fun undoStrokeMove(data: MoveActionData) {
        val originalStrokes = data.originalStrokes.map { it.toStroke() }
        page.removeStrokes(data.strokeIds)
        page.addStrokes(originalStrokes)
    }

    /**
     * Undoes page insertion by shifting strokes back up
     */
    private fun undoPageInsertion(data: InsertPageActionData) {
        val affectedStrokes = page.strokes.filter { it.id in data.affectedStrokeIds }

        // Move strokes back up
        for (stroke in affectedStrokes) {
            stroke.top -= data.pageOffset
            stroke.bottom -= data.pageOffset

            // Update points
            for (point in stroke.points) {
                point.y -= data.pageOffset
            }
        }

        // Update strokes in page
        if (affectedStrokes.isNotEmpty()) {
            page.removeStrokes(data.affectedStrokeIds)
            page.addStrokes(affectedStrokes)
        }

        // Update document height
        updateDocumentHeightAfterPageRemoval()
    }

    /**
     * Redoes stroke addition by adding strokes back
     */
    private fun redoStrokeAddition(data: StrokeActionData) {
        val strokes = data.strokes.map { it.toStroke() }
        page.addStrokes(strokes)
    }

    /**
     * Redoes stroke deletion by removing strokes again
     */
    private fun redoStrokeDeletion(data: StrokeActionData) {
        page.removeStrokes(data.strokeIds)
    }

    /**
     * Redoes stroke movement by applying move again
     */
    private fun redoStrokeMove(data: MoveActionData) {
        val movedStrokes = data.modifiedStrokes.map { it.toStroke() }
        page.removeStrokes(data.strokeIds)
        page.addStrokes(movedStrokes)
    }

    /**
     * Redoes page insertion by shifting strokes down again
     */
    private fun redoPageInsertion(data: InsertPageActionData) {
        val affectedStrokes = page.strokes.filter { it.id in data.affectedStrokeIds }

        // Move strokes down
        for (stroke in affectedStrokes) {
            stroke.top += data.pageOffset
            stroke.bottom += data.pageOffset

            // Update points
            for (point in stroke.points) {
                point.y += data.pageOffset
            }
        }

        // Update strokes in page
        if (affectedStrokes.isNotEmpty()) {
            page.removeStrokes(data.affectedStrokeIds)
            page.addStrokes(affectedStrokes)
        }

        // Update document height
        updateDocumentHeightAfterPageInsertion()
    }

    /**
     * Updates document height after page removal
     */
    private fun updateDocumentHeightAfterPageRemoval() {
        val paginationManager = page.viewportTransformer.getPaginationManager()
        val newMaxPageIndex = paginationManager.getTotalPageCount() - 2
        val newHeight = paginationManager.getPageBottomY(newMaxPageIndex.coerceAtLeast(0))
        page.height = newHeight.toInt()
        page.viewportTransformer.updateDocumentHeight(page.height)
    }

    /**
     * Updates document height after page insertion
     */
    private fun updateDocumentHeightAfterPageInsertion() {
        val paginationManager = page.viewportTransformer.getPaginationManager()
        val newMaxPageIndex = paginationManager.getTotalPageCount()
        val newHeight = paginationManager.getPageBottomY(newMaxPageIndex - 1)
        page.height = newHeight.toInt()
        page.viewportTransformer.updateDocumentHeight(page.height)
    }

    /**
     * Notifies about history change
     */
    private fun notifyHistoryChange() {
        GlobalScope.launch {
            com.wyldsoft.notes.strokeManagement.DrawingManager.undoRedoPerformed.emit(Unit)
            com.wyldsoft.notes.strokeManagement.DrawingManager.refreshUi.emit(Unit)
        }
    }
}