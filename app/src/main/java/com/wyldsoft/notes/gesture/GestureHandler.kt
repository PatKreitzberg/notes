package com.wyldsoft.notes.gesture

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.toMutableStateMap
import com.wyldsoft.notes.transform.ViewportTransformer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Defines possible actions that can be assigned to gestures
 */
enum class GestureAction(val displayName: String) {
    NONE("No Action"),
    SCROLL_UP("Scroll Up"),
    SCROLL_DOWN("Scroll Down"),
    NEXT_PAGE("Next Page"),
    PREVIOUS_PAGE("Previous Page"),
    TOGGLE_TOOLBAR("Toggle Toolbar"),
    UNDO("Undo"),
    REDO("Redo"),
    ZOOM_IN("Zoom In"),
    ZOOM_OUT("Zoom Out"),
    SWITCH_MODE_DRAW("Switch to Draw Mode"),
    SWITCH_MODE_ERASE("Switch to Erase Mode"),
    TOGGLE_SELECTION("Toggle Selection Mode"),
    TOGGLE_TEMPLATE("Toggle Template")
}

/**
 * Defines the available gestures that can be configured
 */
enum class GestureType(val displayName: String) {
    SINGLE_FINGER_DOUBLE_TAP("Single Finger Double Tap"),
    SINGLE_FINGER_TRIPLE_TAP("Single Finger Triple Tap"),
    TWO_FINGER_DOUBLE_TAP("Two Finger Double Tap"),
    TWO_FINGER_TRIPLE_TAP("Two Finger Triple Tap"),
    THREE_FINGER_DOUBLE_TAP("Three Finger Double Tap"),
    THREE_FINGER_TRIPLE_TAP("Three Finger Triple Tap"),
    FOUR_FINGER_DOUBLE_TAP("Four Finger Double Tap"),
    FOUR_FINGER_TRIPLE_TAP("Four Finger Triple Tap")
}

@Serializable
data class GestureMapping(
    val gestureType: String,
    val actionType: String
)

