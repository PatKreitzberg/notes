package com.wyldsoft.notes.classes.drawing

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.unit.IntOffset
import com.wyldsoft.notes.classes.PageView
import com.wyldsoft.notes.utils.Pen
import com.wyldsoft.notes.utils.SimplePointF
import com.wyldsoft.notes.utils.Stroke
import com.wyldsoft.notes.utils.StrokePoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Responsible for managing the drawing operations.
 * Handles stroke creation, rendering, and coordination with PageView.
 */
class DrawingManager(private val page: PageView) {
    companion object {
        val forceUpdate = MutableSharedFlow<Rect?>()
        val refreshUi = MutableSharedFlow<Unit>()
        val isDrawing = MutableSharedFlow<Boolean>()
        val restartAfterConfChange = MutableSharedFlow<Unit>()
        val drawingInProgress = Mutex()
    }

    private val strokeHistoryBatch = mutableListOf<String>()

    private fun convertAndCreateBoundingBox(touchPoints: List<com.onyx.android.sdk.data.note.TouchPoint>, strokeSize: Float): RectF {
        val initialPoint = touchPoints.firstOrNull() ?: return RectF()
        val (pageX, pageY) = page.viewportTransformer.viewToPageCoordinates(initialPoint.x, initialPoint.y)
        val boundingBox = RectF(
            pageX,
            pageY,
            pageX,
            pageY
        )

        touchPoints.forEach { point ->
            boundingBox.union(point.x, point.y)
        }

        // Apply inset for stroke size
        boundingBox.inset(-strokeSize, -strokeSize)
        return boundingBox
    }

    private fun convertAndTransformToStrokePoints(touchPoints: List<com.onyx.android.sdk.data.note.TouchPoint>): List<StrokePoint> {
        return touchPoints.map { point ->
            // Transform point coordinates from view to page coordinate system
            val (pageX, pageY) = page.viewportTransformer.viewToPageCoordinates(point.x, point.y)

            println("scroll point.y=${point.y}  pageY=$pageY")
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
                color = color
            )

            page.addStrokes(listOf(stroke))

            // Draw the stroke on the page
            val rect = Rect(
                boundingBox.left.toInt(),
                boundingBox.top.toInt(),
                boundingBox.right.toInt(),
                boundingBox.bottom.toInt()
            )

            page.drawArea(rect)
            strokeHistoryBatch.add(stroke.id)
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

        path.moveTo(points[0].x, points[0].y)

        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
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
}