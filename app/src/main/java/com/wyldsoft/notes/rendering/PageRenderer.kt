package com.wyldsoft.notes.rendering

import android.graphics.Canvas
import com.wyldsoft.notes.rendering.interfaces.IPageRenderer
import com.wyldsoft.notes.settings.SettingsRepository
import com.wyldsoft.notes.templates.TemplateRenderer
import com.wyldsoft.notes.refreshingScreen.ViewportTransformer

/**
 * Handles rendering of pagination visual elements.
 * Renders page numbers, exclusion zones, and page boundaries.
 */
class PageRenderer(
    private val viewportTransformer: ViewportTransformer,
    private val settingsRepository: SettingsRepository,
    private val templateRenderer: TemplateRenderer
) : IPageRenderer {

    /**
     * Renders pagination visual elements like page numbers and exclusion zones
     */
    override fun renderPaginationElements(canvas: Canvas) {
        val paginationManager = viewportTransformer.getPaginationManager()
        
        // Get template settings from settings repository
        val currentSettings = settingsRepository.getSettings()
        val templateType = currentSettings.template
        val paperSize = currentSettings.paperSize
        
        // Get viewport information
        val viewportTop = viewportTransformer.scrollY
        val viewportHeight = canvas.height.toFloat()
        val viewportWidth = canvas.width.toFloat()
        val viewportBottom = viewportTop + viewportHeight
        
        if (!paginationManager.isPaginationEnabled) {
            // Still render template even if pagination is disabled
            templateRenderer.renderTemplate(
                canvas = canvas,
                templateType = templateType,
                paperSize = paperSize,
                viewportTop = viewportTop,
                viewportHeight = viewportHeight,
                viewportWidth = viewportWidth,
                paginationManager = null,
                viewportTransformer = viewportTransformer
            )
            return
        }

        // Render template background first
        templateRenderer.renderTemplate(
            canvas = canvas,
            templateType = templateType,
            paperSize = paperSize,
            viewportTop = viewportTop,
            viewportHeight = viewportHeight,
            viewportWidth = viewportWidth,
            paginationManager = paginationManager,
            viewportTransformer = viewportTransformer
        )

        // Calculate which pages are visible
        val startPageIndex = paginationManager.getPageIndexForY(viewportTop)
        val endPageIndex = paginationManager.getPageIndexForY(viewportBottom)

        // Render page boundaries and numbers for visible pages
        for (pageIndex in startPageIndex..endPageIndex) {
            renderPageBoundary(canvas, pageIndex)
            renderPageNumber(canvas, pageIndex)
        }

        // Render exclusion zones
        renderExclusionZones(canvas)
    }

    /**
     * Renders the boundary line for a specific page
     */
    private fun renderPageBoundary(canvas: Canvas, pageIndex: Int) {
        val paginationManager = viewportTransformer.getPaginationManager()
        val pageTopY = paginationManager.getPageTopY(pageIndex)
        val pageBottomY = paginationManager.getPageBottomY(pageIndex)
        
        // Transform to view coordinates
        val (_, viewTopY) = viewportTransformer.pageToViewCoordinates(0f, pageTopY)
        val (_, viewBottomY) = viewportTransformer.pageToViewCoordinates(0f, pageBottomY)
        
        // Only draw if visible
        if (viewBottomY >= 0 && viewTopY <= canvas.height) {
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.LTGRAY
                strokeWidth = 2f
                style = android.graphics.Paint.Style.STROKE
            }
            
            // Draw bottom boundary
            canvas.drawLine(0f, viewBottomY, canvas.width.toFloat(), viewBottomY, paint)
        }
    }

    /**
     * Renders page number for a specific page
     */
    private fun renderPageNumber(canvas: Canvas, pageIndex: Int) {
        val paginationManager = viewportTransformer.getPaginationManager()
        val pageBottomY = paginationManager.getPageBottomY(pageIndex)
        
        // Transform to view coordinates
        val (_, viewBottomY) = viewportTransformer.pageToViewCoordinates(0f, pageBottomY)
        
        // Only draw if visible
        if (viewBottomY >= 0 && viewBottomY <= canvas.height) {
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.GRAY
                textSize = 24f
                isAntiAlias = true
            }
            
            val pageNumber = (pageIndex + 1).toString()
            val textBounds = android.graphics.Rect()
            paint.getTextBounds(pageNumber, 0, pageNumber.length, textBounds)
            
            val x = canvas.width - textBounds.width() - 20f
            val y = viewBottomY - 10f
            
            canvas.drawText(pageNumber, x, y, paint)
        }
    }

    /**
     * Renders exclusion zones where drawing is not allowed
     */
    private fun renderExclusionZones(canvas: Canvas) {
        val paginationManager = viewportTransformer.getPaginationManager()
        val viewportTop = viewportTransformer.scrollY
        val viewportHeight = canvas.height.toFloat()
        
        val exclusionZones = paginationManager.getExclusionZonesInViewport(viewportTop, viewportHeight)
        
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(50, 255, 0, 0)
            style = android.graphics.Paint.Style.FILL
        }
        
        for (zone in exclusionZones) {
            val (_, viewTop) = viewportTransformer.pageToViewCoordinates(0f, zone.top.toFloat())
            val (_, viewBottom) = viewportTransformer.pageToViewCoordinates(0f, zone.bottom.toFloat())
            
            canvas.drawRect(
                0f,
                viewTop.coerceAtLeast(0f),
                canvas.width.toFloat(),
                viewBottom.coerceAtMost(canvas.height.toFloat()),
                paint
            )
        }
    }
}