package com.wyldsoft.notes.refreshingScreen

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.pagination.PaginationManager
import com.wyldsoft.notes.utils.convertDpToPixel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Manages scroll operations and document boundaries.
 * Handles scroll constraints, indicators, and document height management.
 */
class ScrollManager(
    private val context: Context,
    private val coordinateTransformer: CoordinateTransformer,
    private val paginationManager: PaginationManager,
    private val coroutineScope: CoroutineScope,
    private val viewWidth: Int,
    private val viewHeight: Int
) {
    // Document dimensions
    var documentHeight by mutableStateOf(viewHeight)
        private set

    // Minimum distance from bottom before auto-extending page
    private val bottomPadding = convertDpToPixel(200.dp, context)

    // Boundary limits
    private val minScrollY = 0f
    private val minScrollX = 0f

    // UI indicators
    var isScrollIndicatorVisible by mutableStateOf(false)
        private set
    var isAtTopBoundary by mutableStateOf(false)
        private set

    private var scrollIndicatorJob: Job? = null
    private var topBoundaryJob: Job? = null

    // Throttling
    private var lastUpdateTime = 0L
    private val updateInterval = 100L

    /**
     * Scrolls the viewport by the specified delta
     */
    fun scroll(deltaX: Float, deltaY: Float): Boolean {
        val newScrollY = coordinateTransformer.scrollY + deltaY

        // Check top boundary
        if (newScrollY < minScrollY) {
            if (coordinateTransformer.scrollY > minScrollY) {
                coordinateTransformer.setScroll(coordinateTransformer.scrollX, minScrollY)
                showTopBoundaryIndicator()
                showScrollIndicator()
                return true
            } else {
                showTopBoundaryIndicator()
                return false
            }
        }

        // Handle horizontal scrolling based on zoom
        val adjustedDeltaX = if (coordinateTransformer.zoomScale > 1.0f) deltaX else 0f
        val newScrollX = calculateConstrainedScrollX(coordinateTransformer.scrollX + adjustedDeltaX)

        // Handle document height extension
        handleDocumentExtension(newScrollY)

        // Update scroll position
        coordinateTransformer.setScroll(newScrollX, newScrollY)
        showScrollIndicator()

        // Throttle updates
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime >= updateInterval) {
            lastUpdateTime = currentTime
            return true
        }

        return true
    }

    /**
     * Scrolls to a specific y-position in the document
     */
    fun scrollToPosition(yPosition: Float) {
        val targetY = yPosition - (viewHeight / (2 * coordinateTransformer.zoomScale))
        val boundedY = targetY.coerceIn(0f, documentHeight - viewHeight.toFloat())

        coordinateTransformer.setScroll(coordinateTransformer.scrollX, boundedY)
        showScrollIndicator()
    }

    /**
     * Updates the document height
     */
    fun updateDocumentHeight(newHeight: Int) {
        documentHeight = max(newHeight, viewHeight)
        paginationManager.setDocumentHeight(documentHeight.toFloat())
    }

    /**
     * Calculates constrained horizontal scroll position
     */
    private fun calculateConstrainedScrollX(proposedScrollX: Float): Float {
        return if (coordinateTransformer.zoomScale > 1.0f) {
            val contentWidth = viewWidth * coordinateTransformer.zoomScale
            val excessWidth = contentWidth - viewWidth

            val proposedViewport = coordinateTransformer.calculateViewportWithScroll(
                proposedScrollX, coordinateTransformer.scrollY
            )

            var constrainedScrollX = proposedScrollX

            // Prevent scrolling beyond document edges
            if (proposedViewport.left < 0f) {
                constrainedScrollX -= proposedViewport.left * coordinateTransformer.zoomScale
            } else if (proposedViewport.right > viewWidth) {
                constrainedScrollX += (viewWidth - proposedViewport.right) * coordinateTransformer.zoomScale
            }

            // Final constraint check
            val maxScrollX = excessWidth / 2
            constrainedScrollX.coerceIn(-maxScrollX, maxScrollX)
        } else {
            0f
        }
    }

    /**
     * Handles automatic document height extension
     */
    private fun handleDocumentExtension(newScrollY: Float) {
        val viewportBottom = newScrollY + viewHeight / coordinateTransformer.zoomScale

        if (paginationManager.isPaginationEnabled) {
            val currentPageIndex = paginationManager.getPageIndexForY(viewportBottom)
            val bottomMostPageY = paginationManager.getExclusionZoneBottomY(currentPageIndex)

            if (viewportBottom > bottomMostPageY && bottomMostPageY > documentHeight - bottomPadding) {
                documentHeight = (bottomMostPageY + paginationManager.pageHeightPx).toInt()
            }
        } else if (viewportBottom > documentHeight - bottomPadding) {
            documentHeight = (viewportBottom + bottomPadding).toInt()
        }
    }

    /**
     * Shows scroll indicator temporarily
     */
    private fun showScrollIndicator() {
        isScrollIndicatorVisible = true

        scrollIndicatorJob?.cancel()
        scrollIndicatorJob = coroutineScope.launch {
            delay(1500)
            isScrollIndicatorVisible = false
        }
    }

    /**
     * Shows top boundary indicator temporarily
     */
    private fun showTopBoundaryIndicator() {
        isAtTopBoundary = true

        topBoundaryJob?.cancel()
        topBoundaryJob = coroutineScope.launch {
            delay(800)
            isAtTopBoundary = false
        }
    }
}