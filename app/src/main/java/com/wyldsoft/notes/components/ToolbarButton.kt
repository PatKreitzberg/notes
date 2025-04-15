package com.wyldsoft.notes.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    isEnabled: Boolean = true,
    onSelect: () -> Unit = {},
    imageVector: ImageVector? = null,
    text: String? = null,
    penColor: Color? = null,
    contentDescription: String = ""
) {
    val backgroundColor = when {
        !isEnabled -> Color.LightGray.copy(alpha = 0.5f)
        isSelected -> penColor ?: Color.Black
        else -> penColor ?: Color.Transparent
    }

    val contentColor = when {
        !isEnabled -> Color.Gray
        penColor == Color.Black || penColor == Color.DarkGray || isSelected -> Color.White
        else -> Color.Black
    }

    Box(
        Modifier
            .then(modifier)
            .let { mod ->
                if (isEnabled) {
                    mod.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        onSelect()
                    }
                } else {
                    mod
                }
            }
            .background(
                color = backgroundColor,
                shape = if (!isSelected) CircleShape else RectangleShape
            )
            .padding(7.dp)

    ) {
        if (imageVector != null) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = contentColor
            )
        }
        if (text != null) {
            Text(
                text = text,
                fontSize = 20.sp,
                color = contentColor
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
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onSelect()
            }
    ) {
        // Content goes here if needed
    }
}