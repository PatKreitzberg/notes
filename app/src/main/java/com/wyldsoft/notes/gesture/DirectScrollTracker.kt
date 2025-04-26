// app/src/main/java/com/wyldsoft/notes/gesture/DirectScrollTracker.kt
package com.wyldsoft.notes.gesture

import android.view.MotionEvent
import com.wyldsoft.notes.transform.ViewportTransformer
import kotlinx.coroutines.CoroutineScope

/**
 * Tracks finger movement directly for scrolling content.
 * This provides a direct mapping between finger position and scroll position.
 */
class DirectScrollTracker(
    private val coroutineScope: CoroutineScope,
    private val viewportTransformer: ViewportTransformer
) {
    // Track previous touch position for delta calculation
    private var lastTouchY = 0f
    private var isScrolling = false

    // Process touch events for direct scrolling
    fun processTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Start tracking
                lastTouchY = event.y
                isScrolling = true
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isScrolling) {
                    // Calculate delta (how far finger moved since last position)
                    val deltaY = lastTouchY - event.y

                    // Apply scroll - positive delta means finger moving up, content should scroll down
                    viewportTransformer.scroll(deltaY, false)

                    // Update last position
                    lastTouchY = event.y
                    return true
                }
                return false
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isScrolling = false
                return true
            }
        }
        return false
    }
}