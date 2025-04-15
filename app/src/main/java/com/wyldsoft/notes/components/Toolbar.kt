package com.wyldsoft.notes.components

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.wyldsoft.notes.classes.DrawCanvas
import com.wyldsoft.notes.utils.EditorState
import com.wyldsoft.notes.utils.Mode
import com.wyldsoft.notes.utils.PageTemplate
import com.wyldsoft.notes.utils.Pen
import com.wyldsoft.notes.utils.PenSetting
import com.wyldsoft.notes.utils.noRippleClickable
import kotlinx.coroutines.launch

@Composable
fun Toolbar(
    state: EditorState,
    navController: NavController? = null
) {
    val scope = rememberCoroutineScope()
    var isColorSelectionOpen by remember { mutableStateOf(false) }
    var isStrokeSelectionOpen by remember { mutableStateOf(false) }

    fun handleChangePen(pen: Pen) {
        if (state.mode == Mode.Draw && state.pen == pen) {
            isStrokeSelectionOpen = true
        } else {
            state.mode = Mode.Draw
            state.pen = pen
        }
    }

    fun handleEraser() {
        state.mode = Mode.Erase
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
                    .background(ComposeColor.White)
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
                        .background(ComposeColor.Black)
                )

                // Ball pen button
                ToolbarButton(
                    onSelect = { handleChangePen(Pen.BALLPEN) },
                    imageVector = Icons.Default.Create,
                    isSelected = state.mode == Mode.Draw && state.pen == Pen.BALLPEN,
                    penColor = ComposeColor(state.penSettings[Pen.BALLPEN.penName]?.color ?: Color.BLACK),
                    contentDescription = "Ball Pen"
                )

                // Fountain pen button
                ToolbarButton(
                    onSelect = { handleChangePen(Pen.FOUNTAIN) },
                    imageVector = Icons.Default.Create,
                    isSelected = state.mode == Mode.Draw && state.pen == Pen.FOUNTAIN,
                    penColor = ComposeColor(state.penSettings[Pen.FOUNTAIN.penName]?.color ?: Color.BLACK),
                    contentDescription = "Fountain Pen"
                )

                // Marker button
                ToolbarButton(
                    onSelect = { handleChangePen(Pen.MARKER) },
                    imageVector = Icons.Default.Brush,
                    isSelected = state.mode == Mode.Draw && state.pen == Pen.MARKER,
                    penColor = ComposeColor(state.penSettings[Pen.MARKER.penName]?.color ?: Color.LTGRAY),
                    contentDescription = "Marker"
                )

                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(0.5.dp)
                        .background(ComposeColor.Black)
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
                        .background(ComposeColor.Black)
                )

                // Home button - NEW
                if (navController != null) {
                    ToolbarButton(
                        onSelect = {
                            navController.popBackStack()
                        },
                        imageVector = Icons.Default.Home,
                        contentDescription = "Back to notebook"
                    )
                }

                // Settings button - NEW
                ToolbarButton(
                    onSelect = {
                        state.isSettingsDialogOpen = true
                    },
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Page settings"
                )

                Spacer(Modifier.weight(1f))

                // Reset zoom button
                ToolbarButton(
                    onSelect = {
                        state.resetZoom()
                        scope.launch {
                            DrawCanvas.refreshUi.emit(Unit)
                        }
                    },
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset zoom"
                )

                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(0.5.dp)
                        .background(ComposeColor.Black)
                )

                // Full refresh button - NEW
                ToolbarButton(
                    onSelect = {
                        scope.launch {
                            DrawCanvas.forceUpdate.emit(null)
                        }
                    },
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Full refresh"
                )

                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(0.5.dp)
                        .background(ComposeColor.Black)
                )

                // Undo button
                ToolbarButton(
                    onSelect = {
                        scope.launch {
                            state.pageView.handleUndo(state)
                            DrawCanvas.refreshUi.emit(Unit)
                        }
                    },
                    imageVector = Icons.Default.Undo,
                    contentDescription = "Undo",
                    isEnabled = state.undoStack.isNotEmpty()
                )

                // Redo button
                ToolbarButton(
                    onSelect = {
                        scope.launch {
                            state.pageView.handleRedo(state)
                            DrawCanvas.refreshUi.emit(Unit)
                        }
                    },
                    imageVector = Icons.Default.Redo,
                    contentDescription = "Redo",
                    isEnabled = state.redoStack.isNotEmpty()
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ComposeColor.Black)
            )

            if (isStrokeSelectionOpen) {
                // Implement stroke size and color selection UI here
                Row(
                    Modifier
                        .background(ComposeColor.White)
                        .padding(8.dp)
                ) {
                    // Stroke sizes
                    ToolbarButton(
                        text = "S",
                        isSelected = state.penSettings[state.pen.penName]?.strokeSize == 3f,
                        onSelect = {
                            onChangeStrokeSetting(state.pen.penName,
                                state.penSettings[state.pen.penName]!!.copy(strokeSize = 3f))
                            isStrokeSelectionOpen = false
                        }
                    )

                    ToolbarButton(
                        text = "M",
                        isSelected = state.penSettings[state.pen.penName]?.strokeSize == 5f,
                        onSelect = {
                            onChangeStrokeSetting(state.pen.penName,
                                state.penSettings[state.pen.penName]!!.copy(strokeSize = 5f))
                            isStrokeSelectionOpen = false
                        }
                    )

                    ToolbarButton(
                        text = "L",
                        isSelected = state.penSettings[state.pen.penName]?.strokeSize == 10f,
                        onSelect = {
                            onChangeStrokeSetting(state.pen.penName,
                                state.penSettings[state.pen.penName]!!.copy(strokeSize = 10f))
                            isStrokeSelectionOpen = false
                        }
                    )

                    ToolbarButton(
                        text = "XL",
                        isSelected = state.penSettings[state.pen.penName]?.strokeSize == 20f,
                        onSelect = {
                            onChangeStrokeSetting(state.pen.penName,
                                state.penSettings[state.pen.penName]!!.copy(strokeSize = 20f))
                            isStrokeSelectionOpen = false
                        }
                    )

                    // Color options
                    ColorButton(
                        color = ComposeColor.Black,
                        isSelected = state.penSettings[state.pen.penName]?.color == Color.BLACK,
                        onSelect = {
                            onChangeStrokeSetting(state.pen.penName,
                                state.penSettings[state.pen.penName]!!.copy(color = Color.BLACK))
                        }
                    )

                    ColorButton(
                        color = ComposeColor.Red,
                        isSelected = state.penSettings[state.pen.penName]?.color == Color.RED,
                        onSelect = {
                            onChangeStrokeSetting(state.pen.penName,
                                state.penSettings[state.pen.penName]!!.copy(color = Color.RED))
                        }
                    )

                    ColorButton(
                        color = ComposeColor.Blue,
                        isSelected = state.penSettings[state.pen.penName]?.color == Color.BLUE,
                        onSelect = {
                            onChangeStrokeSetting(state.pen.penName,
                                state.penSettings[state.pen.penName]!!.copy(color = Color.BLUE))
                        }
                    )

                    ColorButton(
                        color = ComposeColor.Gray,
                        isSelected = state.penSettings[state.pen.penName]?.color == Color.GRAY,
                        onSelect = {
                            onChangeStrokeSetting(state.pen.penName,
                                state.penSettings[state.pen.penName]!!.copy(color = Color.GRAY))
                        }
                    )
                }
            }
        }
    } else {
        ToolbarButton(
            onSelect = { state.isToolbarOpen = true },
            imageVector = if (state.mode == Mode.Draw) Icons.Default.Create else Icons.Default.Delete,
            penColor = if (state.mode == Mode.Draw)
                ComposeColor(state.penSettings[state.pen.penName]?.color ?: Color.BLACK)
            else null,
            contentDescription = "open toolbar",
            modifier = Modifier.height(40.dp)
        )
    }

    // Show settings dialog when isSettingsDialogOpen is true
    if (state.isSettingsDialogOpen) {
        PageSettingsDialog(
            state = state,
            onDismiss = { state.isSettingsDialogOpen = false }
        )
    }
}

