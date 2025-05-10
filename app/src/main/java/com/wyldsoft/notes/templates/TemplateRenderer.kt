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
 * Handles proper scaling with zoom
 */
class TemplateRenderer(private val context: Context) {
    // Paint for grid lines
    private val gridPaint = Paint().apply {
        color = Color.argb(40, 0, 0, 0) // Light gray with 40% opacity
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
     * Now handles zoom properly through viewportTransformer
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

        // Apply zoom transformation if provided
        val zoomScale = viewportTransformer?.zoomScale ?: 1.0f

        when (templateType) {
            TemplateType.BLANK -> {
                // Do nothing for blank template
                canvas.restore()
                return
            }
            TemplateType.GRID -> {
                if (paginationManager != null && paginationManager.isPaginationEnabled) {
                    renderGridTemplateWithPagination(canvas, paginationManager, viewportTop, viewportHeight, viewportWidth, zoomScale, viewportTransformer)
                } else {
                    renderGridTemplate(canvas, paperSize, viewportTop, viewportHeight, viewportWidth, zoomScale, viewportTransformer)
                }
            }
            TemplateType.RULED -> {
                if (paginationManager != null && paginationManager.isPaginationEnabled) {
                    renderRuledTemplateWithPagination(canvas, paginationManager, viewportTop, viewportHeight, viewportWidth, zoomScale, viewportTransformer)
                } else {
                    renderRuledTemplate(canvas, paperSize, viewportTop, viewportHeight, viewportWidth, zoomScale, viewportTransformer)
                }
            }
        }

        // Restore the canvas state
        canvas.restore()
    }

    /**
     * Renders a grid template with proper zoom handling
     */
    private fun renderGridTemplate(
        canvas: Canvas,
        paperSize: PaperSize,
        viewportTop: Float,
        viewportHeight: Float,
        viewportWidth: Float,
        zoomScale: Float,
        viewportTransformer: ViewportTransformer?
    ) {
        // Adjust grid spacing based on zoom scale
        val gridSpacing = convertDpToPixel(gridSpacingDp, context) * zoomScale

        // Calculate the grid boundaries in page coordinates
        val startY = Math.floor((viewportTop / (gridSpacing / zoomScale)).toDouble()) * (gridSpacing / zoomScale)
        val endY = viewportTop + viewportHeight / zoomScale + gridSpacing / zoomScale

        // Draw horizontal lines
        var y = startY.toFloat()
        while (y < endY) {
            // Transform y from page to view coordinates
            val screenY = if (viewportTransformer != null) {
                val (_, viewY) = viewportTransformer.pageToViewCoordinates(0f, y)
                viewY
            } else {
                y - viewportTop
            }

            // Adjust stroke width for zoom
            gridPaint.strokeWidth = 1f * zoomScale

            canvas.drawLine(0f, screenY, viewportWidth, screenY, gridPaint)
            y += gridSpacing / zoomScale
        }

        // Draw vertical lines
        val gridSpacingX = gridSpacing
        var x = 0f
        while (x < viewportWidth) {
            canvas.drawLine(x, 0f, x, viewportHeight, gridPaint)
            x += gridSpacingX
        }
    }

