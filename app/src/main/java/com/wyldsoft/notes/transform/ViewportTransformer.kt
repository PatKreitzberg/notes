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
        return Pair(scaledX, scaledY - scrollY)
    }

    /**
     * Transforms a point from view coordinates to page coordinates
     */
    fun viewToPageCoordinates(x: Float, y: Float): Pair<Float, Float> {
        // First adjust for scrolling
        val scrollAdjustedY = y + scrollY

        // Then invert the zoom transformation
        val pageX = zoomCenterX + (x - zoomCenterX) / zoomScale
        val pageY = zoomCenterY + (scrollAdjustedY - zoomCenterY) / zoomScale

        return Pair(pageX, pageY)
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

        // Only update if scale has changed significantly (avoid micro-changes)
        if (kotlin.math.abs(newScale - zoomScale) > 0.001f) {
            // Get the focus point in page coordinates before zoom changes
            val (pageFocusX, pageFocusY) = viewToPageCoordinates(centerX, centerY)

            // Update the zoom scale
            zoomScale = newScale

            // Update the zoom center (in page coordinates)
            zoomCenterX = pageFocusX
            zoomCenterY = pageFocusY

            // Show zoom indicator
            showZoomIndicator()

            // Notify about viewport change
            notifyViewportChanged()

            println("DEBUG: Zoom updated - scale=$zoomScale, center=($zoomCenterX, $zoomCenterY)")
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
        // Transform the rectangle from page coordinates to view coordinates
        val zoomOffsetX = (viewWidth / 2f) - (zoomCenterX * zoomScale)
        val zoomOffsetY = (viewHeight / 2f) - (zoomCenterY * zoomScale)

        val transformedLeft = rect.left * zoomScale + zoomOffsetX
        val transformedTop = (rect.top - scrollY) * zoomScale + zoomOffsetY
        val transformedRight = rect.right * zoomScale + zoomOffsetX
        val transformedBottom = (rect.bottom - scrollY) * zoomScale + zoomOffsetY

        // A rectangle is visible if ANY part of it is in the viewport
        return !(transformedRight < 0 || transformedLeft > viewWidth ||
                transformedBottom < 0 || transformedTop > viewHeight)
    }

    /**
     * Calculates the scroll amount based on gesture
     * @param startY start Y position of the gesture
     * @param endY end Y position of the gesture
     * @param duration duration of the gesture in milliseconds
     * @return recommended scroll amount
     */
    fun calculateScrollAmount(startY: Float, endY: Float, duration: Long): Float {
        val delta = startY - endY // Reverse direction: up swipe = scroll down
        println("scroll calculateScrollAmount delta $delta")
        // Base scroll amount is proportional to gesture distance
        val baseScrollAmount = delta * (viewHeight / 3f) / viewHeight

        // Adjust for speed - faster swipes get more scroll distance
        val speedFactor = if (duration > 0) {
            val speed = Math.abs(delta) / duration
            val normalizedSpeed = (speed * 5).coerceIn(0.8f, 4.0f)
            normalizedSpeed
        } else {
            1.0f
        }

        // Divide by zoom scale to adjust for zoomed view
        return (baseScrollAmount * speedFactor) / zoomScale
    }

    /**
     * Returns the visible portion of a rect in view coordinates
     */
    fun getVisibleRect(rect: RectF): RectF? {
        if (!isRectVisible(rect)) return null

        // Transform to view coordinates with zoom
        val zoomOffsetX = (viewWidth / 2f) - (zoomCenterX * zoomScale)
        val zoomOffsetY = (viewHeight / 2f) - (zoomCenterY * zoomScale)

        val viewLeft = rect.left * zoomScale + zoomOffsetX
        val viewTop = (max(rect.top, scrollY) - scrollY) * zoomScale + zoomOffsetY
        val viewRight = rect.right * zoomScale + zoomOffsetX
        val viewBottom = (min(rect.bottom, scrollY + viewHeight / zoomScale) - scrollY) * zoomScale + zoomOffsetY

        return RectF(viewLeft, viewTop, viewRight, viewBottom)
    }

    /**
     * Returns the current viewport rect in page coordinates
     */
    fun getCurrentViewportInPageCoordinates(): RectF {
        // Calculate the actual viewport in page coordinates considering zoom
        val (topLeftX, topLeftY) = viewToPageCoordinates(0f, 0f)
        val (bottomRightX, bottomRightY) = viewToPageCoordinates(viewWidth.toFloat(), viewHeight.toFloat())

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
    fun scroll(deltaY: Float, shouldAnimate: Boolean = false): Boolean {
        // Calculate new scrollY position, adjust for zoom
        val adjustedDelta = deltaY
        val newScrollY = scrollY + adjustedDelta

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