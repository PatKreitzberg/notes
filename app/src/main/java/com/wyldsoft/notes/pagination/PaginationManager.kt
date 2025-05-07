// app/src/main/java/com/wyldsoft/notes/pagination/PaginationManager.kt
package com.wyldsoft.notes.pagination

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.utils.convertDpToPixel
import com.wyldsoft.notes.settings.PaperSize
import com.wyldsoft.notes.SCREEN_WIDTH
import com.wyldsoft.notes.SCREEN_HEIGHT


/**
 * Manages pagination for the notes application
 * Handles page size, boundaries, and exclusion zones
 */
class PaginationManager(private val context: Context) {
    // Pagination state
    var isPaginationEnabled by mutableStateOf(true)

    private var pageWidthPx: Float = 0f
    var pageHeightPx: Float = 0f
    private val exclusionZoneHeightPx: Float

    // Exclusion zone properties
    private val exclusionZoneHeightDp = 10.dp
    val exclusionZoneColor = Color.rgb(173, 216, 230) // Light blue color

    init {
        // Initialize with Letter size by default
        exclusionZoneHeightPx = convertDpToPixel(exclusionZoneHeightDp, context)
        updatePageDimensions(PaperSize.LETTER)
    }

    /**
     * Updates page dimensions based on selected paper size
     */
    fun updatePaperSize(paperSize: PaperSize) {
        updatePageDimensions(paperSize)
    }

    private fun updatePageDimensions(paperSize: PaperSize) {
        when (paperSize) {
            PaperSize.LETTER -> {
                // Letter size: 8.5" x 11"
                val heightToWidthRatio = (11.0f)/(8.5f)
                pageWidthPx = SCREEN_WIDTH.toFloat()
                pageHeightPx = (SCREEN_WIDTH.toFloat()*heightToWidthRatio)
            }
            PaperSize.A4 -> {
                // A4 size: 210mm x 297mm (8.27" x 11.69")
                val heightToWidthRatio = (210.0f)/(297.0f)
                pageWidthPx = SCREEN_WIDTH.toFloat()
                pageHeightPx = (SCREEN_WIDTH.toFloat()*heightToWidthRatio)

            }
        }
    }

    /**
     * Calculates the top Y coordinate of the specified page
     */
    fun getPageTopY(pageIndex: Int): Float {
        if (!isPaginationEnabled || pageIndex <= 0) return 0f

        // Calculate position based on page index
        return pageIndex * (pageHeightPx + exclusionZoneHeightPx)
    }

    /**
     * Calculates the bottom Y coordinate of the specified page
     */
    fun getPageBottomY(pageIndex: Int): Float {
        return getPageTopY(pageIndex) + pageHeightPx
    }

    /**
     * Returns the top Y coordinate of the exclusion zone below the specified page
     */
    fun getExclusionZoneTopY(pageIndex: Int): Float {
        return getPageBottomY(pageIndex)
    }

    /**
     * Returns the bottom Y coordinate of the exclusion zone below the specified page
     */
    fun getExclusionZoneBottomY(pageIndex: Int): Float {
        return getExclusionZoneTopY(pageIndex) + exclusionZoneHeightPx
    }

    /**
     * Determines which page a Y coordinate falls on
     */
    fun getPageIndexForY(y: Float): Int {
        if (!isPaginationEnabled) return 0

        // Handle negative values
        if (y < 0) return 0

        // Calculate page index
        val totalPageUnit = pageHeightPx + exclusionZoneHeightPx
        return (y / totalPageUnit).toInt()
    }

    /**
     * Checks if a Y coordinate falls within an exclusion zone
     */
    fun isInExclusionZone(y: Float): Boolean {
        if (!isPaginationEnabled) return false

        val pageIndex = getPageIndexForY(y)
        val exclusionZoneTop = getExclusionZoneTopY(pageIndex)
        val exclusionZoneBottom = getExclusionZoneBottomY(pageIndex)

        return y in exclusionZoneTop..exclusionZoneBottom
    }

    /**
     * Returns a list of all exclusion zone rectangles visible in the current viewport
     */
    fun getExclusionZonesInViewport(viewportTop: Float, viewportHeight: Float): List<Rect> {
        if (!isPaginationEnabled) return emptyList()

        val result = mutableListOf<Rect>()
        val viewportBottom = viewportTop + viewportHeight

        // Find the first page that might be visible
        var pageIndex = getPageIndexForY(viewportTop)

        // Add all exclusion zones visible in the viewport
        while (true) {
            val exclusionZoneTop = getExclusionZoneTopY(pageIndex)

            // Stop if we're beyond the viewport
            if (exclusionZoneTop > viewportBottom) break

            val exclusionZoneBottom = getExclusionZoneBottomY(pageIndex)

            // Check if this exclusion zone is visible
            if (exclusionZoneBottom >= viewportTop) {
                result.add(
                    Rect(
                        0,
                        exclusionZoneTop.toInt(),
                        Int.MAX_VALUE, // Full width
                        exclusionZoneBottom.toInt()
                    )
                )
            }

            pageIndex++
        }

        return result
    }

    /**
     * Returns the page number for a given page index (1-based)
     */
    fun getPageNumber(pageIndex: Int): Int {
        return pageIndex + 1
    }
}