package com.wyldsoft.notes.touchHandling.interfaces

/**
 * Interface for touch event handling operations
 */
interface ITouchEventHandler {
    /**
     * Sets up touch interception for surface view
     */
    fun setupTouchInterception()
    
    /**
     * Updates the active drawing surface with exclusion zones
     */
    fun updateActiveSurface()
    
    /**
     * Updates pen and stroke settings
     */
    fun updatePenAndStroke()
    
    /**
     * Enables or disables raw drawing mode
     */
    fun setRawDrawingEnabled(enabled: Boolean)
    
    /**
     * Closes raw drawing mode
     */
    fun closeRawDrawing()
}