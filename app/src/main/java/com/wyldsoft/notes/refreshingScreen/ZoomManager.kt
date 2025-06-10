package com.wyldsoft.notes.refreshingScreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Manages zoom operations and zoom state.
 * Handles zoom constraints, indicators, and transformations.
 */
class ZoomManager(
    private val coordinateTransformer: CoordinateTransformer,
    private val scrollManager: ScrollManager,
    private val coroutineScope: CoroutineScope,
    private val viewWidth: Int
) {
    private val minZoom = 1.0f
    private val maxZoom = 2.0f

    // Zoom indicator state
    var isZoomIndicatorVisible by mutableStateOf(false)
        private set
    private var zoomIndicatorJob: Job? = null

    /**
     * Gets current zoom scale
     */
    val zoomScale: Float
        get() = coordinateTransformer.zoomScale

    /**
     * Updates the zoom level around the specified center point
     */
    fun zoom(scale: Float, centerX: Float, centerY: Float) {
        val newScale = scale.coerceIn(minZoom, maxZoom)

        // Only update if scale has changed significantly
        if (abs(newScale - zoomScale) > 0.001f) {
            val previousScale = zoomScale

            // Get focus point in page coordinates before zoom changes
            val (pageFocusX, pageFocusY) = coordinateTransformer.viewToPageCoordinates(centerX, centerY)

            // Update zoom in coordinate transformer
            coordinateTransformer.updateZoom(newScale, pageFocusX, pageFocusY)

            // Adjust horizontal scroll for zoom
            adjustScrollForZoom(newScale, previousScale, centerX)

            showZoomIndicator()
        }
    }

    /**
     * Resets zoom to 100%
     */
    fun resetZoom() {
        coordinateTransformer.updateZoom(1.0f, 0f, 0f)
        coordinateTransformer.setScroll(0f, coordinateTransformer.scrollY)
        showZoomIndicator()
    }

    /**
     * Gets the current zoom scale as a percentage string
     */
    fun getZoomPercentage(): String {
        return "${(zoomScale * 100).toInt()}%"
    }

    /**
     * Adjusts scroll position when zoom changes
     */
    private fun adjustScrollForZoom(newScale: Float, previousScale: Float, centerX: Float) {
        if (newScale > 1.0f) {
            // Calculate scroll adjustment for zoom
            val contentWidthDelta = viewWidth * (newScale - previousScale)
            val horizontalFocusOffset = centerX - (viewWidth / 2)
            val focusRatio = horizontalFocusOffset / (viewWidth / 2)
            val scrollXAdjustment = contentWidthDelta * focusRatio * 0.5f

            var proposedScrollX = coordinateTransformer.scrollX + scrollXAdjustment

            // Constrain scroll to viewport bounds
            val proposedViewport = coordinateTransformer.calculateViewportWithScroll(
                proposedScrollX, coordinateTransformer.scrollY
            )

            if (proposedViewport.left < 0) {
                proposedScrollX -= proposedViewport.left * newScale
            }

            if (proposedViewport.right > viewWidth) {
                proposedScrollX += (viewWidth - proposedViewport.right) * newScale
            }

            // Final bounds check
            val contentWidth = viewWidth * newScale
            val excessWidth = contentWidth - viewWidth
            val maxScrollX = excessWidth / 2

            proposedScrollX = proposedScrollX.coerceIn(-maxScrollX, maxScrollX)
            coordinateTransformer.setScroll(proposedScrollX, coordinateTransformer.scrollY)
        } else {
            // Reset horizontal scroll when at normal zoom
            coordinateTransformer.setScroll(0f, coordinateTransformer.scrollY)
        }
    }

    /**
     * Shows the zoom indicator temporarily
     */
    private fun showZoomIndicator() {
        isZoomIndicatorVisible = true

        zoomIndicatorJob?.cancel()
        zoomIndicatorJob = coroutineScope.launch {
            delay(1500)
            isZoomIndicatorVisible = false
        }
    }
}