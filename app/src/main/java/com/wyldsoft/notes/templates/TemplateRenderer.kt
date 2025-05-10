// app/src/main/java/com/wyldsoft/notes/templates/TemplateRenderer.kt
package com.wyldsoft.notes.templates

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.pagination.PaginationManager
import com.wyldsoft.notes.settings.PaperSize
import com.wyldsoft.notes.settings.TemplateType
import com.wyldsoft.notes.transform.ViewportTransformer
import com.wyldsoft.notes.utils.convertDpToPixel

/**
 * Renders background templates like grid and ruled lines
 * Uses ViewportTransformer for consistent coordinate transforms
 */
class TemplateRenderer(private val context: Context) {
    // Paint for grid lines
    private val gridPaint = Paint().apply {
        color = Color.argb(100, 0, 0, 200) // Light gray with 40% opacity
        strokeWidth = 1f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    // Paint for ruled lines
    private val ruledPaint = Paint().apply {
        color = Color.argb(100, 0, 0, 200) // Light blue with 50% opacity
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    // Paint for margin line
    private val marginPaint = Paint().apply {
        color = Color.argb(100, 255, 0, 0) // Light red with 70% opacity
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    // Paint for header line
    private val headerPaint = Paint().apply {
        color = Color.argb(100, 0, 0, 0) // Black with 60% opacity
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    // Grid spacing in dp
    private val gridSpacingDp = 20.dp

    // Ruled line spacing in dp
    private val ruledLineSpacingDp = 30.dp

    // Left margin position in dp
    private val leftMarginDp = 80.dp

    // Header height in dp
    private val headerHeightDp = 60.dp

    /**
     * Renders the selected template on the canvas
     * Using ViewportTransformer for consistent coordinate transformations
     */
    fun renderTemplate(
        canvas: Canvas,
        templateType: TemplateType,
        paperSize: PaperSize,
        viewportTop: Float,
        viewportHeight: Float,
        viewportWidth: Float,
        paginationManager: PaginationManager?,
        viewportTransformer: ViewportTransformer? = null
    ) {
        // Save the canvas state to restore after drawing
        canvas.save()

        when (templateType) {
            TemplateType.BLANK -> {
                // Do nothing for blank template
                canvas.restore()
                return
            }
            TemplateType.GRID -> {
                if (paginationManager != null && paginationManager.isPaginationEnabled) {
                    renderGridTemplateWithPagination(canvas, paginationManager, viewportTop, viewportHeight, viewportWidth, viewportTransformer)
                } else {
                    renderGridTemplate(canvas, paperSize, viewportTop, viewportHeight, viewportWidth, viewportTransformer)
                }
            }
            TemplateType.RULED -> {
                if (paginationManager != null && paginationManager.isPaginationEnabled) {
                    renderRuledTemplateWithPagination(canvas, paginationManager, viewportTop, viewportHeight, viewportWidth, viewportTransformer)
                } else {
                    renderRuledTemplate(canvas, paperSize, viewportTop, viewportHeight, viewportWidth, viewportTransformer)
                }
            }
        }

        // Restore the canvas state
        canvas.restore()
    }

    /**
     * Renders a grid template using ViewportTransformer for coordinate transformations
     */
    private fun renderGridTemplate(
        canvas: Canvas,
        paperSize: PaperSize,
        viewportTop: Float,
        viewportHeight: Float,
        viewportWidth: Float,
        viewportTransformer: ViewportTransformer?
    ) {
        if (viewportTransformer == null) return

        // Grid spacing in page coordinates
        val gridSpacing = convertDpToPixel(gridSpacingDp, context)

        // Adjust stroke width based on zoom
        val zoomScale = viewportTransformer.zoomScale
        gridPaint.strokeWidth = 1f * zoomScale

        // Calculate the grid boundaries in page coordinates
        val viewport = viewportTransformer.getCurrentViewportInPageCoordinates()
        val startY = Math.floor((viewport.top).toDouble() / gridSpacing) * gridSpacing
        val endY = viewport.bottom + gridSpacing

        // Draw horizontal lines
        var y = startY.toFloat()
        while (y < endY) {
            // Transform to view coordinates
            val (_, screenY) = viewportTransformer.pageToViewCoordinates(0f, y)

            // Draw the line in view coordinates
            canvas.drawLine(0f, screenY, viewportWidth, screenY, gridPaint)
            y += gridSpacing
        }

        // Draw vertical lines
        val startX = Math.floor(viewport.left.toDouble() / gridSpacing) * gridSpacing
        val endX = viewport.right + gridSpacing

        var x = startX.toFloat()
        while (x < endX) {
            // Transform to view coordinates
            val (screenX, _) = viewportTransformer.pageToViewCoordinates(x, 0f)

            // Draw the line in view coordinates
            canvas.drawLine(screenX, 0f, screenX, viewportHeight, gridPaint)
            x += gridSpacing
        }
    }

    /**
     * Renders grid template with pagination support using ViewportTransformer
     */
    private fun renderGridTemplateWithPagination(
        canvas: Canvas,
        paginationManager: PaginationManager,
        viewportTop: Float,
        viewportHeight: Float,
        viewportWidth: Float,
        viewportTransformer: ViewportTransformer?
    ) {
        if (viewportTransformer == null) return

        // Grid spacing in page coordinates
        val gridSpacing = convertDpToPixel(gridSpacingDp, context)

        // Adjust stroke width based on zoom
        val zoomScale = viewportTransformer.zoomScale
        gridPaint.strokeWidth = 1f * zoomScale

        // Calculate visible page range (in page coordinates)
        val viewport = viewportTransformer.getCurrentViewportInPageCoordinates()
        val firstVisiblePageIndex = paginationManager.getPageIndexForY(viewport.top)
        val lastVisiblePageIndex = paginationManager.getPageIndexForY(viewport.bottom)

        // For each visible page, render the grid
        for (pageIndex in firstVisiblePageIndex..lastVisiblePageIndex) {
            val pageTop = paginationManager.getPageTopY(pageIndex)
            val pageBottom = paginationManager.getPageBottomY(pageIndex)

            // Transform page boundaries to view coordinates
            val (_, viewPageTop) = viewportTransformer.pageToViewCoordinates(0f, pageTop)
            val (_, viewPageBottom) = viewportTransformer.pageToViewCoordinates(0f, pageBottom)

            // Only proceed if the page is visible
            if (viewPageBottom < 0 || viewPageTop > viewportHeight) continue

            // Calculate visible portion of the page
            val visibleTop = Math.max(0f, viewPageTop)
            val visibleBottom = Math.min(viewportHeight, viewPageBottom)
            val pageRect = RectF(0f, visibleTop, viewportWidth, visibleBottom)

            // Save canvas state and clip to page boundaries
            canvas.save()
            canvas.clipRect(pageRect)

            // Calculate grid start position relative to page top (in page coordinates)
            val gridStart = Math.ceil(pageTop.toDouble() / gridSpacing) * gridSpacing
            var y = gridStart.toFloat()

            // Draw horizontal grid lines for this page
            while (y <= pageBottom) {
                // Transform to view coordinates
                val (_, screenY) = viewportTransformer.pageToViewCoordinates(0f, y)

                if (screenY >= visibleTop && screenY <= visibleBottom) {
                    canvas.drawLine(0f, screenY, viewportWidth, screenY, gridPaint)
                }
                y += gridSpacing
            }

            // Draw vertical grid lines
            val startX = 0f
            val endX = viewportTransformer.getCurrentViewportInPageCoordinates().width()
            var x = startX

            while (x < endX) {
                // Transform to view coordinates
                val (screenX, _) = viewportTransformer.pageToViewCoordinates(x, 0f)

                if (screenX >= 0 && screenX <= viewportWidth) {
                    canvas.drawLine(screenX, visibleTop, screenX, visibleBottom, gridPaint)
                }
                x += gridSpacing
            }

            // Restore canvas state
            canvas.restore()
        }
    }

    /**
     * Renders ruled lines template using ViewportTransformer
     */
    private fun renderRuledTemplate(
        canvas: Canvas,
        paperSize: PaperSize,
        viewportTop: Float,
        viewportHeight: Float,
        viewportWidth: Float,
        viewportTransformer: ViewportTransformer?
    ) {
        if (viewportTransformer == null) return

        // Get dimensions in page coordinates
        val lineSpacing = convertDpToPixel(ruledLineSpacingDp, context)
        val leftMarginInPage = convertDpToPixel(leftMarginDp, context)
        val headerHeightInPage = convertDpToPixel(headerHeightDp, context)

        // Adjust stroke width based on zoom
        val zoomScale = viewportTransformer.zoomScale
        ruledPaint.strokeWidth = 2f * zoomScale
        marginPaint.strokeWidth = 2f * zoomScale
        headerPaint.strokeWidth = 2f * zoomScale

        // Get the current viewport in page coordinates
        val viewport = viewportTransformer.getCurrentViewportInPageCoordinates()

        // Transform the left margin line to view coordinates
        val (leftMarginView, _) = viewportTransformer.pageToViewCoordinates(leftMarginInPage, 0f)

        // Draw the vertical margin line
        canvas.drawLine(
            leftMarginView,
            0f,
            leftMarginView,
            viewportHeight,
            marginPaint
        )

        // Transform the header line to view coordinates
        val (_, headerLineView) = viewportTransformer.pageToViewCoordinates(0f, headerHeightInPage)

        // Draw horizontal header line if it's in view
        if (headerLineView >= 0 && headerLineView <= viewportHeight) {
            canvas.drawLine(
                0f,
                headerLineView,
                viewportWidth,
                headerLineView,
                headerPaint
            )
        }

        // Calculate line boundaries for ruled lines
        val startY = Math.ceil((viewport.top.toDouble() + headerHeightInPage) / lineSpacing) * lineSpacing
        val endY = viewport.bottom + lineSpacing

        // Draw horizontal ruled lines
        var y = startY.toFloat()
        while (y < endY) {
            // Skip lines that would be in the header area
            if (y < headerHeightInPage) {
                y += lineSpacing
                continue
            }

            // Transform to view coordinates
            val (_, screenY) = viewportTransformer.pageToViewCoordinates(0f, y)

            if (screenY >= 0 && screenY <= viewportHeight) {
                canvas.drawLine(0f, screenY, viewportWidth, screenY, ruledPaint)
            }
            y += lineSpacing
        }
    }

    /**
     * Renders ruled lines template with pagination support using ViewportTransformer
     */
    private fun renderRuledTemplateWithPagination(
        canvas: Canvas,
        paginationManager: PaginationManager,
        viewportTop: Float,
        viewportHeight: Float,
        viewportWidth: Float,
        viewportTransformer: ViewportTransformer?
    ) {
        if (viewportTransformer == null) return

        // Get dimensions in page coordinates
        val lineSpacing = convertDpToPixel(ruledLineSpacingDp, context)
        val leftMarginInPage = convertDpToPixel(leftMarginDp, context)
        val headerHeightInPage = convertDpToPixel(headerHeightDp, context)

        // Adjust paint stroke widths for zoom
        val zoomScale = viewportTransformer.zoomScale
        ruledPaint.strokeWidth = 2f * zoomScale
        marginPaint.strokeWidth = 2f * zoomScale
        headerPaint.strokeWidth = 2f * zoomScale

        // Calculate visible page range (in page coordinates)
        val viewport = viewportTransformer.getCurrentViewportInPageCoordinates()
        val firstVisiblePageIndex = paginationManager.getPageIndexForY(viewport.top)
        val lastVisiblePageIndex = paginationManager.getPageIndexForY(viewport.bottom)

        // For each visible page, render the ruled lines
        for (pageIndex in firstVisiblePageIndex..lastVisiblePageIndex) {
            val pageTop = paginationManager.getPageTopY(pageIndex)
            val pageBottom = paginationManager.getPageBottomY(pageIndex)

            // Transform to view coordinates
            val (_, viewPageTop) = viewportTransformer.pageToViewCoordinates(0f, pageTop)
            val (_, viewPageBottom) = viewportTransformer.pageToViewCoordinates(0f, pageBottom)

            // Only proceed if the page is visible
            if (viewPageBottom < 0 || viewPageTop > viewportHeight) continue

            // Calculate visible portion of the page
            val visibleTop = Math.max(0f, viewPageTop)
            val visibleBottom = Math.min(viewportHeight, viewPageBottom)
            val pageRect = RectF(0f, visibleTop, viewportWidth, visibleBottom)

            // Save canvas state and clip to page boundaries
            canvas.save()
            canvas.clipRect(pageRect)

            // Draw the vertical margin line for this page
            val (leftMarginView, _) = viewportTransformer.pageToViewCoordinates(leftMarginInPage, 0f)

            canvas.drawLine(
                leftMarginView,
                visibleTop,
                leftMarginView,
                visibleBottom,
                marginPaint
            )

            // Calculate header position within this page
            val pageHeaderY = pageTop + headerHeightInPage

            // Transform to view coordinates
            val (_, headerLineView) = viewportTransformer.pageToViewCoordinates(0f, pageHeaderY)

            // Draw horizontal header line if it's in view
            if (headerLineView >= visibleTop && headerLineView <= visibleBottom) {
                canvas.drawLine(
                    0f,
                    headerLineView,
                    viewportWidth,
                    headerLineView,
                    headerPaint
                )
            }

            // Calculate ruled lines start position for this page
            val lineStart = Math.ceil((pageTop.toDouble() + headerHeightInPage) / lineSpacing) * lineSpacing
            var y = lineStart.toFloat()

            // Draw horizontal ruled lines for this page
            while (y <= pageBottom) {
                // Transform to view coordinates
                val (_, screenY) = viewportTransformer.pageToViewCoordinates(0f, y)

                if (screenY >= visibleTop && screenY <= visibleBottom) {
                    canvas.drawLine(0f, screenY, viewportWidth, screenY, ruledPaint)
                }
                y += lineSpacing
            }

            // Restore canvas state
            canvas.restore()
        }
    }
}