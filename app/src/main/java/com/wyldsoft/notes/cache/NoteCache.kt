// app/src/main/java/com/wyldsoft/notes/cache/NoteCache.kt
package com.wyldsoft.notes.cache

import com.wyldsoft.notes.utils.Stroke
import java.util.LinkedHashMap

/**
 * Cache for storing recently accessed notes' strokes to avoid reloading from database
 */
class NoteCache(private val maxCacheSize: Int = 10) {
    // LRU (Least Recently Used) cache implementation
    private val cache = object : LinkedHashMap<String, List<Stroke>>(maxCacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, List<Stroke>>): Boolean {
            return size > maxCacheSize
        }
    }

    /**
     * Get strokes for a note from cache
     * @param noteId The ID of the note
     * @return List of strokes if cached, null otherwise
     */
    fun getStrokes(noteId: String): List<Stroke>? {
        return cache[noteId]
    }

    /**
     * Store strokes in cache
     * @param noteId The ID of the note
     * @param strokes The strokes to cache
     */
    fun putStrokes(noteId: String, strokes: List<Stroke>) {
        cache[noteId] = strokes
    }

    /**
     * Update cached strokes with new ones
     * @param noteId The ID of the note
     * @param newStrokes The strokes to add to cache
     */
    fun updateStrokes(noteId: String, newStrokes: List<Stroke>) {
        val existing = cache[noteId] ?: emptyList()
        cache[noteId] = existing + newStrokes
    }

    /**
     * Remove strokes from cache
     * @param noteId The ID of the note
     * @param strokeIds The IDs of strokes to remove
     */
    fun removeStrokes(noteId: String, strokeIds: List<String>) {
        val existing = cache[noteId] ?: return
        cache[noteId] = existing.filter { it.id !in strokeIds }
    }

    /**
     * Clear a specific note from cache
     * @param noteId The ID of the note to clear
     */
    fun clearNote(noteId: String) {
        cache.remove(noteId)
    }

    /**
     * Clear all cached notes
     */
    fun clearAll() {
        cache.clear()
    }
}