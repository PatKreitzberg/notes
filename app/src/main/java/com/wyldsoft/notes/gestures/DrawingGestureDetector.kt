package com.wyldsoft.notes.gestures

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.utils.convertDpToPixel
import kotlinx.coroutines.CoroutineScope

/**
 * Custom gesture detector for the drawing surface.
 *
 * Detects common gestures like:
 * - Double tap (single finger)
 * - Double tap (two fingers)
 * - Double tap (three fingers)
 * - Double tap (four fingers)
 * - Swipe up
 * - Swipe down
 * - Swipe left
 * - Swipe right
 */
class DrawingGestureDetector(
    context: Context,
    private val coroutineScope: CoroutineScope,
    private val onGestureDetected: (String) -> Unit
) {
    // Minimum distance required for a swipe gesture in dp
    private val SWIPE_THRESHOLD_DP = 50.dp

    // Minimum velocity required for a swipe gesture
    private val SWIPE_VELOCITY_THRESHOLD = 100

    // Time window for considering multi-touch gestures (in ms)
    private val MULTI_TOUCH_TIMEOUT = 500L

    // Time window for double tap detection (in ms)
    // Note: using a slightly longer time for e-ink displays since they refresh slower
    private val DOUBLE_TAP_TIMEOUT = 500L

    // Convert dp to pixels for the current context
    private val swipeThreshold = convertDpToPixel(SWIPE_THRESHOLD_DP, context)

    // Track multi-touch events
    private var lastPointerCount = 0
    private var lastTapTime = 0L
    private var lastDoubleTapTime = 0L
    private var twoFingersTapCount = 0
    private var threeFingersTapCount = 0
    private var fourFingersTapCount = 0

    // Android's built-in gesture detector for basic gestures
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            // Ignore stylus inputs
            if (isStylusEvent(e)) {
                return false
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Ignore stylus inputs
            if (isStylusEvent(e)) {
                return false
            }

            if (e.pointerCount == 1) {
                showGestureNotification("Double tap detected")
                return true
            }
            return false
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false

            // Ignore stylus inputs
            if (isStylusEvent(e1) || isStylusEvent(e2)) {
                return false
            }

            val diffY = e2.y - e1.y
            val diffX = e2.x - e1.x

            // Check if the movement is more vertical than horizontal
            if (Math.abs(diffY) > Math.abs(diffX) && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                if (Math.abs(diffY) > swipeThreshold) {
                    if (diffY > 0) {
                        // Swipe down
                        showGestureNotification("Swipe down detected")
                    } else {
                        // Swipe up
                        showGestureNotification("Swipe up detected")
                    }
                    return true
                }
            }
            // Check if the movement is more horizontal than vertical
            else if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                if (Math.abs(diffX) > swipeThreshold) {
                    if (diffX > 0) {
                        // Swipe right
                        showGestureNotification("Swipe right detected")
                    } else {
                        // Swipe left
                        showGestureNotification("Swipe left detected")
                    }
                    return true
                }
            }

            return false
        }
    })


    /**
     * Check if the event is from a stylus rather than a finger.
     *
     * @param event The motion event to check
     * @return True if this is a stylus event, false otherwise
     */
    private fun isStylusEvent(event: MotionEvent): Boolean {
        // Check all pointers in the event
        for (i in 0 until event.pointerCount) {
            // MotionEvent.TOOL_TYPE_STYLUS indicates stylus input
            if (event.getToolType(i) == MotionEvent.TOOL_TYPE_STYLUS) {
                return true
            }
        }
        return false
    }

    /**
     * Process touch events to detect gestures.
     *
     * @param event The motion event to process
     * @return True if the event was consumed, false otherwise
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        // Check if this is a stylus input - if so, ignore for gesture detection
        val isStylusInput = isStylusEvent(event)
        if (isStylusInput) {
            return false
        }

        // Process multi-touch events - do this first to catch multi-finger gestures
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Track pointer count for multi-touch gestures
                if (event.pointerCount == 2) {
                    handleTwoFingerGesture(event)
                } else if (event.pointerCount == 3) {
                    handleThreeFingerGesture(event)
                } else if (event.pointerCount == 4) {
                    handleFourFingerGesture(event)
                }
            }
            MotionEvent.ACTION_UP -> {
                lastPointerCount = 0
            }
        }

        // Use Android's gesture detector for standard gestures
        val result = gestureDetector.onTouchEvent(event)

        return result
    }

    /**
     * Handle two-finger gestures including double-tap.
     */
    private fun handleTwoFingerGesture(event: MotionEvent) {
        // Ignore if any pointer is a stylus
        if (isStylusEvent(event)) return

        val currentTime = System.currentTimeMillis()

        if (event.pointerCount == 2) {
            // We need to track when two fingers touch down
            if (currentTime - lastTapTime < DOUBLE_TAP_TIMEOUT) {
                twoFingersTapCount++

                if (twoFingersTapCount == 2) {
                    // We've detected a two-finger double tap!
                    showGestureNotification("Two-finger double tap detected")

                    // Reset the counter
                    twoFingersTapCount = 0
                    lastDoubleTapTime = currentTime
                }
            } else {
                // Too much time has elapsed, so this is the first tap of a potential double tap
                twoFingersTapCount = 1
            }

            // Update the last tap time
            lastTapTime = currentTime
        }
    }

    /**
     * Handle three-finger gestures including double-tap.
     */
    private fun handleThreeFingerGesture(event: MotionEvent) {
        // Ignore if any pointer is a stylus
        if (isStylusEvent(event)) return

        val currentTime = System.currentTimeMillis()

        if (event.pointerCount == 3) {
            // We need to track when three fingers touch down
            if (currentTime - lastTapTime < DOUBLE_TAP_TIMEOUT) {
                threeFingersTapCount++

                if (threeFingersTapCount == 2) {
                    // We've detected a three-finger double tap!
                    showGestureNotification("Three-finger double tap detected")

                    // Reset the counter
                    threeFingersTapCount = 0
                    lastDoubleTapTime = currentTime
                }
            } else {
                // Too much time has elapsed, so this is the first tap of a potential double tap
                threeFingersTapCount = 1
            }

            // Update the last tap time
            lastTapTime = currentTime
        }
    }

    /**
     * Handle four-finger gestures including double-tap.
     */
    private fun handleFourFingerGesture(event: MotionEvent) {
        // Ignore if any pointer is a stylus
        if (isStylusEvent(event)) return

        val currentTime = System.currentTimeMillis()

        if (event.pointerCount == 4) {
            // We need to track when four fingers touch down
            if (currentTime - lastTapTime < DOUBLE_TAP_TIMEOUT) {
                fourFingersTapCount++

                if (fourFingersTapCount == 2) {
                    // We've detected a four-finger double tap!
                    showGestureNotification("Four-finger double tap detected")

                    // Reset the counter
                    fourFingersTapCount = 0
                    lastDoubleTapTime = currentTime
                }
            } else {
                // Too much time has elapsed, so this is the first tap of a potential double tap
                fourFingersTapCount = 1
            }

            // Update the last tap time
            lastTapTime = currentTime
        }
    }

    /**
     * Display a notification for the detected gesture.
     */
    private fun showGestureNotification(message: String) {
        onGestureDetected(message)
    }
}