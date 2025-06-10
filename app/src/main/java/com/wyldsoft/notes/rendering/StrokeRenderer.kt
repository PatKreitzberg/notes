package com.wyldsoft.notes.rendering

import android.graphics.Canvas
import android.graphics.RectF
import com.wyldsoft.notes.utils.Stroke
import com.wyldsoft.notes.refreshingScreen.ViewportTransformer

/**
 * Handles rendering of individual strokes.
 * Optimizes rendering by only drawing visible strokes.
 */
class StrokeRenderer(
    private val viewportTransformer: ViewportTransformer
) {

    /**
     * Renders only the strokes that are visible in the current viewport
     */
    fun renderVisibleStrokes(canvas: Canvas, strokes: List<Stroke>) {
        for (stroke in strokes) {
            val strokeBounds = RectF(
                stroke.left,
                stroke.top,
                stroke.right,
                stroke.bottom
            )

            // Skip strokes that are not in the viewport
            if (!viewportTransformer.isRectVisible(strokeBounds)) {
                continue
            }

            renderStroke(canvas, stroke)
        }
    }

    /**
     * Renders a single stroke to the canvas
     */
    private fun renderStroke(canvas: Canvas, stroke: Stroke) {
        if (stroke.points.isEmpty()) return

        val paint = android.graphics.Paint().apply {
            color = stroke.color
            strokeWidth = stroke.size
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            isAntiAlias = true
        }

        val path = android.graphics.Path()
        
        // Transform first point to view coordinates
        val firstPoint = stroke.points[0]
        val (viewX, viewY) = viewportTransformer.pageToViewCoordinates(firstPoint.x, firstPoint.y)
        path.moveTo(viewX, viewY)

        // Add subsequent points
        for (i in 1 until stroke.points.size) {
            val point = stroke.points[i]
            val (transformedX, transformedY) = viewportTransformer.pageToViewCoordinates(point.x, point.y)
            path.lineTo(transformedX, transformedY)
        }

        canvas.drawPath(path, paint)
    }
}