class GestureHandler(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val viewportTransformer: ViewportTransformer? = null
) {
    // Map of gesture types to their assigned actions
    private val gestureActions = mutableStateMapOf<GestureType, GestureAction>()

    // Default gesture mappings
    init {
        loadGestureMappings()
    }

    /**
     * Handle a detected gesture
     * @param gestureType The type of gesture detected
     */
    fun handleGesture(gestureString: String) {
        // Extract the gesture type from the string
        // The format is "gesture: [GestureType] detected"
        val parts = gestureString.split(":")
        if (parts.size < 2) return

        val gestureDesc = parts[1].trim().removeSuffix(" detected").trim()

        // Map the gesture description to a GestureType
        val gestureType = when (gestureDesc) {
            "Single-finger double tap" -> GestureType.SINGLE_FINGER_DOUBLE_TAP
            "Single-finger triple tap" -> GestureType.SINGLE_FINGER_TRIPLE_TAP
            "Two-finger double tap" -> GestureType.TWO_FINGER_DOUBLE_TAP
            "Two-finger triple tap" -> GestureType.TWO_FINGER_TRIPLE_TAP
            "Three-finger double tap" -> GestureType.THREE_FINGER_DOUBLE_TAP
            "Three-finger triple tap" -> GestureType.THREE_FINGER_TRIPLE_TAP
            "Four-finger double tap" -> GestureType.FOUR_FINGER_DOUBLE_TAP
            "Four-finger triple tap" -> GestureType.FOUR_FINGER_TRIPLE_TAP
            else -> null
        }

        if (gestureType == null) {
            Log.d("GestureHandler", "Unknown gesture: $gestureDesc")
            return
        }

        // Get the action assigned to this gesture
        val action = gestureActions[gestureType] ?: GestureAction.NONE

        // Execute the action
        executeAction(action)

        // For now, just log what happened
        Log.d("GestureHandler", "Gesture: $gestureType, Action: $action")
    }

    /**
     * Execute the specified action
     */
    private fun executeAction(action: GestureAction) {
        when (action) {
            GestureAction.NONE -> {
                // Do nothing
                println("No action assigned to this gesture")
            }
            GestureAction.SCROLL_UP -> {
                println("Executing action: Scroll Up")
                viewportTransformer?.scroll(-200f)
            }
            GestureAction.SCROLL_DOWN -> {
                println("Executing action: Scroll Down")
                viewportTransformer?.scroll(200f)
            }
            GestureAction.NEXT_PAGE -> {
                println("Executing action: Next Page")
                // This would need to be implemented through a callback to the page view
            }
            GestureAction.PREVIOUS_PAGE -> {
                println("Executing action: Previous Page")
                // This would need to be implemented through a callback to the page view
            }
            GestureAction.TOGGLE_TOOLBAR -> {
                println("Executing action: Toggle Toolbar")
                // This would need to be implemented through a callback to the editor state
            }
            GestureAction.UNDO -> {
                println("Executing action: Undo")
                performUndoRedoAction(true)
            }
            GestureAction.REDO -> {
                println("Executing action: Redo")
                performUndoRedoAction(false)
            }
            GestureAction.ZOOM_IN -> {
                println("Executing action: Zoom In")
                // Implement zoom in logic
            }
            GestureAction.ZOOM_OUT -> {
                println("Executing action: Zoom Out")
                // Implement zoom out logic
            }
            GestureAction.SWITCH_MODE_DRAW -> {
                println("Executing action: Switch to Draw Mode")
                // Implement mode switch logic
            }
            GestureAction.SWITCH_MODE_ERASE -> {
                println("Executing action: Switch to Erase Mode")
                // Implement mode switch logic
            }
            GestureAction.TOGGLE_SELECTION -> {
                println("Executing action: Toggle Selection Mode")
                // Implement selection mode toggle
            }
            GestureAction.TOGGLE_TEMPLATE -> {
                println("Executing action: Toggle Template")
                // Implement template toggle
            }
        }
    }

    /**
     * Set a gesture to perform a specific action
     */
    fun setGestureAction(gesture: GestureType, action: GestureAction) {
        gestureActions[gesture] = action
        saveGestureMappings()
    }

    /**
     * Get the current action for a gesture
     */
    fun getGestureAction(gesture: GestureType): GestureAction {
        return gestureActions[gesture] ?: GestureAction.NONE
    }

    /**
     * Get all gesture mappings
     */
    fun getGestureMappings(): Map<GestureType, GestureAction> {
        return gestureActions.toMap()
    }

    /**
     * Save gesture mappings to shared preferences
     */
    private fun saveGestureMappings() {
        val prefs = context.getSharedPreferences("gesture_settings", Context.MODE_PRIVATE)
        val mappings = gestureActions.map { (gestureType, actionType) ->
            GestureMapping(gestureType.name, actionType.name)
        }
        val json = Json.encodeToString(mappings)
        prefs.edit().putString("gesture_mappings", json).apply()
    }

    /**
     * Load gesture mappings from shared preferences
     */
    private fun loadGestureMappings() {
        try {
            val prefs = context.getSharedPreferences("gesture_settings", Context.MODE_PRIVATE)
            val json = prefs.getString("gesture_mappings", null)

            if (json != null) {
                val mappings = Json.decodeFromString<List<GestureMapping>>(json)
                val loadedMappings = mappings.associate {
                    GestureType.valueOf(it.gestureType) to GestureAction.valueOf(it.actionType)
                }
                gestureActions.clear()
                gestureActions.putAll(loadedMappings)
            } else {
                // Set default mappings if none are saved
                setDefaultMappings()
            }
        } catch (e: Exception) {
            Log.e("GestureHandler", "Error loading gesture mappings: ${e.message}")
            // Fall back to defaults
            setDefaultMappings()
        }
    }

    /**
     * Set default gesture mappings
     */
    private fun setDefaultMappings() {
        gestureActions.clear()
        gestureActions[GestureType.SINGLE_FINGER_DOUBLE_TAP] = GestureAction.TOGGLE_TOOLBAR
        gestureActions[GestureType.SINGLE_FINGER_TRIPLE_TAP] = GestureAction.NONE
        gestureActions[GestureType.TWO_FINGER_DOUBLE_TAP] = GestureAction.UNDO
        gestureActions[GestureType.TWO_FINGER_TRIPLE_TAP] = GestureAction.REDO
        gestureActions[GestureType.THREE_FINGER_DOUBLE_TAP] = GestureAction.NONE
        gestureActions[GestureType.THREE_FINGER_TRIPLE_TAP] = GestureAction.NONE
        gestureActions[GestureType.FOUR_FINGER_DOUBLE_TAP] = GestureAction.NONE
        gestureActions[GestureType.FOUR_FINGER_TRIPLE_TAP] = GestureAction.NONE
    }

    private fun performUndoRedoAction(isUndo: Boolean) {
        coroutineScope.launch {
            try {
                // Get the app instance
                val app = context.applicationContext as? com.wyldsoft.notes.NotesApp ?: return@launch

                // Get the active page ID
                val activePageId = app.getActivePageId() ?: return@launch

                // Get the history manager for the page
                val historyManager = app.historyRepository.getHistoryManager(activePageId)

                // Check if we have a valid history manager
                if (historyManager == null) {
                    println("Cannot perform undo/redo: No history manager for page $activePageId")
                    return@launch
                }

                // Get the page view for this page ID (this is a bit more complex)
                // For a cleaner implementation, NotesApp could also track the PageView instance
                // But for now, we'll just use the history manager

                // Create a new DrawingManager with the history manager
                val drawingManager = com.wyldsoft.notes.classes.drawing.DrawingManager(
                    page = app.activePageView ?: return@launch,
                    historyManager = historyManager
                )

                // Perform the action
                val success = if (isUndo) drawingManager.undo() else drawingManager.redo()

                // If successful, emit an event to refresh the UI
                if (success) {
                    com.wyldsoft.notes.classes.drawing.DrawingManager.undoRedoPerformed.emit(Unit)
                    com.wyldsoft.notes.classes.drawing.DrawingManager.refreshUi.emit(Unit)
                    println("Successfully performed ${if (isUndo) "undo" else "redo"} operation")
                } else {
                    println("No action to ${if (isUndo) "undo" else "redo"}")
                }
            } catch (e: Exception) {
                println("Error performing ${if (isUndo) "undo" else "redo"}: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}