    /**
     * Renders grid template with pagination support and proper zoom handling
     */
    private fun renderGridTemplateWithPagination(
        canvas: Canvas,
        paginationManager: PaginationManager,
        viewportTop: Float,
        viewportHeight: Float,
        viewportWidth: Float,
        zoomScale: Float,
        viewportTransformer: ViewportTransformer?
    ) {
        val gridSpacing = convertDpToPixel(gridSpacingDp, context) * zoomScale
        gridPaint.strokeWidth = 1f * zoomScale

        // Calculate visible page range (in page coordinates)
        val firstVisiblePageIndex = paginationManager.getPageIndexForY(viewportTop)
        val lastVisiblePageIndex = paginationManager.getPageIndexForY(viewportTop + viewportHeight / zoomScale)

        // For each visible page, render the grid
        for (pageIndex in firstVisiblePageIndex..lastVisiblePageIndex) {
            val pageTop = paginationManager.getPageTopY(pageIndex)
            val pageBottom = paginationManager.getPageBottomY(pageIndex)

            // Transform to view coordinates with zoom support
            val viewPageTop = if (viewportTransformer != null) {
                val (_, viewY) = viewportTransformer.pageToViewCoordinates(0f, pageTop)
                viewY
            } else {
                pageTop - viewportTop
            }

            val viewPageBottom = if (viewportTransformer != null) {
                val (_, viewY) = viewportTransformer.pageToViewCoordinates(0f, pageBottom)
                viewY
            } else {
                pageBottom - viewportTop
            }

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
            val pageGridStart = Math.ceil((pageTop / (gridSpacing / zoomScale)).toDouble()) * (gridSpacing / zoomScale)
            var y = pageGridStart

            // Draw horizontal grid lines for this page
            while (y <= pageBottom) {
                // Transform to view coordinates with zoom support
                val screenY = if (viewportTransformer != null) {
                    val (_, viewY) = viewportTransformer.pageToViewCoordinates(0f, y.toFloat())
                    viewY
                } else {
                    y - viewportTop
                }

                if (screenY >= visibleTop && screenY <= visibleBottom) {
                    canvas.drawLine(0f, screenY, viewportWidth, screenY, gridPaint)
                }
                y += gridSpacing / zoomScale
            }

            // Draw vertical grid lines 
            var x = 0f
            while (x < viewportWidth) {
                canvas.drawLine(x, visibleTop, x, visibleBottom, gridPaint)
                x += gridSpacing
            }

            // Restore canvas state
            canvas.restore()
        }
    }

    /**
     * Renders ruled lines template with proper zoom handling
     */
    private fun renderRuledTemplate(
        canvas: Canvas,
        paperSize: PaperSize,
        viewportTop: Float,
        viewportHeight: Float,
        viewportWidth: Float,
        zoomScale: Float,
        viewportTransformer: ViewportTransformer?
    ) {
        val lineSpacing = convertDpToPixel(ruledLineSpacingDp, context) * zoomScale
        val leftMargin = convertDpToPixel(leftMarginDp, context) * zoomScale
        val headerHeight = convertDpToPixel(headerHeightDp, context) * zoomScale

        // Adjust paint stroke widths for zoom
        ruledPaint.strokeWidth = 2f * zoomScale
        marginPaint.strokeWidth = 2f * zoomScale
        headerPaint.strokeWidth = 2f * zoomScale

        // Calculate the line boundaries in page coordinates
        val startY = Math.floor((viewportTop / (lineSpacing / zoomScale)).toDouble()) * (lineSpacing / zoomScale)
        val endY = viewportTop + viewportHeight / zoomScale + lineSpacing / zoomScale

        // Draw the vertical margin line
        canvas.drawLine(
            leftMargin,
            0f,
            leftMargin,
            viewportHeight,
            marginPaint
        )

        // Draw horizontal header line if it's in view
        val headerLine = headerHeight / zoomScale

        // Transform to view coordinates with zoom
        val screenHeaderY = if (viewportTransformer != null) {
            val (_, viewY) = viewportTransformer.pageToViewCoordinates(0f, headerLine)
            viewY
        } else {
            headerLine - viewportTop
        }

        if (screenHeaderY >= 0 && screenHeaderY <= viewportHeight) {
            canvas.drawLine(
                0f,
                screenHeaderY,
                viewportWidth,
                screenHeaderY,
                headerPaint
            )
        }

        // Draw horizontal ruled lines
        var y = startY.toFloat()
        while (y < endY) {
            // Skip lines that would be in the header area
            if (y < headerLine) {
                y += lineSpacing / zoomScale
                continue
            }

            // Transform to view coordinates with zoom
            val screenY = if (viewportTransformer != null) {
                val (_, viewY) = viewportTransformer.pageToViewCoordinates(0f, y)
                viewY
            } else {
                y - viewportTop
            }

            canvas.drawLine(0f, screenY, viewportWidth, screenY, ruledPaint)
            y += lineSpacing / zoomScale
        }
    }

