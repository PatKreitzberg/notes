package com.wyldsoft.notes.pagination

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.transform.ViewportTransformer
import com.wyldsoft.notes.utils.convertDpToPixel

/**
 * Renders pagination visual elements like page numbers and exclusion zones
 */
class PageRenderer(
    private val viewportTransformer: ViewportTransformer
) {
    private val paginationManager = viewportTransformer.getPaginationManager()

    // Page number text paint
    private val pageNumberPaint = Paint().apply {
        color = Color.GRAY
        textSize = 40f
        textAlign = Paint.Align.RIGHT
        isAntiAlias = true
    }

    // Exclusion zone paint
    private val exclusionZonePaint = Paint().apply {
        color = paginationManager.exclusionZoneColor
        style = Paint.Style.FILL
    }

    /**
     * Renders pagination elements on the canvas
     */
    fun renderPaginationElements(canvas: Canvas) {
        if (!paginationManager.isPaginationEnabled) return

        // Get current viewport
        val viewportTop = viewportTransformer.scrollY
        val viewportHeight = canvas.height.toFloat()
        val viewportRight = canvas.width.toFloat()

        // Calculate visible page range
        val firstVisiblePageIndex = paginationManager.getPageIndexForY(viewportTop)
        val lastVisiblePageIndex = paginationManager.getPageIndexForY(viewportTop + viewportHeight)

        // Draw exclusion zones
        renderExclusionZones(canvas, firstVisiblePageIndex, lastVisiblePageIndex)

        // Draw page numbers
        renderPageNumbers(canvas, firstVisiblePageIndex, lastVisiblePageIndex, viewportTop, viewportRight)
    }

    /**
     * Renders exclusion zones between pages
     */
    private fun renderExclusionZones(
        canvas: Canvas,
        firstVisiblePageIndex: Int,
        lastVisiblePageIndex: Int
    ) {
        for (pageIndex in firstVisiblePageIndex..lastVisiblePageIndex) {
            // Skip the first page's top exclusion zone (doesn't exist)
            if (pageIndex > 0) {
                val zoneTop = paginationManager.getExclusionZoneTopY(pageIndex - 1)
                val zoneBottom = paginationManager.getExclusionZoneBottomY(pageIndex - 1)

                // Transform to view coordinates
                val viewZoneTop = zoneTop - viewportTransformer.scrollY
                val viewZoneBottom = zoneBottom - viewportTransformer.scrollY

                // Only draw if visible in viewport
                if (viewZoneBottom >= 0 && viewZoneTop <= canvas.height) {
                    canvas.drawRect(
                        0f,
                        viewZoneTop,
                        canvas.width.toFloat(),
                        viewZoneBottom,
                        exclusionZonePaint
                    )
                }
            }

            // Draw the exclusion zone at the bottom of the current page
            val zoneTop = paginationManager.getExclusionZoneTopY(pageIndex)
            val zoneBottom = paginationManager.getExclusionZoneBottomY(pageIndex)

            // Transform to view coordinates
            val viewZoneTop = zoneTop - viewportTransformer.scrollY
            val viewZoneBottom = zoneBottom - viewportTransformer.scrollY

            // Only draw if visible in viewport
            if (viewZoneBottom >= 0 && viewZoneTop <= canvas.height) {
                canvas.drawRect(
                    0f,
                    viewZoneTop,
                    canvas.width.toFloat(),
                    viewZoneBottom,
                    exclusionZonePaint
                )
            }
        }
    }

    /**
     * Renders page numbers in the top right corner of each page
     */
    private fun renderPageNumbers(
        canvas: Canvas,
        firstVisiblePageIndex: Int,
        lastVisiblePageIndex: Int,
        viewportTop: Float,
        viewportRight: Float
    ) {
        // Padding for page number positioning
        val paddingDp = 20.dp
        val padding = convertDpToPixel(paddingDp, viewportTransformer.getContext())

        for (pageIndex in firstVisiblePageIndex..lastVisiblePageIndex) {
            val pageNumber = paginationManager.getPageNumber(pageIndex)
            val pageTop = paginationManager.getPageTopY(pageIndex)

            // Transform to view coordinates
            val viewPageTop = pageTop - viewportTop

            // Calculate position (top right of page)
            val x = viewportRight - padding
            val y = viewPageTop + padding + pageNumberPaint.textSize

            // Only draw if the top of the page is visible
            if (viewPageTop >= 0 && viewPageTop <= canvas.height) {
                canvas.drawText("Page $pageNumber", x, y, pageNumberPaint)
            }
        }
    }
}