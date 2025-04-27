// app/src/main/java/com/wyldsoft/notes/templates/TemplateRenderer.kt
package com.wyldsoft.notes.templates

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.unit.dp
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
        color = Color.BLACK //Color.argb(50, 0, 0, 200) // Light blue with 50% opacity
        strokeWidth = 5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    // Grid spacing in dp
    private val gridSpacingDp = 20.dp

    // Ruled line spacing in dp
    private val ruledLineSpacingDp = 30.dp

    /**
     * Renders the selected template on the canvas
     */
    fun renderTemplate(
        canvas: Canvas,
        templateType: TemplateType,
        paperSize: PaperSize,
        viewportTop: Float,
        viewportHeight: Float,
        viewportWidth: Float
    ) {
        when (templateType) {
            TemplateType.BLANK -> {
                // Do nothing for blank template
                return
            }
            TemplateType.GRID -> {
                renderGridTemplate(canvas, paperSize, viewportTop, viewportHeight, viewportWidth)
            }
            TemplateType.RULED -> {
                renderRuledTemplate(canvas, paperSize, viewportTop, viewportHeight, viewportWidth)
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
     * Renders ruled lines template
     */
    private fun renderRuledTemplate(
        canvas: Canvas,
        paperSize: PaperSize,
        viewportTop: Float,
        viewportHeight: Float,
        viewportWidth: Float
    ) {
        val lineSpacing = convertDpToPixel(ruledLineSpacingDp, context)

        // Calculate the line boundaries
        val startY = Math.floor((viewportTop / lineSpacing).toDouble()) * lineSpacing
        val endY = viewportTop + viewportHeight + lineSpacing

        // Draw horizontal lines
        var y = startY.toFloat()
        while (y < endY) {
            val screenY = y - viewportTop
            canvas.drawLine(0f, screenY, viewportWidth, screenY, ruledPaint)
            y += lineSpacing
        }
    }
}