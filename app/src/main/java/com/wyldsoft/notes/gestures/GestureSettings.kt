package com.wyldsoft.notes.gestures

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Enum representing the available gestures in the app.
 */
enum class GestureType {
    SINGLE_FINGER_DOUBLE_TAP,
    TWO_FINGER_DOUBLE_TAP,
    THREE_FINGER_DOUBLE_TAP,
    FOUR_FINGER_DOUBLE_TAP,
    SWIPE_UP,
    SWIPE_DOWN,
    SWIPE_LEFT,
    SWIPE_RIGHT;

    companion object {
        fun fromString(value: String): GestureType? {
            return try {
                valueOf(value)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}

/**
 * Enum representing the available actions that can be assigned to gestures.
 */
enum class GestureAction {
    UNDO,
    REDO,
    TOGGLE_TOOLBAR,
    CLEAR_PAGE,
    NEXT_PAGE,
    PREVIOUS_PAGE,
    CHANGE_PEN_COLOR,
    TOGGLE_ERASER,
    NONE;

    companion object {
        fun fromString(value: String): GestureAction? {
            return try {
                valueOf(value)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}

/**
 * Data class representing a gesture mapping.
 */
@Serializable
data class GestureMapping(
    val gestureType: GestureType,
    var action: GestureAction
)

/**
 * Manager class for handling gesture settings.
 */
class GestureSettingsManager(private val context: Context) {
    private val PREFS_NAME = "gesture_settings"
    private val PREFS_KEY_MAPPINGS = "gesture_mappings"
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Get all gesture mappings. If no mappings exist yet, create default mappings.
     */
    fun getMappings(): List<GestureMapping> {
        val mappingsJson = prefs.getString(PREFS_KEY_MAPPINGS, null)

        return if (mappingsJson != null) {
            try {
                json.decodeFromString<List<GestureMapping>>(mappingsJson)
            } catch (e: Exception) {
                createDefaultMappings()
            }
        } else {
            createDefaultMappings()
        }
    }

    /**
     * Save all gesture mappings.
     */
    fun saveMappings(mappings: List<GestureMapping>) {
        val mappingsJson = json.encodeToString(mappings)
        prefs.edit().putString(PREFS_KEY_MAPPINGS, mappingsJson).apply()
    }

    /**
     * Update a specific gesture mapping.
     */
    fun updateMapping(gestureType: GestureType, action: GestureAction) {
        val mappings = getMappings().toMutableList()
        val index = mappings.indexOfFirst { it.gestureType == gestureType }

        if (index != -1) {
            mappings[index] = mappings[index].copy(action = action)
        } else {
            mappings.add(GestureMapping(gestureType, action))
        }

        saveMappings(mappings)
    }

    /**
     * Get the action for a specific gesture type.
     */
    fun getActionForGesture(gestureType: GestureType): GestureAction {
        val mappings = getMappings()
        return mappings.find { it.gestureType == gestureType }?.action ?: GestureAction.NONE
    }

    /**
     * Create default mappings.
     */
    private fun createDefaultMappings(): List<GestureMapping> {
        val defaultMappings = listOf(
            GestureMapping(GestureType.SINGLE_FINGER_DOUBLE_TAP, GestureAction.TOGGLE_TOOLBAR),
            GestureMapping(GestureType.TWO_FINGER_DOUBLE_TAP, GestureAction.UNDO),
            GestureMapping(GestureType.THREE_FINGER_DOUBLE_TAP, GestureAction.REDO),
            GestureMapping(GestureType.FOUR_FINGER_DOUBLE_TAP, GestureAction.CLEAR_PAGE),
            GestureMapping(GestureType.SWIPE_UP, GestureAction.PREVIOUS_PAGE),
            GestureMapping(GestureType.SWIPE_DOWN, GestureAction.NEXT_PAGE),
            GestureMapping(GestureType.SWIPE_LEFT, GestureAction.TOGGLE_ERASER),
            GestureMapping(GestureType.SWIPE_RIGHT, GestureAction.CHANGE_PEN_COLOR)
        )

        saveMappings(defaultMappings)
        return defaultMappings
    }

    /**
     * Reset all mappings to defaults.
     */
    fun resetToDefaults() {
        createDefaultMappings()
    }
}