package com.wyldsoft.notes.touchHandling

import android.content.Context
import android.graphics.Rect
import android.view.SurfaceView
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.refreshingScreen.interfaces.IViewportTransformer
import com.wyldsoft.notes.utils.EditorState
import com.wyldsoft.notes.utils.convertDpToPixel

/**
 * Manages exclusion zones where touch input should be ignored.
 * Handles toolbar, pagination, and custom exclusion areas.
 */
class ExclusionZoneManager(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val state: EditorState,
    private val viewportTransformer: IViewportTransformer
) {
    private var isStrokeOptionsPanelOpen = false

    /**
     * Calculates all active exclusion zones
     */
    fun calculateExclusionZones(): List<Rect> {
        val excludeRects = mutableListOf<Rect>()

        // Add toolbar exclusion zone
        addToolbarExclusionZone(excludeRects)

        // Add custom state exclusion zones
        addStateExclusionZones(excludeRects)

        // Add pagination exclusion zones
        addPaginationExclusionZones(excludeRects)

        // Add global exclusion if drawing disabled
        addGlobalExclusionZone(excludeRects)

        return excludeRects
    }

    /**
     * Updates stroke options panel state
     */
    fun setStrokeOptionsPanelOpen(isOpen: Boolean) {
        isStrokeOptionsPanelOpen = isOpen
    }

    /**
     * Adds toolbar exclusion zone
     */
    private fun addToolbarExclusionZone(excludeRects: MutableList<Rect>) {
        val toolbarHeight = if (state.isToolbarOpen) {
            convertDpToPixel(40.dp, context).toInt()
        } else {
            0
        }

        val optionsPanelHeight = if (isStrokeOptionsPanelOpen) {
            convertDpToPixel(310.dp, context).toInt()
        } else {
            0
        }

        val totalTopExclusion = toolbarHeight + optionsPanelHeight
        
        if (totalTopExclusion > 0) {
            excludeRects.add(Rect(0, 0, surfaceView.width, totalTopExclusion))
        }
    }

    /**
     * Adds state-defined exclusion zones
     */
    private fun addStateExclusionZones(excludeRects: MutableList<Rect>) {
        state.stateExcludeRects.values.forEach { rect ->
            excludeRects.add(rect)
        }
        state.stateExcludeRectsModified = false
    }

    /**
     * Adds pagination-based exclusion zones
     */
    private fun addPaginationExclusionZones(excludeRects: MutableList<Rect>) {
        // Implementation would depend on the pagination manager interface
        // This is a simplified version
        if (state.allowDrawingOnCanvas) {
            // Add pagination exclusion zones based on viewport
            // Implementation would use pagination manager
        }
    }

    /**
     * Adds global exclusion zone if drawing is disabled
     */
    private fun addGlobalExclusionZone(excludeRects: MutableList<Rect>) {
        if (!state.allowDrawingOnCanvas) {
            excludeRects.add(Rect(0, 0, surfaceView.width, surfaceView.height))
        }
    }
}