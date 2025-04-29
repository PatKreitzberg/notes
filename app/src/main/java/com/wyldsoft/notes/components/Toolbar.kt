package com.wyldsoft.notes.components

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Undo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.utils.EditorState
import com.wyldsoft.notes.utils.Mode
import com.wyldsoft.notes.utils.PlacementMode
import com.wyldsoft.notes.utils.Pen
import com.wyldsoft.notes.utils.PenSetting
import kotlinx.coroutines.launch
import com.wyldsoft.notes.classes.drawing.DrawingManager
import com.wyldsoft.notes.settings.SettingsRepository
import com.wyldsoft.notes.transform.ViewportTransformer
import com.wyldsoft.notes.templates.TemplateRenderer
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.SelectAll
import com.wyldsoft.notes.selection.SelectionHandler
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.Text  // Add this import for Text component
import androidx.compose.ui.geometry.Offset  // Make sure this is imported for moveOffset


@Composable
fun Toolbar(
    state: EditorState,
    settingsRepository: SettingsRepository,
    viewportTransformer: ViewportTransformer,
    templateRenderer: TemplateRenderer,
    selectionHandler: SelectionHandler,
    noteTitle: String,
    onUpdateNoteName: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isStrokeSelectionOpen by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }

    fun handleSelection() {
        state.mode = Mode.Selection
        state.selectionState.reset()
    }

    fun handleEraser() {
        state.mode = Mode.Erase
    }

    fun handleChangePen(pen: Pen) {
        if (state.mode == Mode.Draw && state.pen == pen) {
            isStrokeSelectionOpen = true
        } else {
            state.mode = Mode.Draw
            state.pen = pen
        }
    }

    // Expose the stroke selection state to the DrawCanvas
    LaunchedEffect(isStrokeSelectionOpen) {
        // Notify the DrawingManager about panel state changes
        com.wyldsoft.notes.classes.drawing.DrawingManager.isStrokeOptionsOpen.emit(isStrokeSelectionOpen)
    }

    fun onChangeStrokeSetting(penName: String, setting: PenSetting) {
        val settings = state.penSettings.toMutableMap()
        settings[penName] = setting.copy()
        state.penSettings = settings
    }

    if (state.isToolbarOpen) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier
                    .background(androidx.compose.ui.graphics.Color.White)
                    .height(40.dp)
                    .fillMaxWidth()
            ) {
                ToolbarButton(
                    onSelect = {
                        state.isToolbarOpen = !state.isToolbarOpen
                    },
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = "close toolbar"
                )

                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(0.5.dp)
                        .background(androidx.compose.ui.graphics.Color.Black)
                )

                // Ball pen button
                ToolbarButton(
                    onSelect = { handleChangePen(Pen.BALLPEN) },
                    imageVector = Icons.Default.Create,
                    isSelected = state.mode == Mode.Draw && state.pen == Pen.BALLPEN,
                    penColor = androidx.compose.ui.graphics.Color(state.penSettings[Pen.BALLPEN.penName]?.color ?: Color.BLACK),
                    contentDescription = "Ball Pen"
                )

                // Fountain pen button
                ToolbarButton(
                    onSelect = { handleChangePen(Pen.FOUNTAIN) },
                    imageVector = Icons.Default.Create,
                    isSelected = state.mode == Mode.Draw && state.pen == Pen.FOUNTAIN,
                    penColor = androidx.compose.ui.graphics.Color(state.penSettings[Pen.FOUNTAIN.penName]?.color ?: Color.BLACK),
                    contentDescription = "Fountain Pen"
                )

                // Marker button
                ToolbarButton(
                    onSelect = { handleChangePen(Pen.MARKER) },
                    imageVector = Icons.Default.Brush,
                    isSelected = state.mode == Mode.Draw && state.pen == Pen.MARKER,
                    penColor = androidx.compose.ui.graphics.Color(state.penSettings[Pen.MARKER.penName]?.color ?: Color.LTGRAY),
                    contentDescription = "Marker"
                )

                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(0.5.dp)
                        .background(androidx.compose.ui.graphics.Color.Black)
                )

                // Eraser button
                ToolbarButton(
                    onSelect = { handleEraser() },
                    imageVector = Icons.Default.Delete,
                    isSelected = state.mode == Mode.Erase,
                    contentDescription = "Eraser"
                )

                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(0.5.dp)
                        .background(androidx.compose.ui.graphics.Color.Black)
                )

                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(0.5.dp)
                        .background(androidx.compose.ui.graphics.Color.Black)
                )

                // Selection button
                ToolbarButton(
                    onSelect = { handleSelection() },
                    imageVector = Icons.Default.SelectAll,
                    isSelected = state.mode == Mode.Selection,
                    contentDescription = "Selection Tool"
                )

                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(0.5.dp)
                        .background(androidx.compose.ui.graphics.Color.Black)
                )

                // Undo button (Placeholder - no history implemented yet)
                ToolbarButton(
                    onSelect = {
                        // No functionality yet
                    },
                    imageVector = Icons.Default.Undo,
                    contentDescription = "Undo"
                )

                // Redo button (Placeholder - no history implemented yet)
                ToolbarButton(
                    onSelect = {
                        // No functionality yet
                    },
                    imageVector = Icons.Default.Redo,
                    contentDescription = "Redo"
                )
                Spacer(Modifier.weight(1f))

                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(0.5.dp)
                        .background(androidx.compose.ui.graphics.Color.Black)
                )

                // Settings button
                SettingsButton(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    onClick = { showSettings = true }
                )

                // Show settings dialog if needed
                if (showSettings) {
                    SettingsDialog(
                        settingsRepository = settingsRepository,
                        currentNoteName = noteTitle,
                        onUpdateViewportTransformer = { isPaginationEnabled ->
                            viewportTransformer.updatePaginationState(isPaginationEnabled)
                        },
                        onUpdatePageDimensions = { paperSize ->
                            viewportTransformer.updatePaperSizeState(paperSize)
                        },
                        onUpdateTemplate = { template ->
                            scope.launch {
                                DrawingManager.refreshUi.emit(Unit)
                            }
                        },
                        onUpdateNoteName = onUpdateNoteName,
                        onDismiss = { showSettings = false }
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(androidx.compose.ui.graphics.Color.Black)
            )
            if (isStrokeSelectionOpen) {
                StrokeOptionPanel(
                    currentPenName = state.pen.penName,
                    currentSetting = state.penSettings[state.pen.penName]!!,
                    onSettingChanged = { newSetting ->
                        val settings = state.penSettings.toMutableMap()
                        settings[state.pen.penName] = newSetting
                        state.penSettings = settings
                    },
                    onDismiss = { isStrokeSelectionOpen = false }
                )
            }
            if (state.mode == Mode.Selection && state.selectionState.selectedStrokes != null) {
                Row(
                    Modifier
                        .background(androidx.compose.ui.graphics.Color.White)
                        .height(40.dp)
                        .fillMaxWidth()
                ) {
                    // Copy button
                    ToolbarButton(
                        onSelect = {
                            selectionHandler.copySelection()
                        },
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Selection"
                    )

                    // Paste button (only if in paste mode)
                    if (state.selectionState.placementMode == PlacementMode.Paste) {
                        ToolbarButton(
                            onSelect = {
                                // Visual indicator only - actual paste on touch
                            },
                            imageVector = Icons.Default.ContentPaste,
                            isSelected = true,
                            contentDescription = "Paste Selection"
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // Status text
                    Text(
                        text = "${state.selectionState.selectedStrokes?.size ?: 0} strokes selected",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                    )
                }
            }
        }
    } else {
        ToolbarButton(
            onSelect = { state.isToolbarOpen = true },
            imageVector = if (state.mode == Mode.Draw) Icons.Default.Create else Icons.Default.Delete,
            penColor = if (state.mode == Mode.Draw)
                androidx.compose.ui.graphics.Color(state.penSettings[state.pen.penName]?.color ?: Color.BLACK)
            else null,
            contentDescription = "open toolbar",
            modifier = Modifier.height(40.dp)
        )
    }
}