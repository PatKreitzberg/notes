package com.wyldsoft.notes.components

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Undo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.classes.DrawCanvas
import com.wyldsoft.notes.utils.EditorState
import com.wyldsoft.notes.utils.Mode
import com.wyldsoft.notes.utils.Pen
import com.wyldsoft.notes.utils.PenSetting
import com.wyldsoft.notes.utils.noRippleClickable
import kotlinx.coroutines.launch

@Composable
fun Toolbar(
    state: EditorState
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
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(androidx.compose.ui.graphics.Color.Black)
            )

            if (isStrokeSelectionOpen) {
                // Implement stroke size and color selection UI here
                Row(
                    Modifier
                        .background(androidx.compose.ui.graphics.Color.White)
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
                        color = androidx.compose.ui.graphics.Color.Black,
                        isSelected = state.penSettings[state.pen.penName]?.color == Color.BLACK,
                        onSelect = {
                            onChangeStrokeSetting(state.pen.penName,
                                state.penSettings[state.pen.penName]!!.copy(color = Color.BLACK))
                        }
                    )

                    ColorButton(
                        color = androidx.compose.ui.graphics.Color.Red,
                        isSelected = state.penSettings[state.pen.penName]?.color == Color.RED,
                        onSelect = {
                            onChangeStrokeSetting(state.pen.penName,
                                state.penSettings[state.pen.penName]!!.copy(color = Color.RED))
                        }
                    )

                    ColorButton(
                        color = androidx.compose.ui.graphics.Color.Blue,
                        isSelected = state.penSettings[state.pen.penName]?.color == Color.BLUE,
                        onSelect = {
                            onChangeStrokeSetting(state.pen.penName,
                                state.penSettings[state.pen.penName]!!.copy(color = Color.BLUE))
                        }
                    )

                    ColorButton(
                        color = androidx.compose.ui.graphics.Color.Gray,
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
                androidx.compose.ui.graphics.Color(state.penSettings[state.pen.penName]?.color ?: Color.BLACK)
            else null,
            contentDescription = "open toolbar",
            modifier = Modifier.height(40.dp)
        )
    }
}