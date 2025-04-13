package com.wyldsoft.notes.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import com.wyldsoft.notes.classes.PageView

enum class Mode {
    Draw, Erase
}

enum class PlacementMode {
    Move,
    Paste
}

class SelectionState {
    var selectedStrokes by mutableStateOf<List<Stroke>?>(null)
    var selectedBitmap by mutableStateOf<Bitmap?>(null)
    var selectionStartOffset by mutableStateOf<IntOffset?>(null)
    var selectionDisplaceOffset by mutableStateOf<IntOffset?>(null)
    var selectionRect by mutableStateOf<Rect?>(null)
    var placementMode by mutableStateOf<PlacementMode?>(null)

    fun reset() {
        selectedStrokes = null
        selectedBitmap = null
        selectionStartOffset = null
        selectionRect = null
        selectionDisplaceOffset = null
        placementMode = null
    }
}

class EditorState(val pageId: String, val pageView: PageView) {
    var mode by mutableStateOf(Mode.Draw)
    var pen by mutableStateOf(Pen.BALLPEN)
    var eraser by mutableStateOf(Eraser.PEN)
    var isDrawing by mutableStateOf(true)
    var isToolbarOpen by mutableStateOf(false)
    var penSettings by mutableStateOf(
        mapOf(
            Pen.BALLPEN.penName to PenSetting(5f, Color.BLACK),
            Pen.MARKER.penName to PenSetting(40f, Color.LTGRAY),
            Pen.FOUNTAIN.penName to PenSetting(5f, Color.BLACK)
        )
    )

    // Zoom properties
    var zoomScale by mutableStateOf(1.0f)
    var zoomOffsetX by mutableStateOf(0f)
    var zoomOffsetY by mutableStateOf(0f)
    val minZoom = 0.5f
    val maxZoom = 2.0f

    fun resetZoom() {
        zoomScale = 1.0f
        zoomOffsetX = 0f
        zoomOffsetY = 0f
    }

    fun normalizeZoom() {
        zoomScale = zoomScale.coerceIn(minZoom, maxZoom)
        if (zoomScale == 1.0f) {
            zoomOffsetX = 0f
            zoomOffsetY = 0f
        }
    }

    val selectionState = SelectionState()
}