// app/src/main/java/com/wyldsoft/notes/transform/ViewportTransformer.kt
package com.wyldsoft.notes.transform

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.classes.drawing.DrawingManager
import com.wyldsoft.notes.utils.convertDpToPixel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.apache.commons.lang3.math.NumberUtils.toDouble
import java.lang.Math.pow
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Handles viewport transformations including scrolling and coordinates translation
 * between page space and view space.
 */
class ViewportTransformer(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val viewWidth: Int,
    private val viewHeight: Int
) {
    // Viewport position relative to the page (top-left corner)
    var scrollY by mutableStateOf(0f)

    // Current document height
    var documentHeight by mutableStateOf(viewHeight)

    // Minimum distance from bottom before auto-extending page
    private val bottomPadding = convertDpToPixel(200.dp, context)

    // Signals when viewport changes
    val viewportChanged = MutableSharedFlow<Unit>()

    // The minimum scroll value is always 0 (can't scroll above the top)
    private val minScrollY = 0f

    private var lastRealTimeUpdateTime = 0L
    private val minUpdateInterval = 100L // Minimum time between updates for e-ink display
    private var lastDirectScrollUpdate = 0L
    private val directScrollUpdateInterval = 100L // Update every 100ms during direct scrolling



    // Scroll indicator visibility timeout
    private val scrollIndicatorTimeout = 1500L // 1.5 seconds
    var isScrollIndicatorVisible by mutableStateOf(false)
    private var scrollIndicatorJob: Job? = null

    // For scroll update intervals
    private var scrollUpdateJob: Job? = null
    private val scrollUpdateInterval = 50L // As specified, 250ms
    private var scrollAnimationJob: Job? = null
    private var targetScrollY = 0f
    private var scrollAnimationInProgress = false

    // For top boundary indicator
    var isAtTopBoundary by mutableStateOf(false)
    private var topBoundaryJob: Job? = null

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
        val transformedTop = rect.top - scrollY
        val transformedBottom = rect.bottom - scrollY

        // Visible if any part of the rectangle intersects the viewport
        return !(transformedBottom < 0 || transformedTop > viewHeight)
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

        // Transform to view coordinates
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
     * Updates the document height and ensures it's at least as tall as the viewport
     */
    fun updateDocumentHeight(newHeight: Int) {
        documentHeight = max(newHeight, viewHeight)
    }

    /**
     * Scrolls the viewport by the specified delta
     * @param deltaY Amount to scroll (positive = down, negative = up)
     * @param shouldAnimate Whether to animate the scroll with periodic updates
     * @return true if scroll was performed, false if at boundary and couldn't scroll
     */
    fun scroll(deltaY: Float, shouldAnimate: Boolean = false): Boolean {
        // Calculate new scrollY position
        val newScrollY = scrollY + deltaY

        // Check top boundary
        if (newScrollY < minScrollY) {
            if (scrollY > minScrollY) {
                // We can scroll to the top, but not beyond
                scrollY = minScrollY
                showTopBoundaryIndicator()
                showScrollIndicator()
                notifyViewportChanged(true) // Force update
                return true
            } else {
                // Already at top, show indicator
                showTopBoundaryIndicator()
                return false
            }
        }

        // Check if we need to extend the document
        val viewportBottom = newScrollY + viewHeight
        if (viewportBottom > documentHeight - bottomPadding) {
            // Auto-extend the document
            documentHeight = (viewportBottom + bottomPadding).toInt()
        }

        // Update scroll position
        scrollY = newScrollY
        showScrollIndicator()

        // For direct scrolling, we throttle updates to avoid overwhelming the e-ink display
        val currentTime = System.currentTimeMillis()
        val shouldUpdate = currentTime - lastDirectScrollUpdate >= directScrollUpdateInterval

        if (shouldUpdate) {
            lastDirectScrollUpdate = currentTime
            notifyViewportChanged(!shouldAnimate)
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
            delay(scrollIndicatorTimeout)
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
    private fun notifyViewportChanged(forceUpdate: Boolean = false) {
        coroutineScope.launch {
            viewportChanged.emit(Unit)

            if (forceUpdate) {
                // Force UI refresh for important updates
                DrawingManager.refreshUi.emit(Unit)
            }
        }
    }

    /**
     * Starts periodic viewport updates for animation
     */
    private fun startScrollUpdateAnimation() {
        // Cancel previous job if it exists
        scrollUpdateJob?.cancel()

        // Start a new update job
        scrollUpdateJob = coroutineScope.launch {
            notifyViewportChanged() // Immediate first update
            delay(scrollUpdateInterval)
            notifyViewportChanged() // Second update after delay
        }
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
     * Scrolls the viewport by the specified delta with smooth animation
     * @param deltaY Amount to scroll (positive = down, negative = up)
     */
    fun scrollSmooth(deltaY: Float): Boolean {
        // Calculate target scrollY position
        val newTargetScrollY = scrollY + deltaY

        // Check top boundary
        if (newTargetScrollY < minScrollY) {
            if (scrollY > minScrollY) {
                // We can scroll to the top, but not beyond
                targetScrollY = minScrollY
            } else {
                // Already at top, show indicator
                showTopBoundaryIndicator()
                return false
            }
        } else {
            targetScrollY = newTargetScrollY
        }

        // Check if we need to extend the document
        val viewportBottom = targetScrollY + viewHeight
        if (viewportBottom > documentHeight - bottomPadding) {
            // Auto-extend the document
            documentHeight = (viewportBottom + bottomPadding).toInt()
        }

        // Show scroll indicator
        showScrollIndicator()

        // Cancel any existing animation
        scrollAnimationJob?.cancel()

        // Start smooth scrolling animation
        scrollAnimationJob = coroutineScope.launch {
            scrollAnimationInProgress = true

            val startScrollY = scrollY
            val distance = targetScrollY - startScrollY

            // Calculate how many steps we need based on distance and update interval
            // We want scrolling to take between 200-500ms depending on distance
            val duration = (200 + Math.abs(distance) / 3).coerceIn(200.0f, 500.0f).toLong()
            val steps = (duration / scrollUpdateInterval).coerceAtLeast(1)

            for (step in 0 until steps) {
                // Calculate progress (0.0 to 1.0)
                val progress = (step + 1) / steps.toFloat()

                // Apply easing function for more natural scrolling
                val easedProgress = easeInOutCubic(progress)

                // Update actual scroll position
                scrollY = startScrollY + (distance * easedProgress)

                // Notify about viewport change
                notifyViewportChanged()

                // Wait for next update
                if (step < steps - 1) {
                    delay(scrollUpdateInterval)
                }
            }

            // Ensure we end exactly at target
            scrollY = targetScrollY
            notifyViewportChanged()

            scrollAnimationInProgress = false
        }

        return true
    }

    private fun easeInOutCubic(x: Float): Float {
        return if (x < 0.5f) {
            4 * x * x * x
        } else {
            1 - ((-2 * x + 2).pow(3)) / 2
        }
    }

}