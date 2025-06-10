package com.wyldsoft.notes.rendering.interfaces

/**
 * Interface for canvas rendering operations
 */
interface ICanvasRenderer {
    /**
     * Initializes the renderer and draws initial content
     */
    fun initialize()
    
    /**
     * Renders the current page state to the surface view
     */
    fun drawCanvasToView()
}