package com.wyldsoft.notes.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import com.wyldsoft.notes.views.PageView
import android.graphics.Path
import androidx.compose.runtime.mutableStateListOf
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableStateMapOf




enum class Mode {
    Draw, Erase, Selection
}

enum class PlacementMode {
    Move,
    Paste
}


class SelectionState {
    // Existing properties for general selection
    var selectedStrokes by mutableStateOf<List<Stroke>?>(null)
    var selectedBitmap by mutableStateOf<Bitmap?>(null)
    var selectionRect by mutableStateOf<Rect?>(null)
    var placementMode by mutableStateOf<PlacementMode?>(null)

    // For selection path drawing
    var selectionPath by mutableStateOf<Path?>(null)
    var isDrawingSelection by mutableStateOf(false)
    var selectionPoints = mutableStateListOf<SimplePointF>()

    // For move operation
    var isMovingSelection by mutableStateOf(false)
    var moveStartPoint by mutableStateOf<SimplePointF?>(null)
    var lastMovePoint by mutableStateOf<SimplePointF?>(null)
    var selectionBounds by mutableStateOf<RectF?>(null)
    var moveOffset = Offset(0f, 0f) // Simplify with single offset property

    // Remove redundant properties that duplicate selection bounds info
    // selectionStartOffset and selectionDisplaceOffset can be derived

    fun reset() {
        selectedStrokes = null
        selectedBitmap = null
        selectionRect = null
        placementMode = null
        selectionPath = null
        isDrawingSelection = false
        selectionPoints.clear()
        isMovingSelection = false
        moveStartPoint = null
        lastMovePoint = null
        selectionBounds = null
        moveOffset = Offset(0f, 0f)
    }
}

// Add or update a rect
// editorState.rectMapStateFlow[RectType.TOOLBAR] = Rect(0, 0, 100, 50)

// Remove a rect
// editorState.rectMapStateFlow.remove(RectType.SELECTION)

// Clear all rects
// editorState.rectMapStateFlow.clear()

enum class ExcludeRects {
    StrokeOptions
}

class EditorState(val pageId: String, val pageView: PageView) {
    var mode by mutableStateOf(Mode.Draw)
    var pen by mutableStateOf(Pen.BALLPEN)
    var eraser by mutableStateOf(Eraser.PEN)
    var isDrawing by mutableStateOf(true)
    var isToolbarOpen by mutableStateOf(false)
    var allowDrawingOnCanvas by mutableStateOf(true)
    val stateExcludeRects = mutableStateMapOf<ExcludeRects, Rect>()

    var penSettings by mutableStateOf(
        mapOf(
            Pen.BALLPEN.penName to PenSetting(5f, Color.BLACK),
            Pen.MARKER.penName to PenSetting(40f, Color.LTGRAY),
            Pen.FOUNTAIN.penName to PenSetting(5f, Color.BLACK)
        )
    )

    val selectionState = SelectionState()
}