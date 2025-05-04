package com.wyldsoft.notes.gesture

import android.content.Context
import android.view.MotionEvent
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs

import com.wyldsoft.notes.utils.convertDpToPixel

/**
 * Detects and classifies touch gestures on the canvas.
 * Supports distinguishing between different types of finger gestures.
 */
class GestureDetector(private val context: Context) {
    // Minimum distance to be considered a swipe (in dp)
    private val minSwipeDistance = 40.dp

    // Minimum velocity to be considered a fast swipe (dp per millisecond)
    private val minSwipeVelocity = 0.5f

    // Emit detected gestures through this flow
    val gestureDetected = MutableSharedFlow<GestureEvent>()

    // Coroutine scope for emitting events
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    // scroll
    val gestureMoved = MutableSharedFlow<GestureEvent>() // New event for moves
    private var lastMoveY: Float = 0f
    private var lastMoveTime: Long = 0


    // Track gesture state
    private var startX: Float = 0f
    private var startY: Float = 0f
    private var startTime: Long = 0
    private var isTracking = false

    // Process touch events
    fun processTouchEvent(event: MotionEvent): Boolean {
        println("gesture processTouchEvent: ${event.action}")
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startTracking(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isTracking) {
                    trackMovement(event)
                    return true // Now we consume move events for scrolling
                }
                return false
            }
            MotionEvent.ACTION_UP -> {
                if (isTracking) {
                    detectGesture(event)
                    stopTracking()
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                stopTracking()
            }
        }
        return false
    }

    private fun trackMovement(event: MotionEvent) {
        println("scroll trackMovement")
        val currentY = event.y
        val currentTime = System.currentTimeMillis()

        // Only process if we've moved a minimum distance or time has passed
        val minMoveDistance = 5f // Minimum pixels to move before triggering
        val minMoveTime = 16L // ~60fps timing

        if (Math.abs(currentY - lastMoveY) > minMoveDistance ||
            currentTime - lastMoveTime > minMoveTime) {
            println("scroll trackMovement in if")
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

    private fun detectGesture(event: MotionEvent) {
        println("gesture detectGesture")

        val endX = event.x
        val endY = event.y
        val endTime = System.currentTimeMillis()

        val deltaX = endX - startX
        val deltaY = endY - startY
        val duration = endTime - startTime

        // Convert to dp for consistent behavior across devices
        val deltaYDp = convertDpToPixel(Math.abs(deltaY).dp, context)
        val minDistancePx = convertDpToPixel(minSwipeDistance, context)

        // Calculate velocity in dp per ms
        val velocityY = if (duration > 0) deltaYDp / duration else 0f
        val isFast = abs(velocityY) >= minSwipeVelocity

        // Detect vertical swipes
        if (abs(deltaY) > abs(deltaX)) { // Vertical gesture
            if (deltaYDp >= minDistancePx) {
                if (deltaY < 0) { // Swipe Up
                    val gesture = if (isFast) GestureType.SWIPE_UP_FAST else GestureType.SWIPE_UP_SLOW
                    println("DEBUG: Detected ${gesture.name} gesture")
                    emitGesture(gesture, startX, startY, endX, endY, duration)
                } else { // Swipe Down
                    val gesture = if (isFast) GestureType.SWIPE_DOWN_FAST else GestureType.SWIPE_DOWN_SLOW
                    println("DEBUG: Detected ${gesture.name} gesture")
                    emitGesture(gesture, startX, startY, endX, endY, duration)
                }
            }
        }
    }

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

// Data classes and enums for gesture events
//data class GesturePoint(val x: Float, val y: Float)
//
//enum class GestureType {
//    SWIPE_UP_FAST,
//    SWIPE_UP_SLOW,
//    SWIPE_DOWN_FAST,
//    SWIPE_DOWN_SLOW,
//    FINGER_MOVE,
//}
//
//data class GestureEvent(
//    val type: GestureType,
//    val startPoint: GesturePoint,
//    val endPoint: GesturePoint,
//    val duration: Long
//)

