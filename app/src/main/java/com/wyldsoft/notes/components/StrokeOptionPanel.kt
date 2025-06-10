// app/src/main/java/com/wyldsoft/notes/components/StrokeOptionPanel.kt
package com.wyldsoft.notes.components

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyldsoft.notes.strokeManagement.DrawingManager
import com.wyldsoft.notes.utils.PenSetting
import com.wyldsoft.notes.utils.noRippleClickable

@Composable
fun StrokeOptionPanel(
    currentPenName: String,
    currentSetting: PenSetting,
    onSettingChanged: (PenSetting) -> Unit,
    onDismiss: () -> Unit,
    onPanelPositioned: (Rect) -> Unit = {}
) {
    var strokeSize by remember { mutableStateOf(currentSetting.strokeSize) }
    var selectedColor by remember { mutableStateOf(currentSetting.color) }

    // Calculate the maximum stroke size based on pen type
    val maxStrokeSize = when (currentPenName) {
        "BALLPEN" -> 20f
        "FOUNTAIN" -> 30f
        "MARKER" -> 60f
        else -> 20f
    }

    // Apply settings immediately when they change
    LaunchedEffect(strokeSize, selectedColor) {
        // Update pen settings
        onSettingChanged(PenSetting(strokeSize, selectedColor))

        // Directly emit the style change event
        DrawingManager.strokeStyleChanged.emit(Unit)
    }

    // Also apply settings when component is disposed (as a safety measure)
    DisposableEffect(Unit) {
        onDispose {
            onSettingChanged(PenSetting(strokeSize, selectedColor))
        }
    }


    // Calculate the width of a color row (4 buttons of 40.dp each with 4.dp padding on each side)
    // 4 * (40 + 8) = 192.dp for the color buttons
    val colorRowWidth = 192.dp

    Column(
        modifier = Modifier
            .wrapContentWidth() // Use wrap content instead of fillMaxWidth
            .background(Color.White)
            .padding(16.dp)
            .onGloballyPositioned { coordinates ->
                // Here, we return the rect that covers the panel so we can exclude it from drawable area
                // Report the height to the parent
                val boundingRect = coordinates.boundsInWindow()

                // Convert to Android Rect (from Compose Rect)
                val panelRect = Rect(
                    boundingRect.left.toInt(),
                    boundingRect.top.toInt(),
                    boundingRect.right.toInt(),
                    boundingRect.bottom.toInt()
                )

                // Report the rect to the parent
                onPanelPositioned(panelRect)
            }
    ) {
        // Pen name and preview - simplified header without "Done" text
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${getPenDisplayName(currentPenName)} Options",
                fontSize = 18.sp,
                modifier = Modifier.padding(end = 16.dp)
            )

            // Stroke preview
            Box(
                modifier = Modifier
                    .size(strokeSize.dp)
                    .clip(CircleShape)
                    .background(Color(selectedColor))
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stroke size slider with fixed width to match color rows
        Text(text = "Stroke Size: ${strokeSize.toInt()}")
        Box(modifier = Modifier.width(colorRowWidth)) {
            Slider(
                value = strokeSize,
                onValueChange = { strokeSize = it },
                valueRange = 1f..maxStrokeSize,
                steps = 0,
                colors = SliderDefaults.colors(
                    thumbColor = Color(selectedColor),
                    activeTrackColor = Color(selectedColor).copy(alpha = 0.6f)
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Color selection
        Text(text = "Color:")
        Spacer(modifier = Modifier.height(8.dp))

        // First row of colors
        Row {
            ColorButton(
                color = Color.Black,
                isSelected = selectedColor == android.graphics.Color.BLACK,
                size = 40.dp,
                onSelect = { selectedColor = android.graphics.Color.BLACK }
            )

            ColorButton(
                color = Color.DarkGray,
                isSelected = selectedColor == android.graphics.Color.DKGRAY,
                size = 40.dp,
                onSelect = { selectedColor = android.graphics.Color.DKGRAY }
            )

            ColorButton(
                color = Color.Gray,
                isSelected = selectedColor == android.graphics.Color.GRAY,
                size = 40.dp,
                onSelect = { selectedColor = android.graphics.Color.GRAY }
            )

            ColorButton(
                color = Color.LightGray,
                isSelected = selectedColor == android.graphics.Color.LTGRAY,
                size = 40.dp,
                onSelect = { selectedColor = android.graphics.Color.LTGRAY }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Second row of colors
        Row {
            ColorButton(
                color = Color.Red,
                isSelected = selectedColor == android.graphics.Color.RED,
                size = 40.dp,
                onSelect = { selectedColor = android.graphics.Color.RED }
            )

            ColorButton(
                color = Color.Blue,
                isSelected = selectedColor == android.graphics.Color.BLUE,
                size = 40.dp,
                onSelect = { selectedColor = android.graphics.Color.BLUE }
            )

            ColorButton(
                color = Color.Green,
                isSelected = selectedColor == android.graphics.Color.GREEN,
                size = 40.dp,
                onSelect = { selectedColor = android.graphics.Color.GREEN }
            )

            ColorButton(
                color = Color(0xFF8B4513), // Brown
                isSelected = selectedColor == 0xFF8B4513.toInt(),
                size = 40.dp,
                onSelect = { selectedColor = 0xFF8B4513.toInt() }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Third row of colors
        Row {
            ColorButton(
                color = Color(0xFFFF69B4), // Pink
                isSelected = selectedColor == 0xFFFF69B4.toInt(),
                size = 40.dp,
                onSelect = { selectedColor = 0xFFFF69B4.toInt() }
            )

            ColorButton(
                color = Color(0xFFFF8C00), // Dark Orange
                isSelected = selectedColor == 0xFFFF8C00.toInt(),
                size = 40.dp,
                onSelect = { selectedColor = 0xFFFF8C00.toInt() }
            )

            ColorButton(
                color = Color(0xFF800080), // Purple
                isSelected = selectedColor == 0xFF800080.toInt(),
                size = 40.dp,
                onSelect = { selectedColor = 0xFF800080.toInt() }
            )

            ColorButton(
                color = Color(0xFF008080), // Teal
                isSelected = selectedColor == 0xFF008080.toInt(),
                size = 40.dp,
                onSelect = { selectedColor = 0xFF008080.toInt() }
            )
        }
    }
}

@Composable
fun ColorButton(
    color: Color,
    isSelected: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .padding(4.dp)
            .background(color)
            .clip(CircleShape)
            .noRippleClickable(onClick = onSelect)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, Color.Black, CircleShape)
                } else {
                    Modifier.border(1.dp, Color.LightGray, CircleShape)
                }
            )
    )
}

private fun getPenDisplayName(penName: String): String {
    return when (penName) {
        "BALLPEN" -> "Ball Pen"
        "MARKER" -> "Marker"
        "FOUNTAIN" -> "Fountain Pen"
        else -> penName
    }
}