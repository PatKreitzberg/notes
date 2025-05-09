package com.wyldsoft.notes.classes.drawing

import android.graphics.Rect
import android.graphics.RectF
import com.wyldsoft.notes.history.ActionType
import com.wyldsoft.notes.history.HistoryAction
import com.wyldsoft.notes.history.HistoryManager
import com.wyldsoft.notes.history.InsertPageActionData
import com.wyldsoft.notes.history.MoveActionData
import com.wyldsoft.notes.views.PageView
import com.wyldsoft.notes.utils.Pen
import com.wyldsoft.notes.history.SerializableStroke
import com.wyldsoft.notes.utils.SimplePointF
import com.wyldsoft.notes.utils.Stroke
import com.wyldsoft.notes.history.StrokeActionData
import com.wyldsoft.notes.utils.StrokePoint
import io.shipbook.shipbooksdk.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Responsible for managing the drawing operations.
 * Handles stroke creation, rendering, and coordination with PageView.
 */
class DrawingManager(
    private val page: PageView,
    private val historyManager: HistoryManager? = null
) {
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
    val tag="DrawingManager:"

    private fun convertAndCreateBoundingBox(touchPoints: List<com.onyx.android.sdk.data.note.TouchPoint>, strokeSize: Float): RectF {
        val initialPoint = touchPoints.firstOrNull() ?: return RectF()

        // Transform the initial point correctly
        val (pageX, pageY) = page.viewportTransformer.viewToPageCoordinates(initialPoint.x, initialPoint.y)

        // Initialize bounding box with transformed coordinates
        val boundingBox = RectF(
            pageX,
            pageY,
            pageX,
            pageY
        )

        // For each touch point, transform it to page coordinates before adding to bounding box
        touchPoints.forEach { point ->
            val (transformedX, transformedY) = page.viewportTransformer.viewToPageCoordinates(point.x, point.y)
            boundingBox.union(transformedX, transformedY)
        }

        // Apply inset for stroke size
        boundingBox.inset(-strokeSize, -strokeSize)
        return boundingBox
    }

    private fun convertAndTransformToStrokePoints(touchPoints: List<com.onyx.android.sdk.data.note.TouchPoint>): List<StrokePoint> {
        return touchPoints.map { point ->
            // Transform point coordinates from view to page coordinate system
            val (pageX, pageY) = page.viewportTransformer.viewToPageCoordinates(point.x, point.y)

            StrokePoint(
                x = pageX,
                y = pageY,
                pressure = point.pressure,
                size = point.size,
                tiltX = point.tiltX,
                tiltY = point.tiltY,
                timestamp = point.timestamp,
            )
        }
    }

    // Updated handleDraw method that uses the helper functions
    fun handleDraw(
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

            // Create bounding box and convert points
            val boundingBox = convertAndCreateBoundingBox(touchPoints, strokeSize)
            val points = convertAndTransformToStrokePoints(touchPoints)

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
                color = color,
                createdScrollY = page.viewportTransformer.scrollY
            )

            // Add to page
            val strokes = listOf(stroke)
            page.addStrokes(strokes)

            // Draw the stroke on the page
            val rect = Rect(
                boundingBox.left.toInt(),
                boundingBox.top.toInt(),
                boundingBox.right.toInt(),
                boundingBox.bottom.toInt()
            )

            page.drawArea(rect)
            strokeHistoryBatch.add(stroke.id)

            // Record in history for undo/redo
            historyManager?.addAction(
                HistoryAction(
                    type = ActionType.ADD_STROKES,
                    data = StrokeActionData(
                        strokeIds = strokes.map { it.id },
                        strokes = strokes.map { SerializableStroke.fromStroke(it) }
                    )
                )
            ).also { println("undo: Added stroke action to history") }
                ?: println("undo: Could not add stroke action to history - manager is null")
        } catch (e: Exception) {
            println("DEBUG ERROR: Handle Draw: ${e.message}")
            e.printStackTrace()
        }
    }

    fun handleErase(
        points: List<SimplePointF>,
        eraser: com.wyldsoft.notes.utils.Eraser
    ) {
        val path = android.graphics.Path()
        if (points.isEmpty()) return

        // Transform the first point from view to page coordinates
        val (firstPageX, firstPageY) = page.viewportTransformer.viewToPageCoordinates(points[0].x, points[0].y)
        path.moveTo(firstPageX, firstPageY)

        // Transform each subsequent point from view to page coordinates
        for (i in 1 until points.size) {
            val (pageX, pageY) = page.viewportTransformer.viewToPageCoordinates(points[i].x, points[i].y)
            path.lineTo(pageX, pageY)
        }

        var outPath = android.graphics.Path()

        if (eraser == com.wyldsoft.notes.utils.Eraser.SELECT) {
            path.close()
            outPath = path
        } else {
            val paint = android.graphics.Paint().apply {
                this.strokeWidth = 30f
                this.style = android.graphics.Paint.Style.STROKE
                this.strokeCap = android.graphics.Paint.Cap.ROUND
                this.strokeJoin = android.graphics.Paint.Join.ROUND
                this.isAntiAlias = true
            }
            paint.getFillPath(path, outPath)
        }

        val deletedStrokes = selectStrokesFromPath(page.strokes, outPath)
        println("$tag deletedStrokes $deletedStrokes")
        val deletedStrokeIds = deletedStrokes.map { it.id }
        println("$tag deletedStrokeIds $deletedStrokeIds")

        // Skip if no strokes to delete
        if (deletedStrokes.isEmpty()) return

        // Record in history for undo/redo before removing
        historyManager?.let { manager ->
            if (deletedStrokes.isNotEmpty()) {
                manager.addAction(
                    HistoryAction(
                        type = ActionType.DELETE_STROKES,
                        data = StrokeActionData(
                            strokeIds = deletedStrokeIds,
                            strokes = deletedStrokes.map { SerializableStroke.fromStroke(it) }
                        )
                    )
                )
            }
        }

        // Remove the strokes
        page.removeStrokes(deletedStrokeIds)

        page.drawArea(
            area = Rect(
                getStrokeBounds(deletedStrokes).left.toInt(),
                getStrokeBounds(deletedStrokes).top.toInt(),
                getStrokeBounds(deletedStrokes).right.toInt(),
                getStrokeBounds(deletedStrokes).bottom.toInt()
            )
        )
    }

    private fun selectStrokesFromPath(strokes: List<Stroke>, path: android.graphics.Path): List<Stroke> {
        val bounds = RectF()
        path.computeBounds(bounds, true)

        // Instead of using Region.setPath, we'll check points against the path directly
        return strokes.filter {
            val strokeBounds = RectF(it.left, it.top, it.right, it.bottom)

            // First check: bounding box intersection
            val intersects = RectF.intersects(strokeBounds, bounds)

            intersects
        }.filter {
            // Second check: points inside path
            val containsPoint = it.points.any { point ->
                // Check if the point is within the path
                val bounds = RectF()
                path.computeBounds(bounds, true)

                var ret = true
                // Quick check: if point is outside bounds, it's definitely not in path
                if (point.x < bounds.left || point.x > bounds.right || point.y < bounds.top || point.y > bounds.bottom) {
                    ret = false
                }

                // Use Path.contains for more accurate check
                if (ret) {
                    ret = path.isPointInPath(point.x, point.y)
                }
                ret
            }
            containsPoint
        }
    }



    // Extension function for Path
    private fun android.graphics.Path.isPointInPath(x: Float, y: Float): Boolean {
        val bounds = RectF()
        computeBounds(bounds, true)

        // Create a temporary path for testing
        val testPath = android.graphics.Path()

        // Add a small circle around the test point
        testPath.addCircle(x, y, 0.1f, android.graphics.Path.Direction.CW)

        // Check if this small circle intersects with the path
        val isInside = android.graphics.Path()
        isInside.op(this, testPath, android.graphics.Path.Op.INTERSECT)

        // If the result is not empty, the point is inside the path
        val resultBounds = RectF()
        isInside.computeBounds(resultBounds, true)
        return !resultBounds.isEmpty
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

    /**
     * Performs an undo operation
     * @return true if undo was successful, false otherwise
     */
    fun undo(): Boolean {
        if (historyManager == null) return false

        val action = historyManager.undo() ?: return false

        when (action.type) {
            ActionType.ADD_STROKES -> {
                // For added strokes, undo by removing them
                val data = action.data as StrokeActionData
                page.removeStrokes(data.strokeIds)
            }

            ActionType.DELETE_STROKES -> {
                // For deleted strokes, undo by adding them back
                val data = action.data as StrokeActionData
                val strokes = data.strokes.map { it.toStroke() }
                page.addStrokes(strokes)
            }

            ActionType.MOVE_STROKES -> {
                // For moved strokes, undo by restoring original positions
                val data = action.data as MoveActionData
                val originalStrokes = data.originalStrokes.map { it.toStroke() }

                // First remove the moved strokes
                page.removeStrokes(data.strokeIds)

                // Then add back the original ones
                page.addStrokes(originalStrokes)
            }

            ActionType.INSERT_PAGE -> {
                // For inserted pages, undo by shifting strokes back up
                val data = action.data as InsertPageActionData

                // Find strokes that were affected
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
                val paginationManager = page.viewportTransformer.getPaginationManager()
                val newMaxPageIndex = paginationManager.getTotalPageCount() - 2 // -1 for 0-based, -1 for removed page
                val newHeight = paginationManager.getPageBottomY(newMaxPageIndex.coerceAtLeast(0))
                page.height = newHeight.toInt()
                page.viewportTransformer.updateDocumentHeight(page.height)
            }
        }

        // Force UI update
        kotlinx.coroutines.GlobalScope.launch {
            undoRedoPerformed.emit(Unit)
            refreshUi.emit(Unit)
        }

        return true
    }

    /**
     * Performs a redo operation
     * @return true if redo was successful, false otherwise
     */
    fun redo(): Boolean {
        if (historyManager == null) return false

        val action = historyManager.redo() ?: return false

        when (action.type) {
            ActionType.ADD_STROKES -> {
                // For added strokes, redo by adding them back
                val data = action.data as StrokeActionData
                val strokes = data.strokes.map { it.toStroke() }
                page.addStrokes(strokes)
            }

            ActionType.DELETE_STROKES -> {
                // For deleted strokes, redo by removing them again
                val data = action.data as StrokeActionData
                page.removeStrokes(data.strokeIds)
            }

            ActionType.MOVE_STROKES -> {
                // For moved strokes, redo by applying the move again
                val data = action.data as MoveActionData
                val movedStrokes = data.modifiedStrokes.map { it.toStroke() }

                // First remove the original strokes
                page.removeStrokes(data.strokeIds)

                // Then add the moved ones
                page.addStrokes(movedStrokes)
            }

            ActionType.INSERT_PAGE -> {
                // For inserted pages, redo by shifting strokes down again
                val data = action.data as InsertPageActionData

                // Find strokes that were affected
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
                val paginationManager = page.viewportTransformer.getPaginationManager()
                val newMaxPageIndex = paginationManager.getTotalPageCount() // No need to adjust for 0-based since we're adding a page
                val newHeight = paginationManager.getPageBottomY(newMaxPageIndex - 1) // -1 for 0-based
                page.height = newHeight.toInt()
                page.viewportTransformer.updateDocumentHeight(page.height)
            }
        }

        // Force UI update
        kotlinx.coroutines.GlobalScope.launch {
            undoRedoPerformed.emit(Unit)
            refreshUi.emit(Unit)
        }
        return true
    }

    /**
     * Inserts a new page at the specified position
     * @param pageNumber The 1-based page number before which to insert
     * @return True if the operation was successful
     */
    fun insertPage(pageNumber: Int): Boolean {
        try {
            // Get pagination manager
            val paginationManager = page.viewportTransformer.getPaginationManager()

            // Validate page number
            val totalPages = paginationManager.getTotalPageCount()
            if (pageNumber < 1 || pageNumber > totalPages) {
                return false
            }

            // Calculate insertion position
            val insertPosition = paginationManager.getInsertPosition(pageNumber)

            // Calculate offset
            val pageOffset = paginationManager.getPageInsertionOffset()

            // Find strokes that need to be moved
            val affectedStrokeIds = mutableListOf<String>()
            val strokesToUpdate = page.strokes.filter { stroke ->
                val isBelow = stroke.top >= insertPosition
                if (isBelow) affectedStrokeIds.add(stroke.id)
                isBelow
            }

            // Move affected strokes down
            for (stroke in strokesToUpdate) {
                stroke.top += pageOffset
                stroke.bottom += pageOffset

                // Update points
                for (point in stroke.points) {
                    point.y += pageOffset
                }
            }

            // Record in history for undo/redo
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

            // If strokes were affected, update them
            if (strokesToUpdate.isNotEmpty()) {
                // First remove them from the page
                page.removeStrokes(affectedStrokeIds)

                // Then add them back with updated positions
                page.addStrokes(strokesToUpdate)
            }

            // Update document height
            val newHeight = paginationManager.getPageBottomY(paginationManager.getTotalPageCount() - 1)
            page.height = newHeight.toInt()
            page.viewportTransformer.updateDocumentHeight(page.height)

            return true
        } catch (e: Exception) {
            println("Error inserting page: ${e.message}")
            return false
        }
    }
}