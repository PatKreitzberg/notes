// app/src/main/java/com/wyldsoft/notes/components/ScrollIndicator.kt
package com.wyldsoft.notes.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.refreshingScreen.ViewportTransformer

@Composable
fun ScrollIndicator(
    viewportTransformer: ViewportTransformer,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopEnd
    ) {
        AnimatedVisibility(
            visible = viewportTransformer.isScrollIndicatorVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            // Calculate the scroll indicator position and size
            val viewportHeight = viewportTransformer.getCurrentViewportInPageCoordinates().height()
            val documentHeight = viewportTransformer.documentHeight.toFloat()

            // Only show if document is taller than viewport
            if (documentHeight > viewportHeight) {
                val scrollPercent = viewportTransformer.scrollY / (documentHeight - viewportHeight)
                val indicatorHeight = (viewportHeight / documentHeight * viewportHeight)
                    .coerceAtLeast(40f) // Minimum indicator size

                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                    .width(5.dp)
                    .fillMaxHeight(indicatorHeight / viewportHeight)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(Color.Gray.copy(alpha = 0.6f))
                    .align(Alignment.TopEnd)
                )
            }
        }
    }
}