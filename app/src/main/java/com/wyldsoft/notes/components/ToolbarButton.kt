package com.wyldsoft.notes.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyldsoft.notes.utils.noRippleClickable

@Composable
fun ToolbarButton(
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onSelect: () -> Unit = {},
    imageVector: ImageVector? = null,
    text: String? = null,
    penColor: Color? = null,
    contentDescription: String = ""
) {
    Box(
        Modifier
            .then(modifier)
            .noRippleClickable {
                onSelect()
            }
            .background(
                color = if (isSelected) penColor ?: Color.Black else penColor ?: Color.Transparent,
                shape = if (!isSelected) CircleShape else RectangleShape
            )
            .padding(7.dp)

    ) {
        if (imageVector != null) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = if (penColor == Color.Black || penColor == Color.DarkGray || isSelected)
                    Color.White
                else if (isSelected)
                    Color.White
                else
                    Color.Black
            )
        }
        if (text != null) {
            Text(
                text = text,
                fontSize = 20.sp,
                color = if (isSelected) Color.White else Color.Black
            )
        }
    }
}

@Composable
fun ColorButton(
    color: Color,
    isSelected: Boolean = false,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .padding(4.dp)
            .background(color)
            .border(2.dp, if (isSelected) Color.Black else Color.Transparent)
            .noRippleClickable(onSelect)
    ) {
        // Content goes here if needed
    }
}

