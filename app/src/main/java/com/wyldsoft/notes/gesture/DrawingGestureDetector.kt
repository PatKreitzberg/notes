package com.wyldsoft.notes.gesture

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.utils.convertDpToPixel
import kotlinx.coroutines.CoroutineScope

/**
 * Custom gesture detector for the drawing surface with improved scrolling support.
 *
 * This improved version includes the actual scroll distance in the gesture callbacks.
 */
class DrawingGestureDetector(
    context: Context,
    private val coroutineScope: CoroutineScope,
    private val onGestureDetected: (String) -> Unit,
    private val onScaleBegin: (Float, Float) -> Unit = { _, _ -> }, // Focal point X,Y
    private val onScale: (Float) -> Unit = {}, // Scale factor delta
    private val onScaleEnd: () -> Unit = {}
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

    // Track whether we're currently in a scale gesture to avoid conflicting with other gestures
    private var isScaling = false

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

            // Don't trigger double tap during scaling
            if (isScaling) {
                return false
            }

            if (e.pointerCount == 1) {
                onGestureDetected("Double tap detected")
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

            // Don't trigger fling during scaling
            if (isScaling) {
                return false
            }

            val diffY = e2.y - e1.y
            val diffX = e2.x - e1.x

            // Check if the movement is more vertical than horizontal
            if (Math.abs(diffY) > Math.abs(diffX) && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                if (Math.abs(diffY) > swipeThreshold) {
                    if (diffY > 0) {
                        // Swipe down - don't show notification for common navigation
                        onGestureDetected("Swipe down detected")
                    } else {
                        // Swipe up - don't show notification for common navigation
                        onGestureDetected("Swipe up detected")
                    }
                    return true
                }
            }
            // Check if the movement is more horizontal than vertical
            else if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                if (Math.abs(diffX) > swipeThreshold) {
                    if (diffX > 0) {
                        // Swipe right
                        onGestureDetected("Swipe right detected")
                    } else {
                        // Swipe left
                        onGestureDetected("Swipe left detected")
                    }
                    return true
                }
            }

            return false
        }

        // IMPROVED SCROLLING: Pass the actual distance value in the gesture message
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (e1 == null) return false

            // Ignore stylus inputs
            if (isStylusEvent(e1) || isStylusEvent(e2)) {
                return false
            }

            // Don't handle scroll during scaling
            if (isScaling) {
                return false
            }

            // Don't interfere with two-finger scrolling
            if (e2.pointerCount >= 2) {
                return false
            }

            // If primarily vertical motion, treat as vertical scroll
            if (Math.abs(distanceY) > Math.abs(distanceX) * 1.5f) {
                // Note: distanceY is inverted (positive means scrolling up)
                // NEW: Send the actual distance as part of the message
                onGestureDetected("Scroll:${distanceY}")
                return true
            }

            return false
        }
    })

    // Scale detector for pinch zoom
    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            onScaleBegin(detector.focusX, detector.focusY)
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            onScale(detector.scaleFactor)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
            onScaleEnd()
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

        // Skip gesture detection for two-finger events, which we use for proportional scrolling
        if (event.pointerCount == 2) {
            // Let two-finger events be handled by the proportional scrolling
            return false
        }

        // Pass the event to the scale detector first
        val scaleHandled = scaleGestureDetector.onTouchEvent(event)

        // If we're scaling, we don't want other gestures to interfere
        if (isScaling) {
            return scaleHandled
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

        return result || scaleHandled
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
                    onGestureDetected("Two-finger double tap detected")

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
                    onGestureDetected("Three-finger double tap detected")

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
                    onGestureDetected("Four-finger double tap detected")

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
}