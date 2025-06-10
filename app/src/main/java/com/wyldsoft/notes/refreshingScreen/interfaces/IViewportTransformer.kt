package com.wyldsoft.notes.refreshingScreen.interfaces

import android.graphics.RectF

/**
 * Interface for viewport transformation operations
 */
interface IViewportTransformer {
    /**
     * Scrolls the viewport by specified amounts
     */
    fun scroll(deltaX: Float, deltaY: Float)
    
    /**
     * Zooms the viewport by specified factor at point
     */
    fun zoom(scaleFactor: Float, focusX: Float, focusY: Float)
    
    /**
     * Converts page coordinates to view coordinates
     */
    fun pageToViewCoordinates(pageX: Float, pageY: Float): Pair<Float, Float>
    
    /**
     * Converts view coordinates to page coordinates
     */
    fun viewToPageCoordinates(viewX: Float, viewY: Float): Pair<Float, Float>
    
    /**
     * Checks if a rectangle is visible in the current viewport
     */
    fun isRectVisible(rect: RectF): Boolean
    
    /**
     * Updates the document height
     */
    fun updateDocumentHeight(height: Int)
}