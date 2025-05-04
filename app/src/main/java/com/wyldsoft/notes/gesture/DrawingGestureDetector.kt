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
        println("gesture: action is $action")
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                // First finger down - start tracking a new gesture
                stopTapTimer() // don't emit gesture from earlier tap
                if (!isInGesture) {
                    gestureStartTime = currentTime
                }

                isInGesture = true
                maxPointerCount = 1
                println("ACTION_DOWN: Starting new gesture")
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Additional finger down - update max pointer count
                stopTapTimer() // don't emit gesture from earlier tap
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
                actionUpOrActionCancel(event, true, currentTime)
            }

            MotionEvent.ACTION_CANCEL -> {
                // Treat this the same as action up.
                // It is called when fingers are lifted off the ground so that
                // something like drawing with your finger is cancelled if you lift your finger
                actionUpOrActionCancel(event, false, currentTime)
            }
        }

        // For single finger gestures, pass to the standard gesture detector
        // but only if we're not in a multi-finger gesture
        var gestureHandled = false
        val timeNow = System.currentTimeMillis()
        if (((timeNow - gestureStartTime) > FINGER_CONTACT_WINDOW) && maxPointerCount <= 1) {

            gestureHandled = gestureDetector.onTouchEvent(event)
            println("gesture: Sent to simple gesture. Handled? $gestureHandled")
        }

        // Always pass to the scale detector, it handles its own state
        val scaleHandled = scaleGestureDetector.onTouchEvent(event)

        // If we're scaling, don't allow other gestures to interfere
        if (isScaling) {
            return scaleHandled
        }

        return gestureHandled || scaleHandled || isInGesture
    }

    private fun stopTapTimer() {
        //println("gesture: stopTapTimer")
        tapTimer?.cancel()
    }

    private fun startTapTimer() {
        // println("gesture: startTapTimer")
        tapTimer?.cancel() // Cancel existing timer before starting a new one
        tapTimer = object : CountDownTimer(TAP_TIMEOUT, 100) {
            override fun onTick(millisUntilFinished: Long) {
                // Dont need onTick
            }

            override fun onFinish() {
                //println("gesture: tap timer FINISHED tapCount $tapCount")
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
        // Last finger up - process the complete gesture
        println("ACTION_UP: All fingers up, max was: $maxPointerCount is act4ion up? $isActionUp")
        if (isInGesture) {
            //println("gesture: actionUpOrActionCancel isInGesture")
            val timeNow = System.currentTimeMillis()
            //println("gesture: times; $timeNow   $gestureStartTime")

            val isNotQuickTap = timeNow - gestureStartTime >FINGER_CONTACT_WINDOW
            // want to make sure it is not just another finger so we take time measurement
            if (isNotQuickTap) {
                tapCount++ // they tapped again
                // wait to see if they tap again
                startTapTimer()
            }
        } else{
            println("gesture: actionUpOrActionCancel NOT IN GESTURE")
        }
    }

    private fun resetMultiTap() {
        println("gesture: resetMultiTap")
        tapCount = 0
        isInGesture = false
        maxPointerCount = 0
    }

    /**
     * Handle a multi-finger double tap with the specified number of fingers.
     *
     * @param fingerCount Number of fingers used in the gesture
     */
    private fun handleMultiFingerDoubleTap(fingerCount: Int) {
        when (fingerCount) {
            2 -> onGestureDetected("gesture: Two-finger double tap detected")
            3 -> onGestureDetected("gesture: Three-finger double tap detected")
            4 -> onGestureDetected("gesture: Four-finger double tap detected")
            // Add more cases if needed
        }
    }

    private fun handleMultiFingerTripleTap(fingerCount: Int) {
        when (fingerCount) {
            2 -> onGestureDetected("gesture: Two-finger triple tap detected")
            3 -> onGestureDetected("gesture: Three-finger triple tap detected")
            4 -> onGestureDetected("gesture: Four-finger triple tap detected")
            // Add more cases if needed
        }
    }

}