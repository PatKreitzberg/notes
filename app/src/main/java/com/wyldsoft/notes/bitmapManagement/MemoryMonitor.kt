package com.wyldsoft.notes.bitmapManagement

/**
 * Monitors memory usage and cache performance.
 * Tracks cache hits, misses, and memory allocation patterns.
 */
class MemoryMonitor {
    var hitCount: Long = 0
        private set
    
    var missCount: Long = 0
        private set
    
    private var totalOperations: Long = 0
    private var totalStrokesCached: Long = 0

    /**
     * Records a cache hit operation
     */
    fun recordHit() {
        hitCount++
    }

    /**
     * Records a cache miss operation
     */
    fun recordMiss() {
        missCount++
    }

    /**
     * Records a cache operation with stroke count
     */
    fun recordCacheOperation(strokeCount: Int) {
        totalOperations++
        totalStrokesCached += strokeCount
    }

    /**
     * Records removal of strokes from cache
     */
    fun recordRemoval(strokeCount: Int) {
        totalStrokesCached = (totalStrokesCached - strokeCount).coerceAtLeast(0)
    }

    /**
     * Resets all monitoring statistics
     */
    fun reset() {
        hitCount = 0
        missCount = 0
        totalOperations = 0
        totalStrokesCached = 0
    }

    /**
     * Gets current memory pressure level
     */
    fun getMemoryPressure(): MemoryPressure {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val usageRatio = usedMemory.toDouble() / totalMemory

        return when {
            usageRatio > 0.9 -> MemoryPressure.HIGH
            usageRatio > 0.7 -> MemoryPressure.MEDIUM
            else -> MemoryPressure.LOW
        }
    }

    /**
     * Suggests whether cache should be trimmed
     */
    fun shouldTrimCache(): Boolean {
        return getMemoryPressure() == MemoryPressure.HIGH ||
               (totalStrokesCached > 10000 && getMemoryPressure() == MemoryPressure.MEDIUM)
    }
}

/**
 * Enum representing memory pressure levels
 */
enum class MemoryPressure {
    LOW,
    MEDIUM,
    HIGH
}