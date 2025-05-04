package com.wyldsoft.notes.gesture

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope

import com.wyldsoft.notes.utils.convertDpToPixel
import com.wyldsoft.notes.transform.ViewportTransformer


///**
// * Custom gesture detector for the drawing surface with improved scrolling support.
// *
// * This improved version includes the actual scroll distance in the gesture callbacks.
// */
//class DrawingGestureDetector(
//    context: Context,
//    private val viewportTransformer: ViewportTransformer,
//    private val coroutineScope: CoroutineScope,
//    private val onGestureDetected: (String) -> Unit,
//    private val onScaleBegin: (Float, Float) -> Unit = { _, _ -> }, // Focal point X,Y
//    private val onScale: (Float) -> Unit = {}, // Scale factor delta
//    private val onScaleEnd: () -> Unit = {}
//) {
//    // Minimum distance required for a swipe gesture in dp
//    private val SWIPE_THRESHOLD_DP = 50.dp
//
//    // Minimum velocity required for a swipe gesture
//    private val SWIPE_VELOCITY_THRESHOLD = 100
//
//    // Time window for considering multi-touch gestures (in ms)
//    private val MULTI_TOUCH_TIMEOUT = 500L
//
//    // Time window for double tap detection (in ms)
//    // Note: using a slightly longer time for e-ink displays since they refresh slower
//    private val DOUBLE_TAP_TIMEOUT = 500L
//
//    // Convert dp to pixels for the current context
//    private val swipeThreshold = convertDpToPixel(SWIPE_THRESHOLD_DP, context)
//
//    // Track multi-touch events
//    private var lastPointerCount = 0
//    private var lastTapTime = 0L
//    private var lastDoubleTapTime = 0L
//    private var twoFingersTapCount = 0
//    private var threeFingersTapCount = 0
//    private var fourFingersTapCount = 0
//
//    // Track whether we're currently in a scale gesture to avoid conflicting with other gestures
//    private var isScaling = false
//
//    // Android's built-in gesture detector for basic gestures
//    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
//        override fun onDown(e: MotionEvent): Boolean {
//            println("gesture: onDown")
//            // Ignore stylus inputs
//            if (isStylusEvent(e)) {
//                return false
//            }
//            return true
//        }
//
//        override fun onDoubleTap(e: MotionEvent): Boolean {
//            // Ignore stylus inputs
//            if (isStylusEvent(e)) {
//                return false
//            }
//
//            // Don't trigger double tap during scaling
//            if (isScaling) {
//                return false
//            }
//
//            if (e.pointerCount == 1) {
//                onGestureDetected("Double tap detected")
//                return true
//            }
//            return false
//        }
//
//        override fun onFling(
//            e1: MotionEvent?,
//            e2: MotionEvent,
//            velocityX: Float,
//            velocityY: Float
//        ): Boolean {
//            if (e1 == null) return false
//
//            // Ignore stylus inputs
//            if (isStylusEvent(e1) || isStylusEvent(e2)) {
//                return false
//            }
//
//            // Don't trigger fling during scaling
//            if (isScaling) {
//                return false
//            }
//
//            val diffY = e2.y - e1.y
//            val diffX = e2.x - e1.x
//
//            // Check if the movement is more vertical than horizontal
//            if (Math.abs(diffY) > Math.abs(diffX) && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
//                if (Math.abs(diffY) > swipeThreshold) {
//                    if (diffY > 0) {
//                        // Swipe down - don't show notification for common navigation
//                        onGestureDetected("Swipe down detected")
//                    } else {
//                        // Swipe up - don't show notification for common navigation
//                        onGestureDetected("Swipe up detected")
//                    }
//                    return true
//                }
//            }
//            // Check if the movement is more horizontal than vertical
//            else if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
//                if (Math.abs(diffX) > swipeThreshold) {
//                    if (diffX > 0) {
//                        // Swipe right
//                        onGestureDetected("Swipe right detected")
//                    } else {
//                        // Swipe left
//                        onGestureDetected("Swipe left detected")
//                    }
//                    return true
//                }
//            }
//
//            return false
//        }
//
//        // IMPROVED SCROLLING: Pass the actual distance value in the gesture message
//        override fun onScroll(
//            e1: MotionEvent?,
//            e2: MotionEvent,
//            distanceX: Float,
//            distanceY: Float
//        ): Boolean {
//            println("scroll: Native onScroll")
//            if (e1 == null) return false
//
//            // Ignore stylus inputs
//            if (isStylusEvent(e1) || isStylusEvent(e2)) {
//                return false
//            }
//
//            // Don't handle scroll during scaling
//            if (isScaling) {
//                return false
//            }
//
//            // If primarily vertical motion, treat as vertical scroll
//            if (Math.abs(distanceY) > Math.abs(distanceX) * 1.5f) {
//                // Note: distanceY is inverted (positive means scrolling up)
//                // NEW: Send the actual distance as part of the message
//                onGestureDetected("Scroll:${distanceY}")
//
//                viewportTransformer.scroll(distanceY, false)
//                return true
//            }
//
//            return false
//        }
//    })
//
//    // Scale detector for pinch zoom
//    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
//        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
//            isScaling = true
//            onScaleBegin(detector.focusX, detector.focusY)
//            return true
//        }
//
//        override fun onScale(detector: ScaleGestureDetector): Boolean {
//            onScale(detector.scaleFactor)
//            return true
//        }
//
//        override fun onScaleEnd(detector: ScaleGestureDetector) {
//            isScaling = false
//            onScaleEnd()
//        }
//    })
//
//    /**
//     * Check if the event is from a stylus rather than a finger.
//     *
//     * @param event The motion event to check
//     * @return True if this is a stylus event, false otherwise
//     */
//    private fun isStylusEvent(event: MotionEvent): Boolean {
//        // Check all pointers in the event
//        for (i in 0 until event.pointerCount) {
//            // MotionEvent.TOOL_TYPE_STYLUS indicates stylus input
//            if (event.getToolType(i) == MotionEvent.TOOL_TYPE_STYLUS) {
//                return true
//            }
//        }
//        return false
//    }
//
//    /**
//     * Process touch events to detect gestures.
//     *
//     * @param event The motion event to process
//     * @return True if the event was consumed, false otherwise
//     */
//    fun onTouchEvent(event: MotionEvent): Boolean {
//        // Check if this is a stylus input - if so, ignore for gesture detection
//        val isStylusInput = isStylusEvent(event)
//        if (isStylusInput) {
//            return false
//        }
//
//        // Pass the event to the scale detector first
//        val scaleHandled = scaleGestureDetector.onTouchEvent(event)
//
//        // If we're scaling, we don't want other gestures to interfere
//        if (isScaling) {
//            return scaleHandled
//        }
//
//        // Process multi-touch events - do this first to catch multi-finger gestures
//        when (event.actionMasked) {
//            MotionEvent.ACTION_POINTER_DOWN -> {
//                println("pointer count ${event.pointerCount}")
//                // Track pointer count for multi-touch gestures
//                if (event.pointerCount == 2) {
//                    println("calling two finger gesture")
//                    handleTwoFingerGesture(event)
//                } else if (event.pointerCount == 3) {
//                    println("calling three finger gesture")
//                    handleThreeFingerGesture(event)
//                } else if (event.pointerCount == 4) {
//                    println("calling four finger gesture")
//                    handleFourFingerGesture(event)
//                }
//            }
//            MotionEvent.ACTION_UP -> {
//                lastPointerCount = 0
//            }
//        }
//
//        // Use Android's gesture detector for standard gestures
//        val result = gestureDetector.onTouchEvent(event)
//
//        return result || scaleHandled
//    }
//
//    /**
//     * Handle two-finger gestures including double-tap.
//     */
//    private fun handleTwoFingerGesture(event: MotionEvent) {
//        // Ignore if any pointer is a stylus
//        if (isStylusEvent(event)) return
//
//        val currentTime = System.currentTimeMillis()
//
//        if (event.pointerCount == 2) {
//            // We need to track when two fingers touch down
//            if (currentTime - lastTapTime < DOUBLE_TAP_TIMEOUT) {
//                twoFingersTapCount++
//
//                if (twoFingersTapCount == 2) {
//                    // We've detected a two-finger double tap!
//                    onGestureDetected("Two-finger double tap detected")
//
//                    // Reset the counter
//                    twoFingersTapCount = 0
//                    lastDoubleTapTime = currentTime
//                }
//            } else {
//                // Too much time has elapsed, so this is the first tap of a potential double tap
//                twoFingersTapCount = 1
//            }
//
//            // Update the last tap time
//            lastTapTime = currentTime
//        }
//    }
//
//    /**
//     * Handle three-finger gestures including double-tap.
//     */
//    private fun handleThreeFingerGesture(event: MotionEvent) {
//        // Ignore if any pointer is a stylus
//        if (isStylusEvent(event)) return
//
//        val currentTime = System.currentTimeMillis()
//
//        if (event.pointerCount == 3) {
//            // We need to track when three fingers touch down
//            if (currentTime - lastTapTime < DOUBLE_TAP_TIMEOUT) {
//                threeFingersTapCount++
//
//                if (threeFingersTapCount == 2) {
//                    // We've detected a three-finger double tap!
//                    onGestureDetected("Three-finger double tap detected")
//
//                    // Reset the counter
//                    threeFingersTapCount = 0
//                    lastDoubleTapTime = currentTime
//                }
//            } else {
//                // Too much time has elapsed, so this is the first tap of a potential double tap
//                threeFingersTapCount = 1
//            }
//
//            // Update the last tap time
//            lastTapTime = currentTime
//        }
//    }
//
//    /**
//     * Handle four-finger gestures including double-tap.
//     */
//    private fun handleFourFingerGesture(event: MotionEvent) {
//        // Ignore if any pointer is a stylus
//        if (isStylusEvent(event)) return
//
//        val currentTime = System.currentTimeMillis()
//
//        if (event.pointerCount == 4) {
//            // We need to track when four fingers touch down
//            if (currentTime - lastTapTime < DOUBLE_TAP_TIMEOUT) {
//                fourFingersTapCount++
//
//                if (fourFingersTapCount == 2) {
//                    // We've detected a four-finger double tap!
//                    onGestureDetected("Four-finger double tap detected")
//
//                    // Reset the counter
//                    fourFingersTapCount = 0
//                    lastDoubleTapTime = currentTime
//                }
//            } else {
//                // Too much time has elapsed, so this is the first tap of a potential double tap
//                fourFingersTapCount = 1
//            }
//
//            // Update the last tap time
//            lastTapTime = currentTime
//        }
//    }
//}



