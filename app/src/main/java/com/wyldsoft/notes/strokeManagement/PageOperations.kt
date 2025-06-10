package com.wyldsoft.notes.strokeManagement

import com.wyldsoft.notes.pagination.PaginationManager
import com.wyldsoft.notes.views.PageView

/**
 * Handles page insertion and manipulation operations.
 * Manages stroke repositioning when pages are inserted.
 */
class PageOperations(
    private val page: PageView,
    private val historyOperations: HistoryOperations
) {

    /**
     * Inserts a new page at the specified position
     */
    fun insertPage(pageNumber: Int): Boolean {
        try {
            val paginationManager = page.viewportTransformer.getPaginationManager()

            // Validate page number
            val totalPages = paginationManager.getTotalPageCount()
            if (pageNumber < 1 || pageNumber > totalPages) {
                return false
            }

            // Calculate insertion parameters
            val insertPosition = paginationManager.getInsertPosition(pageNumber)
            val pageOffset = paginationManager.getPageInsertionOffset()

            // Find and move affected strokes
            val affectedStrokeIds = moveStrokesForPageInsertion(insertPosition, pageOffset)

            // Record in history
            historyOperations.recordPageInsertion(pageNumber, affectedStrokeIds, pageOffset)

            // Update document height
            updateDocumentHeightAfterInsertion(paginationManager)

            return true
        } catch (e: Exception) {
            println("Error inserting page: ${e.message}")
            return false
        }
    }

    /**
     * Moves strokes that need to be repositioned for page insertion
     */
    private fun moveStrokesForPageInsertion(insertPosition: Float, pageOffset: Float): List<String> {
        val affectedStrokeIds = mutableListOf<String>()
        
        // Find strokes that need to be moved
        val strokesToUpdate = page.strokes.filter { stroke ->
            val isBelow = stroke.top >= insertPosition
            if (isBelow) affectedStrokeIds.add(stroke.id)
            isBelow
        }

        // Move affected strokes down
        for (stroke in strokesToUpdate) {
            stroke.top += pageOffset
            stroke.bottom += pageOffset

            // Update stroke points
            for (point in stroke.points) {
                point.y += pageOffset
            }
        }

        // Update strokes in page if any were affected
        if (strokesToUpdate.isNotEmpty()) {
            page.removeStrokes(affectedStrokeIds)
            page.addStrokes(strokesToUpdate)
        }

        return affectedStrokeIds
    }

    /**
     * Updates document height after page insertion
     */
    private fun updateDocumentHeightAfterInsertion(paginationManager: PaginationManager) {
        val newHeight = paginationManager.getPageBottomY(paginationManager.getTotalPageCount() - 1)
        page.height = newHeight.toInt()
        page.viewportTransformer.updateDocumentHeight(page.height)
    }

    /**
     * Calculates the effective page area for a given page number
     */
    fun getPageArea(pageNumber: Int): android.graphics.RectF? {
        val paginationManager = page.viewportTransformer.getPaginationManager()
        
        return try {
            val pageTopY = paginationManager.getPageTopY(pageNumber - 1) // Convert to 0-based
            val pageBottomY = paginationManager.getPageBottomY(pageNumber - 1)
            
            android.graphics.RectF(
                0f,
                pageTopY,
                page.viewportTransformer.getContext().resources.displayMetrics.widthPixels.toFloat(),
                pageBottomY
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gets the page number for a given Y coordinate
     */
    fun getPageNumberForY(y: Float): Int {
        val paginationManager = page.viewportTransformer.getPaginationManager()
        return paginationManager.getPageIndexForY(y) + 1 // Convert to 1-based
    }

    /**
     * Counts strokes on a specific page
     */
    fun getStrokeCountOnPage(pageNumber: Int): Int {
        val pageArea = getPageArea(pageNumber) ?: return 0
        
        return page.strokes.count { stroke ->
            // Check if stroke's center point is within the page area
            val centerY = (stroke.top + stroke.bottom) / 2
            centerY >= pageArea.top && centerY <= pageArea.bottom
        }
    }

    /**
     * Gets all strokes on a specific page
     */
    fun getStrokesOnPage(pageNumber: Int): List<com.wyldsoft.notes.utils.Stroke> {
        val pageArea = getPageArea(pageNumber) ?: return emptyList()
        
        return page.strokes.filter { stroke ->
            // Check if stroke intersects with the page area
            val strokeRect = android.graphics.RectF(stroke.left, stroke.top, stroke.right, stroke.bottom)
            android.graphics.RectF.intersects(strokeRect, pageArea)
        }
    }
}