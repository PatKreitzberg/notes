package com.wyldsoft.notes.pagination

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.transform.ViewportTransformer
import com.wyldsoft.notes.utils.convertDpToPixel
import com.wyldsoft.notes.settings.SettingsRepository
import com.wyldsoft.notes.templates.TemplateRenderer


/**
 * Renders pagination visual elements like page numbers and exclusion zones
 */
class PageRenderer(
    private val viewportTransformer: ViewportTransformer,
    private val settingsRepository: SettingsRepository,
    private val templateRenderer: TemplateRenderer
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
        // Get current viewport
        val viewportTop = viewportTransformer.scrollY
        val viewportHeight = canvas.height.toFloat()
        val viewportWidth = canvas.width.toFloat()

        // Render the selected template
        val settings = settingsRepository.getSettings()
        templateRenderer.renderTemplate(
            canvas,
            settings.template,
            settings.paperSize,
            viewportTop,
            viewportHeight,
            viewportWidth,
            if (paginationManager.isPaginationEnabled) paginationManager else null,
            viewportTransformer  // Pass the viewportTransformer to handle zoom
        )

        if (!paginationManager.isPaginationEnabled) return

        // Calculate visible page range
        val firstVisiblePageIndex = paginationManager.getPageIndexForY(viewportTop)
        val lastVisiblePageIndex = paginationManager.getPageIndexForY(viewportTop + viewportHeight)

        // Draw exclusion zones
        renderExclusionZones(canvas, firstVisiblePageIndex, lastVisiblePageIndex)

        // Draw page numbers - adjusted for zoom scale
        renderPageNumbers(canvas, firstVisiblePageIndex, lastVisiblePageIndex, viewportTop, viewportWidth)
    }

    /**
     * Renders exclusion zones between pages
     */
    private fun renderExclusionZones(
        canvas: Canvas,
        firstVisiblePageIndex: Int,
        lastVisiblePageIndex: Int
    ) {
        // Apply zoom scale to the paint for consistent rendering
        val zoomScale = viewportTransformer.zoomScale

        for (pageIndex in firstVisiblePageIndex..lastVisiblePageIndex) {
            // Skip the first page's top exclusion zone (doesn't exist)
            if (pageIndex > 0) {
                val zoneTop = paginationManager.getExclusionZoneTopY(pageIndex - 1)
                val zoneBottom = paginationManager.getExclusionZoneBottomY(pageIndex - 1)

                // Transform to view coordinates with zoom support
                val (_, viewZoneTop) = viewportTransformer.pageToViewCoordinates(0f, zoneTop)
                val (_, viewZoneBottom) = viewportTransformer.pageToViewCoordinates(0f, zoneBottom)

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

            // Transform to view coordinates with zoom support
            val (_, viewZoneTop) = viewportTransformer.pageToViewCoordinates(0f, zoneTop)
            val (_, viewZoneBottom) = viewportTransformer.pageToViewCoordinates(0f, zoneBottom)

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

        // Adjust text size for zoom
        val zoomScale = viewportTransformer.zoomScale
        pageNumberPaint.textSize = 40f * zoomScale

        for (pageIndex in firstVisiblePageIndex..lastVisiblePageIndex) {
            val pageNumber = paginationManager.getPageNumber(pageIndex)
            val pageTop = paginationManager.getPageTopY(pageIndex)

            // Transform to view coordinates with zoom support
            val (_, viewPageTop) = viewportTransformer.pageToViewCoordinates(0f, pageTop)

            // Calculate position (top right of page)
            val x = viewportRight - padding * zoomScale
            val y = viewPageTop + padding * zoomScale + pageNumberPaint.textSize

            // Only draw if the top of the page is visible
            if (viewPageTop >= 0 && viewPageTop <= canvas.height) {
                canvas.drawText("Page $pageNumber", x, y, pageNumberPaint)
            }
        }
    }
}