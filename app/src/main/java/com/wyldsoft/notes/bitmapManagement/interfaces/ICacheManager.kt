package com.wyldsoft.notes.bitmapManagement.interfaces

import com.wyldsoft.notes.utils.Stroke

/**
 * Interface for cache management operations
 */
interface ICacheManager {
    /**
     * Retrieves cached strokes for a note
     */
    fun getStrokes(noteId: String): List<Stroke>?
    
    /**
     * Stores strokes in cache for a note
     */
    fun putStrokes(noteId: String, strokes: List<Stroke>)
    
    /**
     * Updates cached strokes for a note
     */
    fun updateStrokes(noteId: String, strokes: List<Stroke>)
    
    /**
     * Removes cached strokes for a note
     */
    fun removeStrokes(noteId: String)
    
    /**
     * Clears all cached data
     */
    fun clearAll()
}