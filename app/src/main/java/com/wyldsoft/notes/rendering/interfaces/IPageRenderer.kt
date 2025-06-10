package com.wyldsoft.notes.rendering.interfaces

import android.graphics.Canvas

/**
 * Interface for pagination rendering operations
 */
interface IPageRenderer {
    /**
     * Renders pagination visual elements like page numbers and exclusion zones
     */
    fun renderPaginationElements(canvas: Canvas)
}