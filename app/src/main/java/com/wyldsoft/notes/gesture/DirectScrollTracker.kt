// app/src/main/java/com/wyldsoft/notes/gesture/DirectScrollTracker.kt
package com.wyldsoft.notes.gesture

import android.view.MotionEvent
import com.wyldsoft.notes.transform.ViewportTransformer

/**
 * Tracks finger movement directly for scrolling content.
 */
class DirectScrollTracker(
    private val viewportTransformer: ViewportTransformer
) {
    private var lastTouchY = 0f
    private var isScrolling = false

    fun processTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                isScrolling = true
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isScrolling) {
                    val deltaY = lastTouchY - event.y
                    viewportTransformer.scroll(deltaY, false)
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