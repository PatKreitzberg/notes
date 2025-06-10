package com.wyldsoft.notes.strokeManagement

import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import com.wyldsoft.notes.utils.SimplePointF
import com.wyldsoft.notes.utils.Stroke
import com.wyldsoft.notes.refreshingScreen.interfaces.IViewportTransformer

/**
 * Handles stroke erasing operations.
 * Determines which strokes intersect with erase paths.
 */
class StrokeEraser(
    private val viewportTransformer: IViewportTransformer
) {

    /**
     * Finds strokes that intersect with the erase path
     */
    fun findStrokesToErase(
        strokes: List<Stroke>,
        erasePoints: List<SimplePointF>,
        eraser: com.wyldsoft.notes.utils.Eraser
    ): List<Stroke> {
        if (erasePoints.isEmpty()) return emptyList()

        val erasePath = createErasePath(erasePoints, eraser)
        return selectStrokesFromPath(strokes, erasePath)
    }

    /**
     * Creates the erase path from touch points
     */
    private fun createErasePath(
        points: List<SimplePointF>,
        eraser: com.wyldsoft.notes.utils.Eraser
    ): Path {
        val path = Path()
        
        // Transform the first point from view to page coordinates
        val (firstPageX, firstPageY) = viewportTransformer.viewToPageCoordinates(points[0].x, points[0].y)
        path.moveTo(firstPageX, firstPageY)

        // Transform each subsequent point from view to page coordinates
        for (i in 1 until points.size) {
            val (pageX, pageY) = viewportTransformer.viewToPageCoordinates(points[i].x, points[i].y)
            path.lineTo(pageX, pageY)
        }

        return when (eraser) {
            com.wyldsoft.notes.utils.Eraser.SELECT -> {
                path.close()
                path
            }
            com.wyldsoft.notes.utils.Eraser.PEN -> {
                val paint = android.graphics.Paint().apply {
                    strokeWidth = 30f
                    style = android.graphics.Paint.Style.STROKE
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                    isAntiAlias = true
                }
                val outPath = Path()
                paint.getFillPath(path, outPath)
                outPath
            }
        }
    }

    /**
     * Selects strokes that intersect with the erase path
     */
    private fun selectStrokesFromPath(strokes: List<Stroke>, path: Path): List<Stroke> {
        // Get the bounds of the path
        val pathBounds = RectF()
        path.computeBounds(pathBounds, true)

        // Convert path to a Region for faster hit testing
        val pathRegion = Region()
        pathRegion.setPath(
            path,
            Region(
                pathBounds.left.toInt(),
                pathBounds.top.toInt(),
                pathBounds.right.toInt(),
                pathBounds.bottom.toInt()
            )
        )

        return strokes.filter { stroke ->
            strokeIntersectsPath(stroke, pathBounds, pathRegion)
        }
    }

    /**
     * Checks if a stroke intersects with the erase path
     */
    private fun strokeIntersectsPath(stroke: Stroke, pathBounds: RectF, pathRegion: Region): Boolean {
        val strokeBounds = RectF(stroke.left, stroke.top, stroke.right, stroke.bottom)

        // First quick check: bounding box intersection
        if (!RectF.intersects(strokeBounds, pathBounds)) {
            return false
        }

        // Sample stroke points for intersection testing
        val pointsToTest = sampleStrokePoints(stroke)

        // Check if any sampled points are inside the path region
        return pointsToTest.any { point ->
            pathRegion.contains(point.x.toInt(), point.y.toInt())
        }
    }

    /**
     * Samples points from a stroke for efficient intersection testing
     */
    private fun sampleStrokePoints(stroke: Stroke): List<com.wyldsoft.notes.utils.StrokePoint> {
        val totalPoints = stroke.points.size
        
        // Use at most 20 points for very long strokes, and at least 3 for short strokes
        val sampleSize = when {
            totalPoints < 10 -> totalPoints
            totalPoints < 50 -> totalPoints / 3
            totalPoints < 200 -> totalPoints / 10
            else -> 20
        }.coerceAtLeast(3)

        return if (sampleSize >= totalPoints) {
            stroke.points
        } else {
            // Generate evenly distributed indices
            val step = (totalPoints - 1).toFloat() / (sampleSize - 1).toFloat()
            (0 until sampleSize).map { i ->
                val index = (i * step).toInt().coerceIn(0, totalPoints - 1)
                stroke.points[index]
            }
        }
    }

    /**
     * Calculates bounding box for a list of strokes
     */
    fun getStrokeBounds(strokes: List<Stroke>): RectF {
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