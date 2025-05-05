package com.wyldsoft.notes.classes.drawing

import android.graphics.Rect
import android.graphics.RectF
import com.wyldsoft.notes.utils.ActionType
import com.wyldsoft.notes.utils.HistoryAction
import com.wyldsoft.notes.utils.HistoryManager
import com.wyldsoft.notes.utils.MoveActionData
import com.wyldsoft.notes.views.PageView
import com.wyldsoft.notes.utils.Pen
import com.wyldsoft.notes.utils.SerializableStroke
import com.wyldsoft.notes.utils.SimplePointF
import com.wyldsoft.notes.utils.Stroke
import com.wyldsoft.notes.utils.StrokeActionData
import com.wyldsoft.notes.utils.StrokePoint
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
        val deletedStrokeIds = deletedStrokes.map { it.id }

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
        }

        // Force UI update
        kotlinx.coroutines.GlobalScope.launch {
            undoRedoPerformed.emit(Unit)
            refreshUi.emit(Unit)
        }

        return true
    }
}