    /**
     * Renders ruled lines template with pagination support and proper zoom handling
     */
    private fun renderRuledTemplateWithPagination(
        canvas: Canvas,
        paginationManager: PaginationManager,
        viewportTop: Float,
        viewportHeight: Float,
        viewportWidth: Float,
        zoomScale: Float,
        viewportTransformer: ViewportTransformer?
    ) {
        val lineSpacing = convertDpToPixel(ruledLineSpacingDp, context) * zoomScale
        val leftMargin = convertDpToPixel(leftMarginDp, context) * zoomScale
        val headerHeight = convertDpToPixel(headerHeightDp, context) * zoomScale

        // Adjust paint stroke widths for zoom
        ruledPaint.strokeWidth = 2f * zoomScale
        marginPaint.strokeWidth = 2f * zoomScale
        headerPaint.strokeWidth = 2f * zoomScale

        // Calculate visible page range (in page coordinates)
        val firstVisiblePageIndex = paginationManager.getPageIndexForY(viewportTop)
        val lastVisiblePageIndex = paginationManager.getPageIndexForY(viewportTop + viewportHeight / zoomScale)

        // For each visible page, render the ruled lines
        for (pageIndex in firstVisiblePageIndex..lastVisiblePageIndex) {
            val pageTop = paginationManager.getPageTopY(pageIndex)
            val pageBottom = paginationManager.getPageBottomY(pageIndex)

            // Transform to view coordinates with zoom support
            val viewPageTop = if (viewportTransformer != null) {
                val (_, viewY) = viewportTransformer.pageToViewCoordinates(0f, pageTop)
                viewY
            } else {
                pageTop - viewportTop
            }

            val viewPageBottom = if (viewportTransformer != null) {
                val (_, viewY) = viewportTransformer.pageToViewCoordinates(0f, pageBottom)
                viewY
            } else {
                pageBottom - viewportTop
            }

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
            canvas.drawLine(
                leftMargin,
                visibleTop,
                leftMargin,
                visibleBottom,
                marginPaint
            )

            // Calculate header position within this page
            val pageHeaderY = pageTop + headerHeight / zoomScale

            // Transform to view coordinates with zoom support
            val screenHeaderY = if (viewportTransformer != null) {
                val (_, viewY) = viewportTransformer.pageToViewCoordinates(0f, pageHeaderY)
                viewY
            } else {
                pageHeaderY - viewportTop
            }

            // Draw horizontal header line if it's in view
            if (screenHeaderY >= visibleTop && screenHeaderY <= visibleBottom) {
                canvas.drawLine(
                    0f,
                    screenHeaderY,
                    viewportWidth,
                    screenHeaderY,
                    headerPaint
                )
            }

            // Calculate ruled lines start position for this page
            val pageLineStart = pageTop + headerHeight / zoomScale + lineSpacing / zoomScale
            var y = pageLineStart

            // Draw horizontal ruled lines for this page
            while (y <= pageBottom) {
                // Transform to view coordinates with zoom support
                val screenY = if (viewportTransformer != null) {
                    val (_, viewY) = viewportTransformer.pageToViewCoordinates(0f, y)
                    viewY
                } else {
                    y - viewportTop
                }

                if (screenY >= visibleTop && screenY <= visibleBottom) {
                    canvas.drawLine(0f, screenY, viewportWidth, screenY, ruledPaint)
                }
                y += lineSpacing / zoomScale
            }

            // Restore canvas state
            canvas.restore()
        }
    }
}