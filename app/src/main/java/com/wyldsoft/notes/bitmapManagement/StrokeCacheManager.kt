package com.wyldsoft.notes.bitmapManagement

import com.wyldsoft.notes.bitmapManagement.interfaces.ICacheManager
import com.wyldsoft.notes.utils.Stroke
import java.util.LinkedHashMap

/**
 * LRU cache implementation for storing stroke data.
 * Manages memory-efficient caching of recently accessed strokes.
 */
class StrokeCacheManager(
    private val maxCacheSize: Int = 10,
    private val memoryThreshold: Long = 50 * 1024 * 1024 // 50MB
) : ICacheManager {
    
    // LRU cache implementation
    private val cache = object : LinkedHashMap<String, List<Stroke>>(maxCacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, List<Stroke>>): Boolean {
            return size > maxCacheSize || estimateMemoryUsage() > memoryThreshold
        }
    }
    
    private val memoryMonitor = MemoryMonitor()

    /**
     * Retrieves cached strokes for a note
     */
    override fun getStrokes(noteId: String): List<Stroke>? {
        return cache[noteId]
    }

    /**
     * Stores strokes in cache for a note
     */
    override fun putStrokes(noteId: String, strokes: List<Stroke>) {
        if (shouldCache(strokes)) {
            cache[noteId] = strokes
            memoryMonitor.recordCacheOperation(strokes.size)
        }
    }

    /**
     * Updates cached strokes for a note
     */
    override fun updateStrokes(noteId: String, strokes: List<Stroke>) {
        val existing = cache[noteId] ?: emptyList()
        val updated = existing + strokes
        
        if (shouldCache(updated)) {
            cache[noteId] = updated
            memoryMonitor.recordCacheOperation(strokes.size)
        }
    }

    /**
     * Removes cached strokes for a note
     */
    override fun removeStrokes(noteId: String) {
        cache.remove(noteId)?.let { removedStrokes ->
            memoryMonitor.recordRemoval(removedStrokes.size)
        }
    }

    /**
     * Clears all cached data
     */
    override fun clearAll() {
        cache.clear()
        memoryMonitor.reset()
    }

    /**
     * Removes specific strokes from a note's cache
     */
    fun removeStrokesFromNote(noteId: String, strokeIds: List<String>) {
        val existing = cache[noteId] ?: return
        val filtered = existing.filter { it.id !in strokeIds }
        
        if (filtered != existing) {
            cache[noteId] = filtered
            memoryMonitor.recordRemoval(existing.size - filtered.size)
        }
    }

    /**
     * Estimates memory usage of cached data
     */
    private fun estimateMemoryUsage(): Long {
        return cache.values.sumOf { strokes ->
            strokes.sumOf { stroke ->
                // Rough estimate: stroke overhead + points
                64 + (stroke.points.size * 32L)
            }
        }
    }

    /**
     * Determines if strokes should be cached based on size and content
     */
    private fun shouldCache(strokes: List<Stroke>): Boolean {
        val estimatedSize = strokes.sumOf { it.points.size * 32L }
        return estimatedSize < memoryThreshold / 5 // Don't cache if >20% of threshold
    }

    /**
     * Gets cache statistics for monitoring
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            size = cache.size,
            memoryUsage = estimateMemoryUsage(),
            hitCount = memoryMonitor.hitCount,
            missCount = memoryMonitor.missCount
        )
    }
}

/**
 * Data class for cache statistics
 */
data class CacheStats(
    val size: Int,
    val memoryUsage: Long,
    val hitCount: Long,
    val missCount: Long
) {
    val hitRate: Double
        get() = if (hitCount + missCount > 0) hitCount.toDouble() / (hitCount + missCount) else 0.0
}