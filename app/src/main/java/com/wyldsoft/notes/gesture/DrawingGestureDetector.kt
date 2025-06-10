package com.wyldsoft.notes.gesture

import android.content.Context
import android.os.CountDownTimer
import android.util.Log
import android.view.MotionEvent
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlin.math.sqrt

import com.wyldsoft.notes.utils.convertDpToPixel
import com.wyldsoft.notes.refreshingScreen.ViewportTransformer

class DrawingGestureDetector(
    context: Context,
    private val viewportTransformer: ViewportTransformer,
    private val coroutineScope: CoroutineScope,
    private val onGestureDetected: (String) -> Unit
) {
    // Minimum distance required for a swipe gesture in dp
    private val SWIPE_THRESHOLD_DP = 50.dp

    private val SCROLL_THRESHOLD = convertDpToPixel(10.dp, context)

    // Minimum velocity required for a swipe gesture
    private val SWIPE_VELOCITY_THRESHOLD = 100

    // Time window for allowing all fingers to make contact (in ms)
    private val FINGER_CONTACT_WINDOW = 150L

    // Time window for double tap detection (in ms)
    // Note: using a slightly longer time for e-ink displays since they refresh slower
    private val TAP_TIMEOUT = 200L

    // Convert dp to pixels for the current context
    private val swipeThreshold = convertDpToPixel(SWIPE_THRESHOLD_DP, context)
    private var tapTimer: CountDownTimer? = null

    // Create the gesture handler
    private val gestureHandler = GestureHandler(context, coroutineScope, viewportTransformer)

    // Track gesture state
    private var isInGesture = false
    private var gestureStartTime = 0L
    private var maxPointerCount = 0
    private var tapCount = 0
    private var countedFirstTap = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isScrolling = false
    private var initialX = 0f
    private var initialY = 0f

    // Custom pinch-to-zoom tracking variables
    private var isZooming = false
    private var initialDistance = 0f
    private var initialScale = 1.0f
    private var activePointerId1 = -1
    private var activePointerId2 = -1
    private var focusX = 0f
    private var focusY = 0f

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
     * Calculate the distance between two pointers
     */
    private fun getDistance(event: MotionEvent, pointerIndex1: Int, pointerIndex2: Int): Float {
        val x1 = event.getX(pointerIndex1)
        val y1 = event.getY(pointerIndex1)
        val x2 = event.getX(pointerIndex2)
        val y2 = event.getY(pointerIndex2)

        return sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
    }

    /**
     * Calculate the focus point (midpoint) between two pointers
     */
    private fun getFocusPoint(event: MotionEvent, pointerIndex1: Int, pointerIndex2: Int): Pair<Float, Float> {
        val x1 = event.getX(pointerIndex1)
        val y1 = event.getY(pointerIndex1)
        val x2 = event.getX(pointerIndex2)
        val y2 = event.getY(pointerIndex2)

        return Pair((x1 + x2) / 2f, (y1 + y2) / 2f)
    }

    /**
     * Handle the start of a pinch gesture
     */
    private fun startPinch(event: MotionEvent) {
        // We need exactly two pointers to start pinch
        if (event.pointerCount != 2) return

        isZooming = true

        // Store the pointer IDs
        activePointerId1 = event.getPointerId(0)
        activePointerId2 = event.getPointerId(1)

        // Calculate the initial distance between pointers
        val pointerIndex1 = event.findPointerIndex(activePointerId1)
        val pointerIndex2 = event.findPointerIndex(activePointerId2)

        initialDistance = getDistance(event, pointerIndex1, pointerIndex2)
        initialScale = viewportTransformer.zoomScale

        // Store the focus point (center of the pinch)
        val (x, y) = getFocusPoint(event, pointerIndex1, pointerIndex2)
        focusX = x
        focusY = y

        println("Pinch started: distance=$initialDistance, scale=$initialScale, focus=($focusX, $focusY)")
    }

    /**
     * Handle pinch movement
     */
    private fun handlePinch(event: MotionEvent) {
        if (!isZooming) return

        // Find pointer indices
        val pointerIndex1 = event.findPointerIndex(activePointerId1)
        val pointerIndex2 = event.findPointerIndex(activePointerId2)

        // Make sure we have both pointers
        if (pointerIndex1 == -1 || pointerIndex2 == -1) {
            isZooming = false
            return
        }

        // Calculate the current distance
        val currentDistance = getDistance(event, pointerIndex1, pointerIndex2)

        // Calculate new scale factor
        val scaleFactor = currentDistance / initialDistance
        val newScale = initialScale * scaleFactor

        // Update focus point
        val (x, y) = getFocusPoint(event, pointerIndex1, pointerIndex2)
        focusX = x
        focusY = y

        // Apply the zoom
        viewportTransformer.zoom(newScale, focusX, focusY)

        // Notify gesture handler about the zoom
        onGestureDetected("gesture: Pinch zoom detected")
    }

    /**
     * End the pinch gesture
     */
    private fun endPinch() {
        if (isZooming) {
            isZooming = false
            activePointerId1 = -1
            activePointerId2 = -1
            println("Pinch ended: final scale=${viewportTransformer.zoomScale}")
        }
    }

    private fun xyDistance(x: Float, y: Float): Float {
        return sqrt((x*x) + (y*y))
    }

    /**
     * Process touch events to detect gestures.
     *
     * @param event The motion event to process
     * @return True if the event was consumed, false otherwise
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        fun updateScrollingVars(deltaX: Float, deltaY: Float) {
            viewportTransformer.scroll(deltaX, deltaY)
            lastTouchX = event.x
            lastTouchY = event.y
        }



        // Check if this is a stylus input - if so, ignore for gesture detection
        val isStylusInput = isStylusEvent(event)
        if (isStylusInput) {
            return false
        }

        // We'll handle the event in different ways depending on action
        val action = event.actionMasked
        val currentTime = System.currentTimeMillis()

        // Handle pinch-to-zoom gestures
        when (action) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                Log.d("GestureDetector", "ACTION POINTER DOWN")
                // Start pinch when second pointer is down
                if (event.pointerCount == 2) {
                    startPinch(event)
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                Log.d("GestureDetector", "ACTION MOVE")
                // Handle pinch movement
                if (isZooming) {
                    handlePinch(event)
                    return true
                }

                // Handle scrolling if not zooming
                if (!isZooming) {
                    var deltaX = lastTouchX - event.x
                    var deltaY = lastTouchY - event.y

                    if (!isScrolling) {
                        if (xyDistance(deltaX, deltaY) > SCROLL_THRESHOLD) {
                            isScrolling = true
                            updateScrollingVars(deltaX, deltaY)
                        }
                    } else {
                        updateScrollingVars(deltaX, deltaY)
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                Log.d("GestureDetector", "ACTION POINTER UP")
                // Check if one of our active pointers is going up
                val pointerId = event.getPointerId(event.actionIndex)
                if (pointerId == activePointerId1 || pointerId == activePointerId2) {
                    endPinch()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                Log.d("GestureDetector", "ACTION UP CANCEL")
                // End any ongoing pinch
                endPinch()
                actionUpOrActionCancel(event, action == MotionEvent.ACTION_UP, currentTime)
            }

            MotionEvent.ACTION_DOWN -> {
                Log.d("GestureDetector", "ACTION DOWN")
                // First finger down - start tracking a new gesture
                lastTouchX = event.x
                initialX = event.x
                lastTouchY = event.y
                initialY = event.y
                if (!isInGesture) {
                    gestureStartTime = currentTime
                    tapCount++
                    countedFirstTap = true
                } else {
                    // Could be single finger triple tap
                    stopTapTimer() // don't emit gesture from earlier tap
                }

                isInGesture = true
                maxPointerCount = 1
            }
        }

        return isInGesture || isZooming
    }

    private fun stopTapTimer() {
        tapTimer?.cancel()
    }

    private fun startTapTimer() {
        tapTimer?.cancel() // Cancel existing timer before starting a new one
        tapTimer = object : CountDownTimer(TAP_TIMEOUT, 100) {
            override fun onTick(millisUntilFinished: Long) {
                // Don't need onTick
            }

            override fun onFinish() {
                if (tapCount == 2) {
                    handleMultiFingerDoubleTap(maxPointerCount)
                } else if (tapCount > 2){
                    handleMultiFingerTripleTap(maxPointerCount)
                }
                resetMultiTap()
            }
        }.start()
    }

    private fun actionUpOrActionCancel(event: MotionEvent, isActionUp: Boolean, currentTime: Long) {
        isScrolling = false
        // Last finger up - process the complete gesture
        if (isInGesture) {
            val timeNow = System.currentTimeMillis()

            val isNotQuickTap = timeNow - gestureStartTime > FINGER_CONTACT_WINDOW
            // want to make sure it is not just another finger so we take time measurement
            if (isNotQuickTap) {
                tapCount++ // they tapped again
                // wait to see if they tap again
                startTapTimer()
            }
        }
    }

    private fun resetMultiTap() {
        tapCount = 0
        isInGesture = false
        maxPointerCount = 0
        countedFirstTap = false
    }

    /**
     * Handle a multi-finger double tap with the specified number of fingers.
     *
     * @param fingerCount Number of fingers used in the gesture
     */
    private fun handleMultiFingerDoubleTap(fingerCount: Int) {
        val gesture = when (fingerCount) {
            1 -> "gesture: Single-finger double tap detected"
            2 -> "gesture: Two-finger double tap detected"
            3 -> "gesture: Three-finger double tap detected"
            4 -> "gesture: Four-finger double tap detected"
            else -> "gesture: Unknown double tap detected"
        }

        onGestureDetected(gesture)
        // Pass to gesture handler
        gestureHandler.handleGesture(gesture)
    }

    private fun handleMultiFingerTripleTap(fingerCount: Int) {
        val gesture = when (fingerCount) {
            1 -> "gesture: Single-finger triple tap detected"
            2 -> "gesture: Two-finger triple tap detected"
            3 -> "gesture: Three-finger triple tap detected"
            4 -> "gesture: Four-finger triple tap detected"
            else -> "gesture: Unknown triple tap detected"
        }

        onGestureDetected(gesture)
        // Pass to gesture handler
        gestureHandler.handleGesture(gesture)
    }

    // Expose the gesture handler to allow configuration
    fun getGestureHandler(): GestureHandler {
        return gestureHandler
    }
}