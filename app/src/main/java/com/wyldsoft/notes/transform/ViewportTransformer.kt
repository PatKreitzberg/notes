package com.wyldsoft.notes.transform

import android.content.Context
import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.pagination.PaginationManager
import com.wyldsoft.notes.utils.convertDpToPixel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import com.wyldsoft.notes.settings.PaperSize
import com.wyldsoft.notes.settings.SettingsRepository


/**
 * Handles viewport transformations including scrolling, zooming, and coordinates translation.
 */
class ViewportTransformer(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    val viewWidth: Int,
    val viewHeight: Int,
    val settingsRepository: SettingsRepository
) {
    // Viewport position
    var scrollY by mutableStateOf(0f)
    var scrollX by mutableStateOf(0f)

    // Zoom state
    var zoomScale by mutableStateOf(1.0f)
    private var zoomCenterX by mutableStateOf(0f)
    private var zoomCenterY by mutableStateOf(0f)
    private val minZoom = 1.0f
    private val maxZoom = 2.0f

    // Zoom indicator visibility
    var isZoomIndicatorVisible by mutableStateOf(false)
    private var zoomIndicatorJob: Job? = null

    // Document dimensions
    var documentHeight by mutableStateOf(viewHeight)

    // Minimum distance from bottom before auto-extending page
    private val bottomPadding = convertDpToPixel(200.dp, context)

    // Signals when viewport changes
    val viewportChanged = MutableSharedFlow<Unit>()

    // The minimum scroll value
    private val minScrollY = 0f
    private val minScrollX = 0f

    // UI indicators
    var isScrollIndicatorVisible by mutableStateOf(false)
    private var scrollIndicatorJob: Job? = null
    var isAtTopBoundary by mutableStateOf(false)
    private var topBoundaryJob: Job? = null

    // For throttling updates
    private var lastUpdateTime = 0L
    private val updateInterval = 100L // 100ms

    private lateinit var paginationManager: PaginationManager
    init {
        paginationManager = PaginationManager(context)
    }

    fun getPaginationManager(): PaginationManager {
        return paginationManager
    }

    /**
     * Transforms a point from page coordinates to view coordinates
     */
    fun pageToViewCoordinates(x: Float, y: Float): Pair<Float, Float> {
        // Apply scale relative to the zoom center
        // The zoom center is in page coordinates
        val scaledX = (x - zoomCenterX) * zoomScale + zoomCenterX
        val scaledY = (y - zoomCenterY) * zoomScale + zoomCenterY

        // Apply scroll offset
        return Pair(scaledX - scrollX, scaledY - scrollY)
    }

    /**
     * Transforms a point from view coordinates to page coordinates
     */
    fun viewToPageCoordinates(x: Float, y: Float): Pair<Float, Float> {
        // First adjust for scrolling
        val scrollAdjustedY = y + scrollY
        val scrollAdjustedX = x + scrollX


        // Then invert the zoom transformation
        val pageX = zoomCenterX + (scrollAdjustedX - zoomCenterX) / zoomScale
        val pageY = zoomCenterY + (scrollAdjustedY - zoomCenterY) / zoomScale

        return Pair(pageX, pageY)
    }

    /**
     * Calculates what the viewport would be with the given scroll values
     */
    private fun calculateViewportWithScroll(proposedScrollX: Float, proposedScrollY: Float): RectF {
        // Function that mimics viewToPageCoordinates but uses provided scroll values
        fun viewToPageWithScroll(x: Float, y: Float, scrollX: Float, scrollY: Float): Pair<Float, Float> {
            // First adjust for scrolling
            val scrollAdjustedY = y + scrollY
            val scrollAdjustedX = x + scrollX

            // Then invert the zoom transformation
            val pageX = zoomCenterX + (scrollAdjustedX - zoomCenterX) / zoomScale
            val pageY = zoomCenterY + (scrollAdjustedY - zoomCenterY) / zoomScale

            return Pair(pageX, pageY)
        }

        // Calculate viewport corners with the proposed scroll values
        val (topLeftX, topLeftY) = viewToPageWithScroll(0f, 0f, proposedScrollX, proposedScrollY)
        val (bottomRightX, bottomRightY) = viewToPageWithScroll(viewWidth.toFloat(), viewHeight.toFloat(), proposedScrollX, proposedScrollY)

        return RectF(topLeftX, topLeftY, bottomRightX, bottomRightY)
    }

    /**
     * Updates the zoom level around the specified center point
     * @param scale The new zoom scale (1.0 = 100%)
     * @param centerX The x-coordinate of the zoom center in view coordinates
     * @param centerY The y-coordinate of the zoom center in view coordinates
     */
    fun zoom(scale: Float, centerX: Float, centerY: Float) {
        // Constrain zoom scale
        val newScale = scale.coerceIn(minZoom, maxZoom)

        // Only update if scale has changed significantly
        if (kotlin.math.abs(newScale - zoomScale) > 0.001f) {
            // Store the previous scale for calculations
            val previousScale = zoomScale

            // Get the focus point in page coordinates before zoom changes
            val (pageFocusX, pageFocusY) = viewToPageCoordinates(centerX, centerY)

            // Update the zoom scale
            zoomScale = newScale

            // Update the zoom center (in page coordinates)
            zoomCenterX = pageFocusX
            zoomCenterY = pageFocusY

            // Adjust scrollX to maintain horizontal position relative to focus point
            // Only if zoomed in
            if (zoomScale > 1.0f) {
                // Calculate how much the content width has changed
                val contentWidthDelta = viewWidth * (zoomScale - previousScale)

                // Calculate the horizontal offset relative to view center
                val horizontalFocusOffset = centerX - (viewWidth / 2)

                // Calculate the proportion of the focus point from the center (0 to 1)
                val focusRatio = horizontalFocusOffset / (viewWidth / 2)

                // Apply an offset that increases with zoom level and focus distance from center
                val scrollXAdjustment = contentWidthDelta * focusRatio * 0.5f

                // Calculate the proposed new scrollX
                var proposedScrollX = scrollX + scrollXAdjustment

                // Calculate what the viewport would be with this scrollX
                val proposedViewport = calculateViewportWithScroll(proposedScrollX, scrollY)

                // Check if viewport would go beyond document edges
                if (proposedViewport.left < 0) {
                    // Viewport would go beyond left edge
                    proposedScrollX -= proposedViewport.left * zoomScale
                }

                if (proposedViewport.right > viewWidth) {
                    // Viewport would go beyond right edge
                    proposedScrollX += (viewWidth - proposedViewport.right) * zoomScale
                }

                // Apply the constrained scrollX
                scrollX = proposedScrollX

                // Additional bounds check from the original code
                val contentWidth = viewWidth * zoomScale
                val excessWidth = contentWidth - viewWidth
                val maxScrollX = excessWidth / 2

                scrollX = scrollX.coerceIn(-maxScrollX, maxScrollX)
            } else {
                // Reset horizontal scroll when at normal zoom
                scrollX = 0f
            }

            // Show zoom indicator
            showZoomIndicator()

            // Notify about viewport change
            notifyViewportChanged()
        }
    }

    /**
     * Reset zoom to 100%
     */
    fun resetZoom() {
        zoomScale = 1.0f
        showZoomIndicator()
        notifyViewportChanged()
    }

    /**
     * Gets the current zoom scale as a percentage string
     */
    fun getZoomPercentage(): String {
        return "${(zoomScale * 100).toInt()}%"
    }

    /**
     * Updates the paper size
     */
    fun updatePaperSizeState(paperSize: PaperSize) {
        paginationManager.updatePaperSize(paperSize)

        // Notify that viewport has changed
        notifyViewportChanged()
    }

    /**
     * Determines if a rect in page coordinates is visible in the current viewport
     */
    fun isRectVisible(rect: RectF): Boolean {
        // Get the current viewport in page coordinates
        val viewport = getCurrentViewportInPageCoordinates()

        // Simple bounding box intersection check
        return RectF.intersects(viewport, rect)
    }

    /**
     * Returns the current viewport rect in page coordinates
     */
    fun getCurrentViewportInPageCoordinates(): RectF {
        // Transform the view corners to page coordinates
        val (topLeftX, topLeftY)         = viewToPageCoordinates(0f, 0f)
        val (bottomRightX, bottomRightY) = viewToPageCoordinates(viewWidth.toFloat(), viewHeight.toFloat())

        println("getCurrentViewport ($topLeftX, $topLeftY)  ($bottomRightX, $bottomRightY)")

        return RectF(
            topLeftX,
            topLeftY,
            bottomRightX,
            bottomRightY
        )
    }

    /**
     * Updates the document height
     */
    fun updateDocumentHeight(newHeight: Int) {
        documentHeight = max(newHeight, viewHeight)
        // Make sure to update pagination manager
        paginationManager.setDocumentHeight(documentHeight.toFloat())
    }

    /**
     * Scrolls the viewport by the specified delta
     */
    fun scroll(deltaX: Float, deltaY: Float, shouldAnimate: Boolean = false): Boolean {
        // Calculate new scrollY position, adjust for zoom
        val adjustedDeltaY = deltaY
        val newScrollY = scrollY + adjustedDeltaY

        // Check top boundary
        if (newScrollY < minScrollY) {
            if (scrollY > minScrollY) {
                scrollY = minScrollY
                showTopBoundaryIndicator()
                showScrollIndicator()
                notifyViewportChanged()
                return true
            } else {
                showTopBoundaryIndicator()
                return false
            }
        }

        val adjustedDeltaX = deltaX
        var newScrollX = scrollX + adjustedDeltaX

        // Handle horizontal scrolling based on zoom
        if (zoomScale > 1.0f) {
            // Calculate how much the content has expanded beyond the view width
            val contentWidth = viewWidth * zoomScale
            val excessWidth = contentWidth - viewWidth

            // Calculate the page coordinates of the left and right edges after the proposed scroll
            val (leftEdgePageX, _) = viewToPageCoordinates(0f, 0f)
            val (rightEdgePageX, _) = viewToPageCoordinates(viewWidth.toFloat(), 0f)

            // Prevent scrolling beyond the document edges (horizontal constraints)
            if (leftEdgePageX < 0f) {
                // Left edge of view would show content beyond the document's left edge (x=0)
                // Adjust scrollX to align the left edge of the document with the left edge of the view
                val adjustment = leftEdgePageX * zoomScale
                newScrollX -= adjustment
            } else if (rightEdgePageX > viewWidth) {
                // Right edge of view would show content beyond the document's right edge
                // Adjust scrollX to align the right edge of the document with the right edge of the view
                val adjustment = (rightEdgePageX - viewWidth) * zoomScale
                newScrollX += adjustment
            }

            // Ensure scrollX stays within bounds (this is the existing check)
            val maxScrollX = excessWidth / 2
            newScrollX = newScrollX.coerceIn(-maxScrollX, maxScrollX)
        } else {
            // When not zoomed, no horizontal scrolling
            newScrollX = 0f
        }

        // Check if we need to extend the document
        val viewportBottom = newScrollY + viewHeight / zoomScale

        // When pagination is enabled, we might need to extend beyond current pages
        if (paginationManager.isPaginationEnabled) {
            val currentPageIndex = paginationManager.getPageIndexForY(viewportBottom)
            val bottomMostPageY = paginationManager.getExclusionZoneBottomY(currentPageIndex)

            if (viewportBottom > bottomMostPageY && bottomMostPageY > documentHeight - bottomPadding) {
                // Extend to include the next page
                documentHeight = (bottomMostPageY + paginationManager.pageHeightPx).toInt()
            }
        } else if (viewportBottom > documentHeight - bottomPadding) {
            // Original behavior when pagination is disabled
            documentHeight = (viewportBottom + bottomPadding).toInt()
        }

        // Update scroll position
        if (zoomScale != 1.0f) {
            scrollX = newScrollX
        }
        scrollY = newScrollY

        showScrollIndicator()

        // Throttle updates
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime >= updateInterval) {
            lastUpdateTime = currentTime
            notifyViewportChanged()
        }

        return true
    }

    /**
     * Updates the pagination manager's state
     */
    fun updatePaginationState(enabled: Boolean) {
        paginationManager.isPaginationEnabled = enabled

        // Recalculate document height based on pagination
        if (enabled) {
            // If pagination is enabled, extend document to include at least one full page
            documentHeight = paginationManager.getExclusionZoneBottomY(0).toInt()
        }

        // Notify that viewport has changed
        notifyViewportChanged()
    }

    /**
     * Returns the context used to create this transformer
     */
    fun getContext(): Context {
        return context
    }

    /**
     * Shows the scroll indicator and hides it after a delay
     */
    private fun showScrollIndicator() {
        isScrollIndicatorVisible = true

        // Cancel previous job if it exists
        scrollIndicatorJob?.cancel()

        // Schedule hiding the indicator
        scrollIndicatorJob = coroutineScope.launch {
            delay(1500) // 1.5 second timeout
            isScrollIndicatorVisible = false
        }
    }

    /**
     * Shows the zoom indicator and hides it after a delay
     */
    private fun showZoomIndicator() {
        isZoomIndicatorVisible = true

        // Cancel previous job if it exists
        zoomIndicatorJob?.cancel()

        // Schedule hiding the indicator
        zoomIndicatorJob = coroutineScope.launch {
            delay(1500) // 1.5 second timeout
            isZoomIndicatorVisible = false
        }
    }

    /**
     * Shows the top boundary indicator and hides it after a delay
     */
    private fun showTopBoundaryIndicator() {
        isAtTopBoundary = true

        // Cancel previous job if it exists
        topBoundaryJob?.cancel()

        // Schedule hiding the indicator
        topBoundaryJob = coroutineScope.launch {
            delay(800) // Show for 800ms
            isAtTopBoundary = false
        }
    }

    /**
     * Sends viewport change notification
     */
    private fun notifyViewportChanged() {
        coroutineScope.launch {
            viewportChanged.emit(Unit)
            // We still need to ensure the UI refreshes appropriately
            com.wyldsoft.notes.classes.drawing.DrawingManager.refreshUi.emit(Unit)
        }
    }
}