@Composable
fun PageSettingsDialog(
    state: EditorState,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // Define standard page sizes
    val A4_WIDTH = 595
    val A4_HEIGHT = 842
    val LETTER_WIDTH = 612
    val LETTER_HEIGHT = 792

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Page Settings") },
        text = {
            Column {
                Text("Page Size", style = MaterialTheme.typography.subtitle1)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PageSizeOption(
                        label = "A4",
                        isSelected = state.pageView.width == A4_WIDTH,
                        onClick = {
                            // Changing page size would require more complex changes
                            // This is just a placeholder for now
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    PageSizeOption(
                        label = "Letter",
                        isSelected = state.pageView.width == LETTER_WIDTH,
                        onClick = {
                            // Placeholder for page size change
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Template", style = MaterialTheme.typography.subtitle1)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TemplateOption(
                        label = "Blank",
                        isSelected = state.pageTemplate == PageTemplate.BLANK,
                        onClick = {
                            state.pageTemplate = PageTemplate.BLANK
                            scope.launch {
                                DrawCanvas.forceUpdate.emit(null)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TemplateOption(
                        label = "Ruled Lines",
                        isSelected = state.pageTemplate == PageTemplate.RULED,
                        onClick = {
                            state.pageTemplate = PageTemplate.RULED
                            scope.launch {
                                DrawCanvas.forceUpdate.emit(null)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = state.isPaginationEnabled,
                        onCheckedChange = { checked ->
                            state.isPaginationEnabled = checked
                        }
                    )
                    Text("Enable Pagination")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun PageSizeOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colors.primary else ComposeColor.LightGray,
                shape = RoundedCornerShape(4.dp)
            )
            .background(if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.1f) else ComposeColor.Transparent)
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) MaterialTheme.colors.primary else ComposeColor.Gray
        )
    }
}

@Composable
fun TemplateOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Reuse the same design as PageSizeOption
    PageSizeOption(
        label = label,
        isSelected = isSelected,
        onClick = onClick
    )
}