package com.wyldsoft.notes.utils

import android.content.Context
import android.util.TypedValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp

fun Modifier.noRippleClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    if (enabled) {
        clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
            onClick()
        }
    } else {
        this
    }
}

fun convertDpToPixel(dp: Dp, context: Context): Float {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.value,
        context.resources.displayMetrics
    )
}

data class SimplePointF(val x: Float, val y: Float)