class DrawingGestureDetector(
    context: Context,
    private val viewportTransformer: ViewportTransformer,
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

    // Time window for allowing all fingers to make contact (in ms)
    private val FINGER_CONTACT_WINDOW = 150L

    // Time window for double tap detection (in ms)
    // Note: using a slightly longer time for e-ink displays since they refresh slower
    private val DOUBLE_TAP_TIMEOUT = 500L

    // Convert dp to pixels for the current context
    private val swipeThreshold = convertDpToPixel(SWIPE_THRESHOLD_DP, context)

    // Track gesture state
    private var isInGesture = false
    private var gestureStartTime = 0L
    private var maxPointerCount = 0
    private var lastGestureEndTime = 0L
    private var tapCount = 0
    private var lastTapFingerCount = 0

    // Track whether we're currently in a scale gesture to avoid conflicting with other gestures
    private var isScaling = false

    // Android's built-in gesture detector for basic gestures
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            // Only handle single finger gestures with GestureDetector
            // Multi-finger gestures will be handled separately
            println("gesture: onDown")
            if (e.pointerCount > 1 || isStylusEvent(e)) {
                return false
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            println("gesture: onDoubleTap")
            // Ignore stylus inputs
            if (isStylusEvent(e)) {
                return false
            }

            // Don't trigger double tap during scaling
            if (isScaling) {
                return false
            }

            // Only handle single finger double taps with GestureDetector
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
            println("gesture: onFling")
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

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            println("gesture: onScroll: Native onScroll")
            if (e1 == null) return false

            // Ignore stylus inputs
            if (isStylusEvent(e1) || isStylusEvent(e2)) {
                return false
            }

            // Don't handle scroll during scaling
            if (isScaling) {
                return false
            }

            // If primarily vertical motion, treat as vertical scroll
            if (Math.abs(distanceY) > Math.abs(distanceX) * 1.5f) {
                // Note: distanceY is inverted (positive means scrolling up)
                // NEW: Send the actual distance as part of the message
                onGestureDetected("Scroll:${distanceY}")

                viewportTransformer.scroll(distanceY, false)
                return true
            }

            return false
        }
    })

    // Scale detector for pinch zoom
    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            println("gesture: onScaleBegin")
            isScaling = true
            onScaleBegin(detector.focusX, detector.focusY)
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            println("gesture: onScale")
            onScale(detector.scaleFactor)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            println("gesture: onScaleEnd")
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

        // We'll handle the event in different ways depending on action
        val action = event.actionMasked
        val currentTime = System.currentTimeMillis()

        // Handle multi-finger gestures first, before delegating to other detectors
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                // First finger down - start tracking a new gesture
                isInGesture = true
                gestureStartTime = currentTime
                maxPointerCount = 1
                println("ACTION_DOWN: Starting new gesture")
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Additional finger down - update max pointer count
                val pointerCount = event.pointerCount
                println("ACTION_POINTER_DOWN: Pointer count: $pointerCount")

                if (isInGesture) {
                    // Always update max pointer count for this gesture
                    maxPointerCount = Math.max(maxPointerCount, pointerCount)
                    println("Updated max pointer count: $maxPointerCount")
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // One finger up but others remain
                println("ACTION_POINTER_UP: Pointer count remaining: ${event.pointerCount - 1}")
                // Continue tracking the gesture, don't reset anything
            }

            MotionEvent.ACTION_UP -> {
                // Last finger up - process the complete gesture
                println("ACTION_UP: All fingers up, max was: $maxPointerCount")

                if (isInGesture) {
                    // Check for tap gesture (simple or part of double tap)
                    val isQuickTap = currentTime - gestureStartTime < MULTI_TOUCH_TIMEOUT

                    if (isQuickTap) {
                        val timeSinceLastGesture = currentTime - lastGestureEndTime

                        if (timeSinceLastGesture < DOUBLE_TAP_TIMEOUT && maxPointerCount == lastTapFingerCount) {
                            // This could be part of a double tap with the same finger count
                            tapCount++

                            if (tapCount == 2) {
                                // We have a double tap with maxPointerCount fingers
                                handleMultiFingerDoubleTap(maxPointerCount)
                                tapCount = 0  // Reset for next gesture
                            }
                        } else {
                            // First tap of a potential double tap or different finger count
                            tapCount = 1
                            lastTapFingerCount = maxPointerCount
                        }

                        lastGestureEndTime = currentTime
                    } else {
                        // Not a tap gesture, reset tap count
                        tapCount = 0
                    }

                    // Reset gesture tracking
                    isInGesture = false
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                println("ACTION_CANCEL received but continuing gesture tracking")
                // Do NOT reset isInGesture here - we want to keep tracking
                // But we will consider this the end of a potential tap

                // Only if this is a full cancel (all pointers), then reset
                val possibleMultiTap = ((System.currentTimeMillis()-currentTime) < DOUBLE_TAP_TIMEOUT)
                if (!possibleMultiTap && event.pointerCount <= 1) {
                    println("Full cancel, resetting gesture event.pounterCount ${event.pointerCount}")
                    isInGesture = false
                    tapCount = 0
                }
            }
        }

        // For single finger gestures, pass to the standard gesture detector
        // but only if we're not in a multi-finger gesture
        var gestureHandled = false
        val timeNow = System.currentTimeMillis()
        if (((timeNow - gestureStartTime) > FINGER_CONTACT_WINDOW) && maxPointerCount <= 1) {
            println("gesture: Send to simple gesture timeNow - gestureStartTime ${timeNow-gestureStartTime}")
            gestureHandled = gestureDetector.onTouchEvent(event)
        }

        // Always pass to the scale detector, it handles its own state
        val scaleHandled = scaleGestureDetector.onTouchEvent(event)

        // If we're scaling, don't allow other gestures to interfere
        if (isScaling) {
            return scaleHandled
        }

        return gestureHandled || scaleHandled || isInGesture
    }

    /**
     * Handle a multi-finger double tap with the specified number of fingers.
     *
     * @param fingerCount Number of fingers used in the gesture
     */
    private fun handleMultiFingerDoubleTap(fingerCount: Int) {
        when (fingerCount) {
            2 -> onGestureDetected("Two-finger double tap detected")
            3 -> onGestureDetected("Three-finger double tap detected")
            4 -> onGestureDetected("Four-finger double tap detected")
            // Add more cases if needed
        }
    }
}