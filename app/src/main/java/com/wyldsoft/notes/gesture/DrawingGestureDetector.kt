// app/src/main/java/com/wyldsoft/notes/gesture/DrawingGestureDetector.kt
package com.wyldsoft.notes.gesture

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.transform.ViewportTransformer
import com.wyldsoft.notes.utils.convertDpToPixel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Comprehensive gesture detector for the drawing surface.
 * Handles scrolling, swiping, tap detection, and scaling.
 */
class DrawingGestureDetector(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val viewportTransformer: ViewportTransformer,
    private val onScrollComplete: () -> Unit = {}
) {
    // Minimum distance required for a swipe gesture in dp
    private val SWIPE_THRESHOLD_DP = 50.dp

    // Minimum velocity required for a swipe gesture
    private val SWIPE_VELOCITY_THRESHOLD = 100

    // Time window for double tap detection (in ms)
    // Note: using a slightly longer time for e-ink displays since they refresh slower
    private val DOUBLE_TAP_TIMEOUT = 500L

    // Convert dp to pixels for the current context
    private val swipeThreshold = convertDpToPixel(SWIPE_THRESHOLD_DP, context)

    // Emit detected gestures through this flow
    val gestureDetected = MutableSharedFlow<GestureEvent>()

    // For continuous movement updates
    val gestureMoved = MutableSharedFlow<GestureEvent>()

    // Track touch state
    private var isScrolling = false
    private var lastTouchY = 0f
    private var startX = 0f
    private var startY = 0f
    private var startTime = 0L
    private var lastMoveY = 0f
    private var lastMoveTime = 0L
    private var isTracking = false

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
                emitGesture(GestureType.DOUBLE_TAP, e.x, e.y, e.x, e.y, 0)
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
            val duration = e2.eventTime - e1.eventTime

            // Check if the movement is more vertical than horizontal
            if (abs(diffY) > abs(diffX) && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                if (abs(diffY) > swipeThreshold) {
                    val type = if (diffY > 0) {
                        if (abs(velocityY) > SWIPE_VELOCITY_THRESHOLD * 2) {
                            GestureType.SWIPE_DOWN_FAST
                        } else {
                            GestureType.SWIPE_DOWN_SLOW
                        }
                    } else {
                        if (abs(velocityY) > SWIPE_VELOCITY_THRESHOLD * 2) {
                            GestureType.SWIPE_UP_FAST
                        } else {
                            GestureType.SWIPE_UP_SLOW
                        }
                    }

                    // Calculate scroll amount and apply it
                    val scrollAmount = viewportTransformer.calculateScrollAmount(e1.y, e2.y, duration)
                    viewportTransformer.scroll(scrollAmount, true)

                    emitGesture(type, e1.x, e1.y, e2.x, e2.y, duration)
                    return true
                }
            }
            // Check if the movement is more horizontal than vertical
            else if (abs(diffX) > abs(diffY) && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                if (abs(diffX) > swipeThreshold) {
                    val type = if (diffX > 0) GestureType.SWIPE_RIGHT else GestureType.SWIPE_LEFT
                    emitGesture(type, e1.x, e1.y, e2.x, e2.y, duration)
                    return true
                }
            }

            return false
        }

        // Handle scrolling
