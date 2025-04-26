package com.wyldsoft.notes.gesture

import com.wyldsoft.notes.classes.SnackConf
import com.wyldsoft.notes.classes.SnackState
import java.text.DecimalFormat

/**
 * Handles displaying gesture notifications using the app's SnackBar system.
 */
class GestureNotifier {
    // Format for displaying coordinates
    private val coordFormat = DecimalFormat("#.#")

    // Duration to display the gesture notification
    private val notificationDuration = 2000 // milliseconds

    suspend fun notifyGesture(event: GestureEvent) {
        val message = formatGestureMessage(event)

        // Use the global snack flow to display notifications
        SnackState.globalSnackFlow.emit(
            SnackConf(
                text = message,
                duration = notificationDuration
            )
        )
    }

    private fun formatGestureMessage(event: GestureEvent): String {
        val gestureType = when (event.type) {
            GestureType.SWIPE_UP_FAST -> "Fast Swipe Up"
            GestureType.SWIPE_UP_SLOW -> "Slow Swipe Up"
            GestureType.SWIPE_DOWN_FAST -> "Fast Swipe Down"
            GestureType.SWIPE_DOWN_SLOW -> "Slow Swipe Down"
        }

        return "$gestureType detected: " +
                "Start(${coordFormat.format(event.startPoint.x)}, ${coordFormat.format(event.startPoint.y)}), " +
                "End(${coordFormat.format(event.endPoint.x)}, ${coordFormat.format(event.endPoint.y)}), " +
                "Duration: ${event.duration}ms"
    }
}