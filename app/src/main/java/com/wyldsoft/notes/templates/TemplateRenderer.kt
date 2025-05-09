// Update app/src/main/java/com/wyldsoft/notes/templates/TemplateRenderer.kt
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
import com.wyldsoft.notes.utils.convertDpToPixel

/**
 * Renders background templates like grid and ruled lines
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
     */
    fun renderTemplate(
        canvas: Canvas,
        templateType: TemplateType,
        paperSize: PaperSize,
        viewportTop: Float,
        viewportHeight: Float,
        viewportWidth: Float,
        paginationManager: PaginationManager?
    ) {
        when (templateType) {
            TemplateType.BLANK -> {
                // Do nothing for blank template
                return
            }
            TemplateType.GRID -> {
                if (paginationManager != null && paginationManager.isPaginationEnabled) {
                    renderGridTemplateWithPagination(canvas, paginationManager, viewportTop, viewportHeight, viewportWidth)
                } else {
                    renderGridTemplate(canvas, paperSize, viewportTop, viewportHeight, viewportWidth)
                }
            }
            TemplateType.RULED -> {
                if (paginationManager != null && paginationManager.isPaginationEnabled) {
                    renderRuledTemplateWithPagination(canvas, paginationManager, viewportTop, viewportHeight, viewportWidth)
                } else {
                    renderRuledTemplate(canvas, paperSize, viewportTop, viewportHeight, viewportWidth)
                }
            }
        }
    }

    /**
     * Renders a grid template
     */
    private fun renderGridTemplate(
        canvas: Canvas,
        paperSize: PaperSize,
        viewportTop: Float,
        viewportHeight: Float,
        viewportWidth: Float
    ) {
        val gridSpacing = convertDpToPixel(gridSpacingDp, context)

        // Calculate the grid boundaries
        val startY = Math.floor((viewportTop / gridSpacing).toDouble()) * gridSpacing
        val endY = viewportTop + viewportHeight + gridSpacing

        // Draw horizontal lines
        var y = startY.toFloat()
        while (y < endY) {
            val screenY = y - viewportTop
            canvas.drawLine(0f, screenY, viewportWidth, screenY, gridPaint)
            y += gridSpacing
        }

        // Draw vertical lines
        var x = 0f
        while (x < viewportWidth) {
            canvas.drawLine(x, 0f, x, viewportHeight, gridPaint)
            x += gridSpacing
        }
    }

    /**
     * Renders grid template with pagination support
     */
    private fun renderGridTemplateWithPagination(
        canvas: Canvas,
        paginationManager: PaginationManager,
        viewportTop: Float,
        viewportHeight: Float,
        viewportWidth: Float
    ) {
        val gridSpacing = convertDpToPixel(gridSpacingDp, context)

        // Calculate visible page range
        val firstVisiblePageIndex = paginationManager.getPageIndexForY(viewportTop)
        val lastVisiblePageIndex = paginationManager.getPageIndexForY(viewportTop + viewportHeight)

        // For each visible page, render the grid
        for (pageIndex in firstVisiblePageIndex..lastVisiblePageIndex) {
            val pageTop = paginationManager.getPageTopY(pageIndex)
            val pageBottom = paginationManager.getPageBottomY(pageIndex)

            // Transform to view coordinates
            val viewPageTop = pageTop - viewportTop
            val viewPageBottom = pageBottom - viewportTop

            // Only proceed if the page is visible
            if (viewPageBottom < 0 || viewPageTop > viewportHeight) continue

            // Calculate visible portion of the page
            val visibleTop = Math.max(0f, viewPageTop)
            val visibleBottom = Math.min(viewportHeight, viewPageBottom)
            val pageRect = RectF(0f, visibleTop, viewportWidth, visibleBottom)

            // Save canvas state and clip to page boundaries
            canvas.save()
            canvas.clipRect(pageRect)

            // Calculate grid start position relative to page top
            val pageGridStart = Math.ceil((pageTop / gridSpacing).toDouble()) * gridSpacing
            var y = pageGridStart

            // Draw horizontal grid lines for this page
            while (y <= pageBottom) {
                val screenY = y - viewportTop
                if (screenY >= visibleTop && screenY <= visibleBottom) {
                    canvas.drawLine(0f, screenY.toFloat(), viewportWidth, screenY.toFloat(), gridPaint)
                }
                y += gridSpacing
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
     * Renders ruled lines template that resembles a college notebook
     */
    private fun renderRuledTemplate(
        canvas: Canvas,
        paperSize: PaperSize,
        viewportTop: Float,
        viewportHeight: Float,
        viewportWidth: Float
    ) {
        val lineSpacing = convertDpToPixel(ruledLineSpacingDp, context)
        val leftMargin = convertDpToPixel(leftMarginDp, context)
        val headerHeight = convertDpToPixel(headerHeightDp, context)

        // Calculate the line boundaries
        val startY = Math.floor((viewportTop / lineSpacing).toDouble()) * lineSpacing
        val endY = viewportTop + viewportHeight + lineSpacing

        // Draw the vertical margin line
        canvas.drawLine(
            leftMargin,
            0f,
            leftMargin,
            viewportHeight,
            marginPaint
        )

        // Draw horizontal header line if it's in view
        val headerLine = headerHeight
        val screenHeaderY = headerLine - viewportTop

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
            if (y < headerHeight) {
                y += lineSpacing
                continue
            }

            val screenY = y - viewportTop
            canvas.drawLine(0f, screenY, viewportWidth, screenY, ruledPaint)
            y += lineSpacing
        }
    }

    /**
     * Renders ruled lines template with pagination support
     */
    private fun renderRuledTemplateWithPagination(
        canvas: Canvas,
        paginationManager: PaginationManager,
        viewportTop: Float,
        viewportHeight: Float,
        viewportWidth: Float
    ) {
        val lineSpacing = convertDpToPixel(ruledLineSpacingDp, context)
        println("template: lineSpacing $lineSpacing")
        val leftMargin = convertDpToPixel(leftMarginDp, context)
        val headerHeight = convertDpToPixel(headerHeightDp, context)

        // Calculate visible page range
        val firstVisiblePageIndex = paginationManager.getPageIndexForY(viewportTop)
        val lastVisiblePageIndex = paginationManager.getPageIndexForY(viewportTop + viewportHeight)

        // For each visible page, render the ruled lines
        for (pageIndex in firstVisiblePageIndex..lastVisiblePageIndex) {
            val pageTop = paginationManager.getPageTopY(pageIndex)
            val pageBottom = paginationManager.getPageBottomY(pageIndex)

            // Transform to view coordinates
            val viewPageTop = pageTop - viewportTop
            val viewPageBottom = pageBottom - viewportTop

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
            val pageHeaderY = pageTop + headerHeight
            val screenHeaderY = pageHeaderY - viewportTop

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
            val pageLineStart = pageTop + headerHeight + lineSpacing
            var y = pageLineStart

            // Draw horizontal ruled lines for this page
            while (y <= pageBottom) {
                val screenY = y - viewportTop
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