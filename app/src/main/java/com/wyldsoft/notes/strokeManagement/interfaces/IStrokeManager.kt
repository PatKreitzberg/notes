package com.wyldsoft.notes.strokeManagement.interfaces

import com.wyldsoft.notes.utils.Pen
import com.wyldsoft.notes.utils.SimplePointF

/**
 * Interface for stroke management operations
 */
interface IStrokeManager {
    /**
     * Handles drawing operations with touch points
     */
    fun handleDraw(
        strokeSize: Float,
        color: Int,
        pen: Pen,
        touchPoints: List<com.onyx.android.sdk.data.note.TouchPoint>
    )
    
    /**
     * Handles erasing operations
     */
    fun handleErase(
        points: List<SimplePointF>,
        eraser: com.wyldsoft.notes.utils.Eraser
    )
    
    /**
     * Performs undo operation
     */
    fun undo(): Boolean
    
    /**
     * Performs redo operation
     */
    fun redo(): Boolean
    
    /**
     * Inserts a new page at specified position
     */
    fun insertPage(pageNumber: Int): Boolean
}