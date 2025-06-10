package com.wyldsoft.notes.refreshingScreen

import android.graphics.RectF

/**
 * Handles coordinate transformations between page and view coordinates.
 * Manages zoom and scroll transformations.
 */
class CoordinateTransformer(
    private val viewWidth: Int,
    private val viewHeight: Int
) {
    // Zoom state
    var zoomScale: Float = 1.0f
        private set
    
    private var zoomCenterX: Float = 0f
    private var zoomCenterY: Float = 0f
    
    // Scroll state
    var scrollX: Float = 0f
        internal set
    var scrollY: Float = 0f
        internal set

    /**
     * Updates zoom parameters
     */
    fun updateZoom(scale: Float, centerX: Float, centerY: Float) {
        zoomScale = scale
        zoomCenterX = centerX
        zoomCenterY = centerY
    }

    /**
     * Updates scroll parameters
     */
    fun updateScroll(deltaX: Float, deltaY: Float) {
        scrollX += deltaX
        scrollY += deltaY
    }

    /**
     * Sets scroll position directly
     */
    fun setScroll(x: Float, y: Float) {
        scrollX = x
        scrollY = y
    }

    /**
     * Transforms a point from page coordinates to view coordinates
     */
    fun pageToViewCoordinates(x: Float, y: Float): Pair<Float, Float> {
        // Apply scale relative to the zoom center
        val scaledX = (x - zoomCenterX) * zoomScale + zoomCenterX
        val scaledY = (y - zoomCenterY) * zoomScale + zoomCenterY

        // Apply scroll offset
        return Pair(scaledX - scrollX, scaledY - scrollY)
    }

    /**
     * Transforms a point from view coordinates to page coordinates
     */
    fun viewToPageCoordinates(x: Float, y: Float): Pair<Float, Float> {
        // First adjust for scrolling
        val scrollAdjustedX = x + scrollX
        val scrollAdjustedY = y + scrollY

        // Then invert the zoom transformation
        val pageX = zoomCenterX + (scrollAdjustedX - zoomCenterX) / zoomScale
        val pageY = zoomCenterY + (scrollAdjustedY - zoomCenterY) / zoomScale

        return Pair(pageX, pageY)
    }

    /**
     * Returns the current viewport rect in page coordinates
     */
    fun getCurrentViewportInPageCoordinates(): RectF {
        val (topLeftX, topLeftY) = viewToPageCoordinates(0f, 0f)
        val (bottomRightX, bottomRightY) = viewToPageCoordinates(
            viewWidth.toFloat(), 
            viewHeight.toFloat()
        )

        return RectF(topLeftX, topLeftY, bottomRightX, bottomRightY)
    }

    /**
     * Calculates viewport with hypothetical scroll values
     */
    fun calculateViewportWithScroll(proposedScrollX: Float, proposedScrollY: Float): RectF {
        val tempScrollX = scrollX
        val tempScrollY = scrollY
        
        // Temporarily set proposed scroll values
        scrollX = proposedScrollX
        scrollY = proposedScrollY
        
        // Calculate viewport
        val viewport = getCurrentViewportInPageCoordinates()
        
        // Restore original scroll values
        scrollX = tempScrollX
        scrollY = tempScrollY
        
        return viewport
    }

    /**
     * Resets all transformations
     */
    fun reset() {
        zoomScale = 1.0f
        zoomCenterX = 0f
        zoomCenterY = 0f
        scrollX = 0f
        scrollY = 0f
    }
}