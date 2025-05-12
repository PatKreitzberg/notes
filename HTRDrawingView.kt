package com.example.handwritingtotext

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.mlkit.vision.digitalink.Ink
import java.util.ArrayList

class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val STROKE_WIDTH = 10f
    }

    private val paint = Paint().apply {
        isAntiAlias = true
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeWidth = STROKE_WIDTH
    }

    private var path = Path()
    private var lastX = 0f
    private var lastY = 0f
    private val paths = ArrayList<Path>()

    // For ML Kit Ink Recognition
    private lateinit var strokeBuilder: Ink.Stroke.Builder
    private var inkBuilder: Ink.Builder? = null

    init {
        // Initialize the Paint object
        paint.isAntiAlias = true
        paint.color = Color.BLACK
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = STROKE_WIDTH
    }

    fun setInkBuilder(inkBuilder: Ink.Builder) {
        this.inkBuilder = inkBuilder
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw all saved paths
        for (p in paths) {
            canvas.drawPath(p, paint)
        }

        // Draw current path
        canvas.drawPath(path, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val t = System.currentTimeMillis()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Start a new stroke
                path = Path()
                paths.add(path)
                path.moveTo(x, y)
                lastX = x
                lastY = y

                // Create a new stroke builder for the ML Kit ink
                strokeBuilder = Ink.Stroke.builder()
                strokeBuilder.addPoint(Ink.Point.create(x, y, t))
            }

            MotionEvent.ACTION_MOVE -> {
                // Add points to the path
                path.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2)
                lastX = x
                lastY = y

                // Add points to the ML Kit ink
                strokeBuilder.addPoint(Ink.Point.create(x, y, t))
            }

            MotionEvent.ACTION_UP -> {
                // Finish the path
                path.lineTo(x, y)

                // Add the final point and build the stroke
                strokeBuilder.addPoint(Ink.Point.create(x, y, t))
                inkBuilder?.addStroke(strokeBuilder.build())
            }

            else -> return false
        }

        invalidate()
        return true
    }

    fun clear() {
        path = Path()
        paths.clear()
        invalidate()
    }
}