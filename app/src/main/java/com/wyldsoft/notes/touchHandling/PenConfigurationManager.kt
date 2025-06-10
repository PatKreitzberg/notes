package com.wyldsoft.notes.touchHandling

import com.wyldsoft.notes.utils.EditorState
import com.wyldsoft.notes.utils.Mode
import com.wyldsoft.notes.utils.Pen

/**
 * Manages pen configuration settings for different modes.
 * Handles pen style, stroke width, and color configuration.
 */
class PenConfigurationManager(
    private val state: EditorState
) {
    
    /**
     * Data class for pen configuration
     */
    data class PenConfiguration(
        val strokeStyle: Int,
        val strokeWidth: Float,
        val strokeColor: Int
    )

    /**
     * Gets pen configuration based on current mode
     */
    fun getPenConfiguration(mode: Mode): PenConfiguration? {
        return when (mode) {
            Mode.Draw -> getDrawingConfiguration()
            Mode.Erase -> getErasingConfiguration()
            Mode.Selection -> getSelectionConfiguration()
        }
    }

    /**
     * Gets configuration for drawing mode
     */
    private fun getDrawingConfiguration(): PenConfiguration {
        val strokeStyle = PenStyleConverter.convertPenToStrokeStyle(state.pen)
        val strokeWidth = state.penSettings[state.pen.penName]!!.strokeSize
        val strokeColor = state.penSettings[state.pen.penName]!!.color

        println("DEBUG: Drawing config - style=$strokeStyle, width=$strokeWidth, color=$strokeColor")
        
        return PenConfiguration(strokeStyle, strokeWidth, strokeColor)
    }

    /**
     * Gets configuration for erasing mode
     */
    private fun getErasingConfiguration(): PenConfiguration {
        return when (state.eraser) {
            com.wyldsoft.notes.utils.Eraser.PEN -> {
                PenConfiguration(
                    strokeStyle = PenStyleConverter.convertPenToStrokeStyle(Pen.MARKER),
                    strokeWidth = 30f,
                    strokeColor = android.graphics.Color.GRAY
                )
            }
            com.wyldsoft.notes.utils.Eraser.SELECT -> {
                PenConfiguration(
                    strokeStyle = PenStyleConverter.convertPenToStrokeStyle(Pen.BALLPEN),
                    strokeWidth = 3f,
                    strokeColor = android.graphics.Color.GRAY
                )
            }
        }
    }

    /**
     * Gets configuration for selection mode
     */
    private fun getSelectionConfiguration(): PenConfiguration {
        return PenConfiguration(
            strokeStyle = PenStyleConverter.convertPenToStrokeStyle(Pen.BALLPEN),
            strokeWidth = 2f,
            strokeColor = android.graphics.Color.BLUE
        )
    }
}