package com.wyldsoft.notes.selection

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import com.wyldsoft.notes.strokeManagement.DrawingManager
import com.wyldsoft.notes.history.ActionType
import com.wyldsoft.notes.utils.EditorState
import com.wyldsoft.notes.history.HistoryAction
import com.wyldsoft.notes.utils.PlacementMode
import com.wyldsoft.notes.utils.Mode
import com.wyldsoft.notes.history.MoveActionData
import com.wyldsoft.notes.history.SerializableStroke
import com.wyldsoft.notes.utils.SimplePointF
import com.wyldsoft.notes.views.PageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class SelectionHandler(
    private val context: Context,
    private val editorState: EditorState,
    private val page: PageView,
    private val coroutineScope: CoroutineScope
) {
    // Optimize paint objects - set them up once
    private val selectionPaint = Paint().apply {
        color = android.graphics.Color.BLUE
        strokeWidth = 2f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        isAntiAlias = true
    }

    private val highlightPaint = Paint().apply {
        color = android.graphics.Color.argb(50, 0, 0, 255) // Semi-transparent blue
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val borderPaint = Paint().apply {
        color = android.graphics.Color.BLUE
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    // Convenience property for viewport transformer
    private val viewportTransformer get() = page.viewportTransformer

    // Start drawing a selection path
    fun startSelectionDraw(x: Float, y: Float) {
        if (editorState.mode != Mode.Selection) return

        // Reset any existing selection
        editorState.selectionState.reset()

        // Transform to page coordinates
        val (pageX, pageY) = viewportTransformer.viewToPageCoordinates(x, y)

        // Start drawing a new selection
        editorState.selectionState.isDrawingSelection = true
        editorState.selectionState.selectionPoints.clear()
        editorState.selectionState.selectionPoints.add(SimplePointF(pageX, pageY))

        // Create a new path
        val path = Path()
        path.moveTo(pageX, pageY)
        editorState.selectionState.selectionPath = path

        // Force UI update - using existing DrawingManager
        refreshUi()
    }

    // Continue drawing the selection path
    fun updateSelectionDraw(x: Float, y: Float) {
        if (!editorState.selectionState.isDrawingSelection) return

        // Transform to page coordinates
        val (pageX, pageY) = viewportTransformer.viewToPageCoordinates(x, y)

        // Add to selection points
        editorState.selectionState.selectionPoints.add(SimplePointF(pageX, pageY))

        // Update the path
        val path = editorState.selectionState.selectionPath ?: Path()
        path.lineTo(pageX, pageY)
        editorState.selectionState.selectionPath = path

        // Periodic UI updates to avoid ghosting - every 5 points
        if (editorState.selectionState.selectionPoints.size % 5 == 0) {
            refreshUi()
        }
    }

    // Complete the selection drawing and select strokes
    fun completeSelectionDraw(x: Float, y: Float) {
        if (!editorState.selectionState.isDrawingSelection) return

        // Transform to page coordinates
        val (pageX, pageY) = viewportTransformer.viewToPageCoordinates(x, y)

        // Add final point and close the path
        editorState.selectionState.selectionPoints.add(SimplePointF(pageX, pageY))

        val path = editorState.selectionState.selectionPath ?: Path()
        path.lineTo(pageX, pageY)
        path.close()
        editorState.selectionState.selectionPath = path

        // Find strokes inside the selection - reuse existing method if available
        // This is similar to drawingManager.handleErase with some modifications
        selectStrokesInPath(path)

        // Mark selection as complete
        editorState.selectionState.isDrawingSelection = false

        // Calculate selection bounds
        if (editorState.selectionState.selectedStrokes?.isNotEmpty() == true) {
            calculateSelectionBounds()
        }

        // Force UI update
        refreshUi()
    }

    // Select strokes - leverage existing selectStrokesFromPath logic in DrawingManager
    private fun selectStrokesInPath(path: Path) {
        val bounds = RectF()
        path.computeBounds(bounds, true)

        // Create region from path - similar to DrawingManager.handleErase
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

        // Filter strokes that are completely inside the region
        // only need pages visibleStrokes
        val selectedStrokes = page.visibleStrokes.filter { stroke ->
            // Check if stroke bounds intersect with selection bounds
            val strokeBounds = RectF(stroke.left, stroke.top, stroke.right, stroke.bottom)

            if (!RectF.intersects(bounds, strokeBounds)) {
                return@filter false
            }

            // Check if all points of the stroke are inside the region
            stroke.points.all { point ->
                region.contains(point.x.toInt(), point.y.toInt())
            }
        }

        // Store selected strokes
        editorState.selectionState.selectedStrokes = selectedStrokes
    }

    // Calculate the bounds of the selection - similar to DrawingManager.getStrokeBounds
    private fun calculateSelectionBounds() {
        val strokes = editorState.selectionState.selectedStrokes ?: return
        if (strokes.isEmpty()) return

        val left = strokes.minOf { it.left }
        val top = strokes.minOf { it.top }
        val right = strokes.maxOf { it.right }
        val bottom = strokes.maxOf { it.bottom }

        editorState.selectionState.selectionBounds = RectF(left, top, right, bottom)
    }

    // Start moving the selection
    fun startMovingSelection(x: Float, y: Float) {
        if (editorState.mode != Mode.Selection) return
        if (editorState.selectionState.selectedStrokes == null) return

        // Transform to page coordinates
        val (pageX, pageY) = viewportTransformer.viewToPageCoordinates(x, y)

        // Check if the point is inside the selection bounds
        val bounds = editorState.selectionState.selectionBounds ?: return
        if (bounds.contains(pageX, pageY)) {
            // Inside bounds, start moving
            editorState.selectionState.isMovingSelection = true
            editorState.selectionState.moveStartPoint = SimplePointF(pageX, pageY)
            refreshUi()
        } else {
            // Outside bounds, deselect
            editorState.selectionState.reset()
            refreshUi()
            return
        }

        // Start moving
        editorState.selectionState.isMovingSelection = true
        editorState.selectionState.moveStartPoint = SimplePointF(pageX, pageY)

        refreshUi()
    }

    // Update the position of the selection during move
    // In SelectionHandler.kt, modify the updateMovingSelection method
    // Update the position of the selection during move
    fun updateMovingSelection(x: Float, y: Float) {
        if (!editorState.selectionState.isMovingSelection) return

        // Transform to page coordinates
        val (pageX, pageY) = viewportTransformer.viewToPageCoordinates(x, y)

        // Get the last move position
        val lastMovePoint = editorState.selectionState.moveStartPoint ?: return

        // Calculate the displacement from the last position
        val deltaX = pageX - lastMovePoint.x
        val deltaY = pageY - lastMovePoint.y

        // Get current bounds
        val bounds = editorState.selectionState.selectionBounds ?: return

        // Calculate new bounds based on the delta
        val newBounds = RectF(
            bounds.left + deltaX,
            bounds.top + deltaY,
            bounds.right + deltaX,
            bounds.bottom + deltaY
        )

        // Check boundaries using existing viewport constraints
        if (!isMovementWithinBoundaries(newBounds)) {
            return
        }

        // Update selection bounds
        editorState.selectionState.selectionBounds = newBounds

        // Update the cumulative move offset (add to existing offset)
        val currentOffset = editorState.selectionState.moveOffset
        editorState.selectionState.moveOffset = Offset(
            currentOffset.x + deltaX,
            currentOffset.y + deltaY
        )

        // IMPORTANT: Update the move start point to the current position for next delta
        editorState.selectionState.moveStartPoint = SimplePointF(pageX, pageY)

        // Force UI update
        refreshUi()
    }

    // Helper to check if movement stays within boundaries
    // Helper to check if movement stays within boundaries
    private fun isMovementWithinBoundaries(newBounds: RectF): Boolean {
        // Ensure the selection stays within page width boundaries only
        // We don't need to restrict vertical movement as much
        val documentWidth = page.width.toFloat()

        // Only restrict horizontal bounds to page width
        if (newBounds.left < 0 || newBounds.right > documentWidth) {
            return false
        }

        // Allow vertical movement without restriction to document height
        // This allows moving down past the original view

        // If pagination is enabled, check if selection crosses page boundaries
        if (viewportTransformer.getPaginationManager().isPaginationEnabled) {
            val paginationManager = viewportTransformer.getPaginationManager()

            // Get page indices for top and bottom of selection
            val topPageIndex = paginationManager.getPageIndexForY(newBounds.top)
            val bottomPageIndex = paginationManager.getPageIndexForY(newBounds.bottom)

            // If selection would cross a page boundary, only check exclusion zones
            // This allows movement within a page or across pages, but not into exclusion zones
            if (topPageIndex != bottomPageIndex) {
                // Check if any part would be in exclusion zone
                for (pageIndex in topPageIndex until bottomPageIndex) {
                    val exclusionZoneTop = paginationManager.getExclusionZoneTopY(pageIndex)
                    val exclusionZoneBottom = paginationManager.getExclusionZoneBottomY(pageIndex)

                    // If selection overlaps exclusion zone, don't allow move
                    if (!(newBounds.bottom <= exclusionZoneTop || newBounds.top >= exclusionZoneBottom)) {
                        return false
                    }
                }
            }
        }

        // If we've passed all the checks, allow the movement
        return true
    }

    // Complete the move operation
    fun completeMovingSelection(x: Float, y: Float) {
        if (!editorState.selectionState.isMovingSelection) return

        val offset = editorState.selectionState.moveOffset
        if (offset.x == 0f && offset.y == 0f) {
            // No actual movement happened
            editorState.selectionState.isMovingSelection = false
            editorState.selectionState.moveStartPoint = null
            return
        }

        // Apply the displacement to all selected strokes
        val selectedStrokes = editorState.selectionState.selectedStrokes ?: return
        val updatedStrokes = selectedStrokes.map { stroke ->
            // Create new point list with adjusted coordinates
            val newPoints = stroke.points.map { point ->
                val newX = point.x + offset.x
                val newY = point.y + offset.y
                point.copy(x = newX, y = newY)
            }

            // Create a new stroke with updated bounds and positions
            stroke.copy(
                left = stroke.left + offset.x,
                right = stroke.right + offset.x,
                top = stroke.top + offset.y,
                bottom = stroke.bottom + offset.y,
                points = newPoints,
                updatedAt = java.util.Date()
            )
        }

        // Record the move operation for undo/redo
        val strokeIds = selectedStrokes.map { it.id }
        val historyManager = (page.context.applicationContext as? com.wyldsoft.notes.NotesApp)?.let { app ->
            app.historyRepository.getHistoryManager(page.id)
        }

        historyManager?.addAction(
            HistoryAction(
                type = ActionType.MOVE_STROKES,
                data = MoveActionData(
                    strokeIds = strokeIds,
                    originalStrokes = selectedStrokes.map { SerializableStroke.fromStroke(it) },
                    modifiedStrokes = updatedStrokes.map { SerializableStroke.fromStroke(it) },
                    offsetX = offset.x,
                    offsetY = offset.y
                )
            )
        )

        // Remove old strokes and add new ones
        page.removeStrokes(strokeIds)
        page.addStrokes(updatedStrokes)

        // Update the selection with the new strokes
        editorState.selectionState.selectedStrokes = updatedStrokes

        // Reset move state
        editorState.selectionState.isMovingSelection = false
        editorState.selectionState.moveStartPoint = null
        editorState.selectionState.moveOffset = Offset(0f, 0f)

        // Force UI update
        refreshUi()
    }


    // Copy selected strokes to clipboard
    fun copySelection() {
        val selectedStrokes = editorState.selectionState.selectedStrokes ?: return
        if (selectedStrokes.isEmpty()) return

        // Create clipboard data - deep copy with new IDs
        val clipboardStrokes = selectedStrokes.map {
            it.copy(id = java.util.UUID.randomUUID().toString())
        }

        // Store in EditorState for paste operation
        editorState.selectionState.placementMode = PlacementMode.Paste
        editorState.selectionState.selectedStrokes = clipboardStrokes

        // Reset selection bounds for paste operation
        calculateSelectionBounds()

        // Notify user using existing SnackState
        notifyUser("${selectedStrokes.size} strokes copied")
    }

    // Paste the copied strokes
    fun pasteSelection(x: Float, y: Float) {
        if (editorState.selectionState.placementMode != PlacementMode.Paste) return

        val clipboardStrokes = editorState.selectionState.selectedStrokes ?: return
        if (clipboardStrokes.isEmpty()) return

        // Transform to page coordinates
        val (pageX, pageY) = viewportTransformer.viewToPageCoordinates(x, y)

        // Calculate offset from original position
        val bounds = editorState.selectionState.selectionBounds ?: return
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()

        val deltaX = pageX - centerX
        val deltaY = pageY - centerY

        // Create new strokes at the paste position
        val pastedStrokes = clipboardStrokes.map { stroke ->
            // Create new point list with adjusted coordinates
            val newPoints = stroke.points.map { point ->
                val newX = point.x + deltaX
                val newY = point.y + deltaY
                point.copy(x = newX, y = newY)
            }

            // Create a new stroke with updated bounds and positions
            stroke.copy(
                id = java.util.UUID.randomUUID().toString(),
                left = stroke.left + deltaX,
                right = stroke.right + deltaX,
                top = stroke.top + deltaY,
                bottom = stroke.bottom + deltaY,
                points = newPoints,
                createdAt = java.util.Date(),
                updatedAt = java.util.Date()
            )
        }

        // Add new strokes using existing PageView method
        page.addStrokes(pastedStrokes)

        // Update the selection with the new strokes
        editorState.selectionState.selectedStrokes = pastedStrokes
        editorState.selectionState.placementMode = null

        // Update bounds
        calculateSelectionBounds()

        // Force UI update
        refreshUi()

        // Notify user
        notifyUser("${pastedStrokes.size} strokes pasted")
    }

    // Helper function for user notifications
    private fun notifyUser(message: String) {
        coroutineScope.launch {
            com.wyldsoft.notes.classes.SnackState.globalSnackFlow.emit(
                com.wyldsoft.notes.classes.SnackConf(
                    text = message,
                    duration = 2000
                )
            )
        }
    }

    // Render the selection on the canvas
    // In SelectionHandler.kt, modify the renderSelection method
    fun renderSelection(canvas: Canvas) {
        // If currently drawing selection, draw the path
        if (editorState.selectionState.isDrawingSelection) {
            val path = editorState.selectionState.selectionPath ?: return

            // Transform for view coordinates
            canvas.save()
            canvas.translate(0f, -viewportTransformer.scrollY)
            canvas.drawPath(path, selectionPaint)
            canvas.restore()
        }

        // If strokes are selected, highlight them
        val selectedStrokes = editorState.selectionState.selectedStrokes ?: return
        if (selectedStrokes.isEmpty()) return

        // Draw selection bounds
        val bounds = editorState.selectionState.selectionBounds ?: return

        // Transform to view coordinates - ensure consistent transformation
        val viewBounds = RectF(
            bounds.left,
            bounds.top - viewportTransformer.scrollY,
            bounds.right,
            bounds.bottom - viewportTransformer.scrollY
        )

        // Draw highlight and border with consistent coordinates
        canvas.drawRect(viewBounds, highlightPaint)
        canvas.drawRect(viewBounds, borderPaint)
    }

    // Handle selection mode interactions
    fun handleTouchEvent(action: Int, x: Float, y: Float) {
        if (editorState.mode != Mode.Selection) return

        when (action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                // If we have a paste operation pending, paste at this location
                if (editorState.selectionState.placementMode == PlacementMode.Paste) {
                    pasteSelection(x, y)
                    return
                }

                // If we already have selected strokes, check if we're starting a move
                if (editorState.selectionState.selectedStrokes != null) {
                    startMovingSelection(x, y)
                } else {
                    // Otherwise start a new selection
                    startSelectionDraw(x, y)
                }
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                // If we're moving selection, update position
                if (editorState.selectionState.isMovingSelection) {
                    updateMovingSelection(x, y)
                } else if (editorState.selectionState.isDrawingSelection) {
                    // Otherwise continue drawing selection
                    updateSelectionDraw(x, y)
                }
            }
            android.view.MotionEvent.ACTION_UP -> {
                // Complete current operation
                if (editorState.selectionState.isMovingSelection) {
                    completeMovingSelection(x, y)
                } else if (editorState.selectionState.isDrawingSelection) {
                    completeSelectionDraw(x, y)
                }
            }
        }
    }

    // Use existing refreshUi from DrawingManager
    private fun refreshUi() {
        coroutineScope.launch {
            DrawingManager.refreshUi.emit(Unit)
        }
    }
}