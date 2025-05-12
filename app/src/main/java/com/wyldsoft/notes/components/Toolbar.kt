package com.wyldsoft.notes.components

import android.graphics.Color
import android.graphics.Rect
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.SelectAll
import com.wyldsoft.notes.selection.SelectionHandler
import androidx.compose.material.Text
import com.wyldsoft.notes.dialog.SettingsDialog
import com.wyldsoft.notes.utils.ExcludeRects
import android.graphics.drawable.VectorDrawable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.wyldsoft.notes.R
import com.wyldsoft.notes.classes.SnackConf
import com.wyldsoft.notes.classes.SnackState
import com.wyldsoft.notes.views.PageView



@Composable
fun Toolbar(
    state: EditorState,
    settingsRepository: SettingsRepository,
    viewportTransformer: ViewportTransformer,
    templateRenderer: TemplateRenderer,
    selectionHandler: SelectionHandler,
    drawingManager: DrawingManager,
    noteTitle: String,
    onUpdateNoteName: (String) -> Unit,
    page: PageView = state.pageView
) {
    val scope = rememberCoroutineScope()
    var isStrokeSelectionOpen by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var strokePanelRect by remember { mutableStateOf<Rect?>(null) }
    var haveStrokePanelRect by remember { mutableStateOf(false) }
    val EraserIcon = ImageVector.vectorResource(R.drawable.eraser)


    fun removeStrokeOptionPanelRect() {
        state.stateExcludeRects.remove(ExcludeRects.StrokeOptions)
        isStrokeSelectionOpen = false
        state.stateExcludeRectsModified = true
    }

    fun addStrokeOptionPanelRect() {
        if (haveStrokePanelRect) {
            state.stateExcludeRects[ExcludeRects.StrokeOptions] = strokePanelRect?: Rect(0, 0, 300, 500)
            state.stateExcludeRectsModified = true
        }
    }

    fun handleSelection() {
        state.mode = Mode.Selection
        state.selectionState.reset()
    }

    fun handleEraser() {
        state.mode = Mode.Erase
    }

    fun handleChangePen(pen: Pen) {
        if (state.mode == Mode.Draw && state.pen == pen) {
            // Toggle stroke options panel if clicking the same pen button
            isStrokeSelectionOpen = !isStrokeSelectionOpen

            if (isStrokeSelectionOpen) {
                addStrokeOptionPanelRect()
            } else {
                removeStrokeOptionPanelRect()
            }
        } else {
            // Selected a different pen - close options and switch
            if (isStrokeSelectionOpen) {
                removeStrokeOptionPanelRect()
            }
            state.mode = Mode.Draw
            state.pen = pen
        }
    }

    LaunchedEffect(Unit) {
        DrawingManager.isDrawing.collect {
            if (it && isStrokeSelectionOpen) {
                // Close options panel when drawing starts
                isStrokeSelectionOpen = false
                state.stateExcludeRects.remove(ExcludeRects.StrokeOptions)
            }
        }
    }

    // Expose the stroke selection state to the DrawCanvas
    LaunchedEffect(isStrokeSelectionOpen) {
        // Notify the DrawingManager about panel state changes
        println("LauncedEffect isStrokeSelectionOpen $isStrokeSelectionOpen")
        com.wyldsoft.notes.classes.drawing.DrawingManager.isStrokeOptionsOpen.emit(isStrokeSelectionOpen)

        // Remove the rect when panel is closed
        if (!isStrokeSelectionOpen) {
            removeStrokeOptionPanelRect()
            println("DEBUG: Removed StrokeOptions exclusion rect")
        }
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

                        // Close stroke options panel if open
                        if (isStrokeSelectionOpen) {
                            removeStrokeOptionPanelRect()
                        }
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
                    onSelect = {
                        handleEraser()
                        // Close stroke options panel if open
                        if (isStrokeSelectionOpen) {
                            removeStrokeOptionPanelRect()
                        }
                    },
                    imageVector = ImageVector.vectorResource(id = R.drawable.eraser),
                    isSelected = state.mode == Mode.Erase,
                    contentDescription = "Eraser"
                )

                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(0.5.dp)
                        .background(androidx.compose.ui.graphics.Color.Black)
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
                    onSelect = {
                        handleSelection()
                        // Close stroke options panel if open
                        if (isStrokeSelectionOpen) {
                            removeStrokeOptionPanelRect()
                        }
                    },
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

                // Undo button
                ToolbarButton(
                    onSelect = {
                        // Close stroke options panel if open
                        if (isStrokeSelectionOpen) {
                            removeStrokeOptionPanelRect()
                        }

                        // Perform undo operation
                        scope.launch {
                            val historyManager = page.getHistoryManager()
                            val drawingManager = DrawingManager(page, historyManager)
                            val success = drawingManager.undo()
                            if (success) {
                                scope.launch {
                                    DrawingManager.refreshUi.emit(Unit)
                                }
                            }
                        }
                    },
                    imageVector = Icons.Default.Undo,
                    contentDescription = "Undo",
                    isEnabled = state.canUndo.also { println("DEBUG: Undo button isEnabled=$it") }
                )

                // Redo button
                ToolbarButton(
                    onSelect = {
                        // Close stroke options panel if open
                        if (isStrokeSelectionOpen) {
                            removeStrokeOptionPanelRect()
                        }

                        // Perform redo operation
                        scope.launch {
                            val historyManager = page.getHistoryManager()
                            val drawingManager = DrawingManager(page, historyManager)
                            val success = drawingManager.redo()
                            if (success) {
                                scope.launch {
                                    DrawingManager.refreshUi.emit(Unit)
                                }
                            }
                        }
                    },
                    imageVector = Icons.Default.Redo,
                    contentDescription = "Redo",
                    isEnabled = state.canRedo
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
                    onClick = {
                        showSettings = true
                        state.allowDrawingOnCanvas = false
                        println("DISAllow draing")
                        // Close stroke options panel if open
                        if (isStrokeSelectionOpen) {
                            removeStrokeOptionPanelRect()
                        }
                    }
                )

                // Show settings dialog if needed
                if (showSettings) {
                    SettingsDialog(
                        settingsRepository = settingsRepository,
                        currentNoteName = noteTitle,
                        currentNoteId = state.pageId,
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
                        onInsertPage = { pageNumber ->
                            // Handle page insertion
                            scope.launch {
                                val drawingManager = DrawingManager(page, page.getHistoryManager())
                                val success = drawingManager.insertPage(pageNumber)

                                if (success) {
                                    // Show notification
                                    SnackState.globalSnackFlow.emit(
                                        SnackConf(
                                            text = "Page inserted before page $pageNumber",
                                            duration = 3000
                                        )
                                    )

                                    // Refresh UI
                                    DrawingManager.refreshUi.emit(Unit)
                                }
                            }
                        },
                        onDismiss = { showSettings = false; state.allowDrawingOnCanvas = true; println("Allow drawing") }
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
                    onDismiss = {
                        println("strokeOptionPanel onDismiss")
                        removeStrokeOptionPanelRect()
                    },
                    onPanelPositioned = { rect ->
                        if (rect != strokePanelRect) {
                            strokePanelRect = rect
                            haveStrokePanelRect = true
                            addStrokeOptionPanelRect()
                            println("ExclusionRects: Updated StrokeOptions exclusion rect: $rect")
                        }
                    }
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