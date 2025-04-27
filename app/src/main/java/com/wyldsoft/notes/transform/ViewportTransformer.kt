package com.wyldsoft.notes.transform

import android.content.Context
import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.utils.convertDpToPixel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * Handles viewport transformations including scrolling and coordinates translation.
 */
class ViewportTransformer(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val viewWidth: Int,
    private val viewHeight: Int
) {
    // Viewport position
    var scrollY by mutableStateOf(0f)

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

    /**
     * Transforms a point from page coordinates to view coordinates
     */
    fun pageToViewCoordinates(x: Float, y: Float): Pair<Float, Float> {
        return Pair(x, y - scrollY)
    }

    /**
     * Transforms a point from view coordinates to page coordinates
     */
    fun viewToPageCoordinates(x: Float, y: Float): Pair<Float, Float> {
        return Pair(x, y + scrollY)
    }

    /**
     * Determines if a rect in page coordinates is visible in the current viewport
     */
    fun isRectVisible(rect: RectF): Boolean {
        // Transform the rectangle from page coordinates to view coordinates
        val transformedBottom = rect.bottom - scrollY
        val transformedTop = rect.top - scrollY

        // Debug logging to understand the transformation
        println("scroll isRectVisible: original rect.bottom= ${rect.bottom} rect.top=${rect.top}")
        println("scroll isRectVisible: transformedTop=$transformedTop, transformedBottom=$transformedBottom, scrollY=$scrollY, viewHeight=$viewHeight")

        // A rectangle is visible if ANY part of it is in the viewport
        // If the bottom is above the viewport (negative) OR top is below the viewport (> viewHeight), it's not visible
        return !(transformedBottom < 0 || transformedTop > viewHeight)
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
        println("scroll delta $delta")
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

        println("scroll base scroll * speed ${baseScrollAmount*speedFactor}")

        return baseScrollAmount * speedFactor
    }

    /**
     * Returns the visible portion of a rect in view coordinates
     */
    fun getVisibleRect(rect: RectF): RectF? {
        if (!isRectVisible(rect)) return null

        val visibleRect = RectF(
            rect.left,
            max(rect.top, scrollY),
            rect.right,
            min(rect.bottom, scrollY + viewHeight)
        )

        visibleRect.offset(0f, -scrollY)
        return visibleRect
    }

    /**
     * Returns the current viewport rect in page coordinates
     */
    fun getCurrentViewportInPageCoordinates(): RectF {
        return RectF(
            0f,
            scrollY,
            viewWidth.toFloat(),
            scrollY + viewHeight
        )
    }

    /**
     * Updates the document height
     */
    fun updateDocumentHeight(newHeight: Int) {
        documentHeight = max(newHeight, viewHeight)
    }

    /**
     * Scrolls the viewport by the specified delta
     */
    fun scroll(deltaY: Float, shouldAnimate: Boolean = false): Boolean {
        // Calculate new scrollY position
        val newScrollY = scrollY + deltaY

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
        val viewportBottom = newScrollY + viewHeight
        if (viewportBottom > documentHeight - bottomPadding) {
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


