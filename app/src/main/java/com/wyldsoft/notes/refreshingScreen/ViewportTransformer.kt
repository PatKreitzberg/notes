package com.wyldsoft.notes.refreshingScreen

import android.content.Context
import android.graphics.RectF
import com.wyldsoft.notes.pagination.PaginationManager
import com.wyldsoft.notes.refreshingScreen.interfaces.IViewportTransformer
import com.wyldsoft.notes.settings.PaperSize
import com.wyldsoft.notes.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Main viewport transformer that coordinates all viewport operations.
 * Uses composition to delegate to specialized managers for different concerns.
 */
class ViewportTransformer(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    val viewWidth: Int,
    val viewHeight: Int,
    val settingsRepository: SettingsRepository
) : IViewportTransformer {

    // Component managers
    private val coordinateTransformer = CoordinateTransformer(viewWidth, viewHeight)
    private val paginationManager = PaginationManager(context)
    private val scrollManager = ScrollManager(
        context, coordinateTransformer, paginationManager, 
        coroutineScope, viewWidth, viewHeight
    )
    private val zoomManager = ZoomManager(
        coordinateTransformer, scrollManager, coroutineScope, viewWidth
    )

    // Public state access
    val scrollY: Float get() = coordinateTransformer.scrollY
    val scrollX: Float get() = coordinateTransformer.scrollX
    val zoomScale: Float get() = zoomManager.zoomScale
    val documentHeight: Int get() = scrollManager.documentHeight
    val isZoomIndicatorVisible: Boolean get() = zoomManager.isZoomIndicatorVisible
    val isScrollIndicatorVisible: Boolean get() = scrollManager.isScrollIndicatorVisible
    val isAtTopBoundary: Boolean get() = scrollManager.isAtTopBoundary

    // Viewport change notifications
    val viewportChanged = MutableSharedFlow<Unit>()

    /**
     * Gets the pagination manager instance
     */
    fun getPaginationManager(): PaginationManager = paginationManager

    /**
     * Gets the context used to create this transformer
     */
    fun getContext(): Context = context

    /**
     * Scrolls the viewport by specified amounts
     */
    override fun scroll(deltaX: Float, deltaY: Float) {
        if (scrollManager.scroll(deltaX, deltaY)) {
            notifyViewportChanged()
        }
    }

    /**
     * Zooms the viewport by specified factor at point
     */
    override fun zoom(scaleFactor: Float, focusX: Float, focusY: Float) {
        zoomManager.zoom(scaleFactor, focusX, focusY)
        notifyViewportChanged()
    }

    /**
     * Converts page coordinates to view coordinates
     */
    override fun pageToViewCoordinates(pageX: Float, pageY: Float): Pair<Float, Float> {
        return coordinateTransformer.pageToViewCoordinates(pageX, pageY)
    }

    /**
     * Converts view coordinates to page coordinates
     */
    override fun viewToPageCoordinates(viewX: Float, viewY: Float): Pair<Float, Float> {
        return coordinateTransformer.viewToPageCoordinates(viewX, viewY)
    }

    /**
     * Checks if a rectangle is visible in the current viewport
     */
    override fun isRectVisible(rect: RectF): Boolean {
        val viewport = coordinateTransformer.getCurrentViewportInPageCoordinates()
        return RectF.intersects(viewport, rect)
    }

    /**
     * Updates the document height
     */
    override fun updateDocumentHeight(height: Int) {
        scrollManager.updateDocumentHeight(height)
    }

    /**
     * Scrolls to a specific y-position in the document
     */
    fun scrollToPosition(yPosition: Float) {
        scrollManager.scrollToPosition(yPosition)
        notifyViewportChanged()
    }

    /**
     * Resets zoom to 100%
     */
    fun resetZoom() {
        zoomManager.resetZoom()
        notifyViewportChanged()
    }

    /**
     * Gets the current zoom scale as a percentage string
     */
    fun getZoomPercentage(): String {
        return zoomManager.getZoomPercentage()
    }

    /**
     * Updates the paper size
     */
    fun updatePaperSizeState(paperSize: PaperSize) {
        paginationManager.updatePaperSize(paperSize)
        notifyViewportChanged()
    }

    /**
     * Returns the current viewport rect in page coordinates
     */
    fun getCurrentViewportInPageCoordinates(): RectF {
        return coordinateTransformer.getCurrentViewportInPageCoordinates()
    }

    /**
     * Updates the pagination manager's state
     */
    fun updatePaginationState(enabled: Boolean) {
        paginationManager.isPaginationEnabled = enabled

        if (enabled) {
            scrollManager.updateDocumentHeight(
                paginationManager.getExclusionZoneBottomY(0).toInt()
            )
        }

        notifyViewportChanged()
    }

    /**
     * Sends viewport change notification
     */
    private fun notifyViewportChanged() {
        coroutineScope.launch {
            viewportChanged.emit(Unit)
            // Maintain compatibility with existing refresh system
            com.wyldsoft.notes.strokeManagement.DrawingManager.refreshUi.emit(Unit)
        }
    }
}