//        override fun onScroll(
//            e1: MotionEvent?,
//            e2: MotionEvent,
//            distanceX: Float,
//            distanceY: Float
//        ): Boolean {
//            if (e1 == null) return false
//            println("on scroll")
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
//            // Don't interfere with two-finger scrolling
//            if (e2.pointerCount >= 2) {
//                return false
//            }
//
//            // If primarily vertical motion, treat as vertical scroll
//            if (abs(distanceY) > abs(distanceX) * 1.5f) {
//                println("scroll: e1 $e1   e2 $e2  distanceY $distanceY")
//                // The distanceY is inverted in the gesture detector
//                viewportTransformer.scroll(-distanceY, false)
//
//                // Emit continuous movement event
//                emitGesture(GestureType.FINGER_MOVE, e1.x, e1.y, e2.x, e2.y, e2.eventTime - e1.eventTime)
//                return true
//            }
//
//            return false
//        }
    })

    /**
     * Scale detector for pinch zoom (placeholder for future implementation)
     */
    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // Zoom would be implemented here
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
        }
    })

    /**
     * Process touch events to detect gestures.
     *
     * @param event The motion event to process
     * @return True if the event was consumed, false otherwise
     */
    fun processTouchEvent(event: MotionEvent): Boolean {
        // Ignore stylus inputs
        if (isStylusEvent(event)) {
            return false
        }

        // Skip multi-touch for now
        if (event.pointerCount > 1) {
            return false
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Start tracking
                lastTouchY = event.y
                startX = event.x
                startY = event.y
                startTime = System.currentTimeMillis()
                isScrolling = true
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isScrolling) {
                    // Direct scroll handling - simple and efficient
                    val deltaY = lastTouchY - event.y
                    viewportTransformer.scroll(deltaY, false)
                    lastTouchY = event.y

                    // Emit move event for other components
                    coroutineScope.launch {
                        gestureMoved.emit(GestureEvent(
                            type = GestureType.FINGER_MOVE,
                            startPoint = GesturePoint(startX, startY),
                            endPoint = GesturePoint(event.x, event.y),
                            duration = System.currentTimeMillis() - startTime
                        ))
                    }
                    return true
                }
                return false
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isScrolling) {
                    // Detect gestures on release
                    val endTime = System.currentTimeMillis()
                    val deltaY = event.y - startY
                    val deltaX = event.x - startX
                    val duration = endTime - startTime

                    // Apply inertial scroll for swipes
                    if (Math.abs(deltaY) > Math.abs(deltaX) && Math.abs(deltaY) > swipeThreshold) {
                        // Calculate scroll with inertia
                        val scrollAmount = viewportTransformer.calculateScrollAmount(
                            startY, event.y, duration
                        )
                        viewportTransformer.scroll(scrollAmount, true)

                        // Determine gesture type
                        val gestureType = if (deltaY < 0) {
                            // Moving finger up = swipe up
                            if (duration < 300) GestureType.SWIPE_UP_FAST else GestureType.SWIPE_UP_SLOW
                        } else {
                            // Moving finger down = swipe down
                            if (duration < 300) GestureType.SWIPE_DOWN_FAST else GestureType.SWIPE_DOWN_SLOW
                        }

                        // Emit gesture
                        coroutineScope.launch {
                            gestureDetected.emit(GestureEvent(
                                type = gestureType,
                                startPoint = GesturePoint(startX, startY),
                                endPoint = GesturePoint(event.x, event.y),
                                duration = duration
                            ))
                        }
                    }

                    // Call completion callback
                    onScrollComplete()
                    isScrolling = false
                    return true
                }
                return false
            }
        }
        return false
    }

    /**
     * Track continuous movement for scrolling feedback
     */
    private fun trackMovement(event: MotionEvent) {
        val currentY = event.y
        val currentTime = System.currentTimeMillis()

        // Only process if we've moved a minimum distance or time has passed
        val minMoveDistance = 5f // Minimum pixels to move before triggering
        val minMoveTime = 16L // ~60fps timing

        if (abs(currentY - lastMoveY) > minMoveDistance ||
            currentTime - lastMoveTime > minMoveTime) {

            val moveEvent = GestureEvent(
                type = GestureType.FINGER_MOVE,
                startPoint = GesturePoint(startX, startY),
                endPoint = GesturePoint(event.x, currentY),
                duration = currentTime - startTime
            )

            // Emit via coroutines
            coroutineScope.launch {
                gestureMoved.emit(moveEvent)
            }

            // Update last position and time
            lastMoveY = currentY
            lastMoveTime = currentTime
        }
    }

    private fun startTracking(event: MotionEvent) {
        startX = event.x
        startY = event.y
        lastMoveY = startY
        startTime = System.currentTimeMillis()
        lastMoveTime = startTime
        isTracking = true
    }

    private fun stopTracking() {
        isTracking = false
    }

    /**
     * Check if the event is from a stylus rather than a finger.
     */
    private fun isStylusEvent(event: MotionEvent): Boolean {
        for (i in 0 until event.pointerCount) {
            if (event.getToolType(i) == MotionEvent.TOOL_TYPE_STYLUS) {
                return true
            }
        }
        return false
    }

    /**
     * Detect gesture based on the end of a touch sequence
     */
    private fun detectGesture(event: MotionEvent) {
        val endX = event.x
        val endY = event.y
        val endTime = System.currentTimeMillis()

        val deltaX = endX - startX
        val deltaY = endY - startY
        val duration = endTime - startTime

        // Convert to dp for consistent behavior across devices
        val deltaYDp = convertDpToPixel(abs(deltaY).dp, context)
        val minDistancePx = convertDpToPixel(SWIPE_THRESHOLD_DP, context)

        // Calculate velocity in dp per ms
        val velocityY = if (duration > 0) deltaYDp / duration else 0f
        val isFast = abs(velocityY) >= 0.5f // Threshold for fast swipe

        // Detect vertical swipes
        if (abs(deltaY) > abs(deltaX)) { // Vertical gesture
            if (deltaYDp >= minDistancePx) {
                if (deltaY < 0) { // Swipe Up
                    val gesture = if (isFast) GestureType.SWIPE_UP_FAST else GestureType.SWIPE_UP_SLOW
                    emitGesture(gesture, startX, startY, endX, endY, duration)
                } else { // Swipe Down
                    val gesture = if (isFast) GestureType.SWIPE_DOWN_FAST else GestureType.SWIPE_DOWN_SLOW
                    emitGesture(gesture, startX, startY, endX, endY, duration)
                }
            }
        }
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
                    emitGesture(GestureType.TWO_FINGER_DOUBLE_TAP,
                        event.x, event.y, event.x, event.y, 0)

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
                    emitGesture(GestureType.THREE_FINGER_DOUBLE_TAP,
                        event.x, event.y, event.x, event.y, 0)

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
                    emitGesture(GestureType.FOUR_FINGER_DOUBLE_TAP,
                        event.x, event.y, event.x, event.y, 0)

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
     * Emit a gesture event through the shared flow
     */
    private fun emitGesture(
        type: GestureType,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long
    ) {
        val event = GestureEvent(
            type = type,
            startPoint = GesturePoint(startX, startY),
            endPoint = GesturePoint(endX, endY),
            duration = duration
        )

        // Emit the event via coroutines
        coroutineScope.launch {
            gestureDetected.emit(event)
        }
    }
}

// Expanded GestureType enum to include all supported gestures
enum class GestureType {
    SWIPE_UP_FAST,
    SWIPE_UP_SLOW,
    SWIPE_DOWN_FAST,
    SWIPE_DOWN_SLOW,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    FINGER_MOVE,
    DOUBLE_TAP,
    TWO_FINGER_DOUBLE_TAP,
    THREE_FINGER_DOUBLE_TAP,
    FOUR_FINGER_DOUBLE_TAP
}

// These data classes are kept the same as in the original GestureDetector
data class GesturePoint(val x: Float, val y: Float)

data class GestureEvent(
    val type: GestureType,
    val startPoint: GesturePoint,
    val endPoint: GesturePoint,
    val duration: Long
)