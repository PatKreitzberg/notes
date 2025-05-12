// app/src/main/java/com/wyldsoft/notes/search/SearchHighlighter.kt
package com.wyldsoft.notes.search

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.wyldsoft.notes.transform.ViewportTransformer

/**
 * Handles highlighting of search results on the canvas
 */
class SearchHighlighter(
    private val searchManager: SearchManager,
    private val viewportTransformer: ViewportTransformer
) {
    // Paint for the highlight behind search results
    private val highlightPaint = Paint().apply {
        color = android.graphics.Color.argb(100, 255, 255, 0) // Semi-transparent yellow
        style = Paint.Style.FILL
    }

    // Paint for the highlight of the current result
    private val currentHighlightPaint = Paint().apply {
        color = android.graphics.Color.argb(150, 255, 165, 0) // Semi-transparent orange
        style = Paint.Style.FILL
    }

    /**
     * Draw highlights for all search results
     * @param canvas The canvas to draw on
     */
    fun drawHighlights(canvas: Canvas) {
        val results = searchManager.searchResults.value
        if (results.isEmpty()) return

        val currentResultIndex = searchManager.currentResultIndex

        results.forEachIndexed { index, result ->
            // Determine if this is the current result
            val isCurrent = index + 1 == currentResultIndex

            // Create a rectangle for the highlight
            // In a real implementation, you would need to map text positions
            // to actual page coordinates more accurately
            val left = 50f // Left margin
            val top = result.yPosition
            val right = viewportTransformer.viewWidth - 50f // Right margin
            val bottom = result.yPosition + 40f // Assuming 40px line height

            // Transform to view coordinates
            val (viewLeft, viewTop) = viewportTransformer.pageToViewCoordinates(left, top)
            val (viewRight, viewBottom) = viewportTransformer.pageToViewCoordinates(right, bottom)

            val rect = RectF(viewLeft, viewTop, viewRight, viewBottom)

            // Draw highlight
            canvas.drawRect(rect, if (isCurrent) currentHighlightPaint else highlightPaint)
        }
    }
}