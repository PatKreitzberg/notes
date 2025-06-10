package com.wyldsoft.notes.strokeManagement

import android.graphics.RectF
import com.wyldsoft.notes.utils.Pen
import com.wyldsoft.notes.utils.Stroke
import com.wyldsoft.notes.utils.StrokePoint
import com.wyldsoft.notes.refreshingScreen.interfaces.IViewportTransformer

/**
 * Creates strokes from touch input data.
 * Handles coordinate transformation and bounding box calculation.
 */
class StrokeCreator(
    private val viewportTransformer: IViewportTransformer
) {

    /**
     * Creates a stroke from touch points and drawing parameters
     */
    fun createStroke(
        strokeSize: Float,
        color: Int,
        pen: Pen,
        pageId: String,
        touchPoints: List<com.onyx.android.sdk.data.note.TouchPoint>
    ): Stroke? {
        if (touchPoints.isEmpty()) return null

        // Transform touch points to stroke points
        val strokePoints = convertToStrokePoints(touchPoints)
        
        // Calculate bounding box
        val boundingBox = calculateBoundingBox(touchPoints, strokeSize)

        return Stroke(
            size = strokeSize,
            pen = pen,
            pageId = pageId,
            top = boundingBox.top,
            bottom = boundingBox.bottom,
            left = boundingBox.left,
            right = boundingBox.right,
            points = strokePoints,
            color = color,
            createdScrollY = getCurrentScrollY()
        )
    }

    /**
     * Converts touch points to stroke points with coordinate transformation
     */
    private fun convertToStrokePoints(touchPoints: List<com.onyx.android.sdk.data.note.TouchPoint>): List<StrokePoint> {
        return touchPoints.map { point ->
            // Transform point coordinates from view to page coordinate system
            val (pageX, pageY) = viewportTransformer.viewToPageCoordinates(point.x, point.y)

            StrokePoint(
                x = pageX,
                y = pageY,
                pressure = point.pressure,
                size = point.size,
                tiltX = point.tiltX,
                tiltY = point.tiltY,
                timestamp = point.timestamp
            )
        }
    }

    /**
     * Calculates bounding box for touch points with stroke size padding
     */
    private fun calculateBoundingBox(
        touchPoints: List<com.onyx.android.sdk.data.note.TouchPoint>, 
        strokeSize: Float
    ): RectF {
        val initialPoint = touchPoints.firstOrNull() ?: return RectF()

        // Transform the initial point correctly
        val (pageX, pageY) = viewportTransformer.viewToPageCoordinates(initialPoint.x, initialPoint.y)

        // Initialize bounding box with transformed coordinates
        val boundingBox = RectF(pageX, pageY, pageX, pageY)

        // For each touch point, transform it to page coordinates before adding to bounding box
        touchPoints.forEach { point ->
            val (transformedX, transformedY) = viewportTransformer.viewToPageCoordinates(point.x, point.y)
            boundingBox.union(transformedX, transformedY)
        }

        // Apply inset for stroke size
        boundingBox.inset(-strokeSize, -strokeSize)
        return boundingBox
    }

    /**
     * Gets current scroll Y position for stroke metadata
     */
    private fun getCurrentScrollY(): Float {
        // This would need to be implemented based on viewport transformer interface
        return 0f // Simplified for now
    }
}