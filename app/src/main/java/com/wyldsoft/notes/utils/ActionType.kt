package com.wyldsoft.notes.utils

/**
 * Types of actions that can be performed in the editor.
 * Used for undo/redo history tracking.
 */
enum class ActionType(val key: String) {
    ADD_STROKES("ADD_STROKES"),
    DELETE_STROKES("DELETE_STROKES"),
    MOVE_STROKES("MOVE_STROKES"),
    INSERT_PAGE("INSERT_PAGE");  // New action type for page insertion

    companion object {
        fun fromString(key: String): ActionType {
            return entries.find { it.key == key } ?: ADD_STROKES
        }
    }
}