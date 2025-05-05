package com.wyldsoft.notes.gesture

import android.content.Context
import android.os.CountDownTimer
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope

import com.wyldsoft.notes.utils.convertDpToPixel
import com.wyldsoft.notes.transform.ViewportTransformer

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

    private val SCROLL_THRESHOLD = convertDpToPixel(10.dp, context)

    // Minimum velocity required for a swipe gesture
    private val SWIPE_VELOCITY_THRESHOLD = 100

    // Time window for allowing all fingers to make contact (in ms)
    private val FINGER_CONTACT_WINDOW = 150L

    // Time window for double tap detection (in ms)
    // Note: using a slightly longer time for e-ink displays since they refresh slower
    private val TAP_TIMEOUT = 300L

    // Convert dp to pixels for the current context
    private val swipeThreshold = convertDpToPixel(SWIPE_THRESHOLD_DP, context)
    private var tapTimer: CountDownTimer? = null

    // Track gesture state
    private var isInGesture = false
    private var gestureStartTime = 0L
    private var maxPointerCount = 0
    private var tapCount = 0
    private var countedFirstTap = false
    private var lastTouchY = 0f
    private var isScrolling = false
    private var initialY = 0f


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
            MotionEvent.ACTION_MOVE -> {
                if (!isScrolling) {
                    var deltaY = initialY - event.y
                    if (kotlin.math.abs(deltaY) > SCROLL_THRESHOLD) {
                        isScrolling = true
                        viewportTransformer.scroll(deltaY, true)
                        lastTouchY = event.y
                    }
                } else {
                    var deltaY = lastTouchY - event.y
                    viewportTransformer.scroll(deltaY, true)
                    lastTouchY = event.y
                }
            }
            MotionEvent.ACTION_DOWN -> {
                // First finger down - start tracking a new gesture
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

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Additional finger down - update max pointer count
                stopTapTimer() // don't emit gesture from earlier tap
                val pointerCount = event.pointerCount
                if (isInGesture) {
                    // Always update max pointer count for this gesture
                    maxPointerCount = Math.max(maxPointerCount, pointerCount)
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // One finger up but others remain
                // Continue tracking the gesture, don't reset anything
            }

            MotionEvent.ACTION_UP -> {
                actionUpOrActionCancel(event, true, currentTime)
            }

            MotionEvent.ACTION_CANCEL -> {
                // Treat this the same as action up.
                // It is called when fingers are lifted off the ground so that
                // something like drawing with your finger is cancelled if you lift your finger
                actionUpOrActionCancel(event, false, currentTime)
            }
        }
        return isInGesture
    }

    private fun stopTapTimer() {
        tapTimer?.cancel()
    }

    private fun startTapTimer() {
        tapTimer?.cancel() // Cancel existing timer before starting a new one
        tapTimer = object : CountDownTimer(TAP_TIMEOUT, 100) {
            override fun onTick(millisUntilFinished: Long) {
                // Dont need onTick
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

            val isNotQuickTap = timeNow - gestureStartTime >FINGER_CONTACT_WINDOW
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
        when (fingerCount) {
            1 -> onGestureDetected("gesture: Single-finger double tap detected")
            2 -> onGestureDetected("gesture: Two-finger double tap detected")
            3 -> onGestureDetected("gesture: Three-finger double tap detected")
            4 -> onGestureDetected("gesture: Four-finger double tap detected")
            // Add more cases if needed
        }
    }

    private fun handleMultiFingerTripleTap(fingerCount: Int) {
        when (fingerCount) {
            1 -> onGestureDetected("gesture: Single-finger triple tap detected")
            2 -> onGestureDetected("gesture: Two-finger triple tap detected")
            3 -> onGestureDetected("gesture: Three-finger triple tap detected")
            4 -> onGestureDetected("gesture: Four-finger triple tap detected")
            // Add more cases if needed
        }
    }
}