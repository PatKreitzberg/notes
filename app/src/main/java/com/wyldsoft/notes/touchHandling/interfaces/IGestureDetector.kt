package com.wyldsoft.notes.touchHandling.interfaces

import android.view.MotionEvent

/**
 * Interface for gesture detection operations
 */
interface IGestureDetector {
    /**
     * Processes touch events to detect gestures
     */
    fun onTouchEvent(event: MotionEvent): Boolean
}