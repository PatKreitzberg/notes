package com.wyldsoft.notes.classes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import com.wyldsoft.notes.utils.Pen
import com.wyldsoft.notes.utils.Stroke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class PageView(
    val context: Context,
    val coroutineScope: CoroutineScope,
    val id: String,
    val width: Int,
    var viewWidth: Int,
    var viewHeight: Int
) {
    var windowedBitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888)
    var windowedCanvas = Canvas(windowedBitmap)
    var strokes = listOf<Stroke>()
    private var strokesById: HashMap<String, Stroke> = hashMapOf()
    var scroll by mutableIntStateOf(0)
    private val saveTopic = MutableSharedFlow<Unit>()
    var height by mutableIntStateOf(viewHeight)

    init {
        coroutineScope.launch {
            saveTopic.debounce(1000).collect {
                launch { persistBitmap() }
            }
        }

        windowedCanvas.drawColor(Color.WHITE)
    }

    private fun indexStrokes() {
        coroutineScope.launch {
            strokesById = hashMapOf(*strokes.map { s -> s.id to s }.toTypedArray())
        }
    }

    fun addStrokes(strokesToAdd: List<Stroke>) {
        strokes += strokesToAdd
        strokesToAdd.forEach {
            val bottomPlusPadding = it.bottom + 50
            if (bottomPlusPadding > height) height = bottomPlusPadding.toInt()
        }

        indexStrokes()
        persistBitmapDebounced()
    }

    fun removeStrokes(strokeIds: List<String>) {
        strokes = strokes.filter { s -> !strokeIds.contains(s.id) }
        indexStrokes()
        computeHeight()
        persistBitmapDebounced()
    }

    fun getStrokes(strokeIds: List<String>): List<Stroke?> {
        return strokeIds.map { s -> strokesById[s] }
    }

    private fun computeHeight() {
        if (strokes.isEmpty()) {
            height = viewHeight
            return
        }

        val maxStrokeBottom = strokes.maxOf { it.bottom } + 50
        height = maxStrokeBottom.toInt().coerceAtLeast(viewHeight)
    }

    fun computeWidth(): Int {
        if (strokes.isEmpty()) {
            return viewWidth
        }
        val maxStrokeRight = strokes.maxOf { it.right }.plus(50)
        return max(maxStrokeRight.toInt(), viewWidth)
    }

    private fun persistBitmap() {
        val dir = File(context.filesDir, "pages/previews/full/")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, id)
        val os = FileOutputStream(file)
        windowedBitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
        os.close()
    }

    private fun persistBitmapDebounced() {
        coroutineScope.launch {
            saveTopic.emit(Unit)
        }
    }

    fun drawArea(
        area: Rect,
        ignoredStrokeIds: List<String> = listOf(),
        canvas: Canvas? = null
    ) {
        println("DEBUG: drawArea called with area=$area")
        val activeCanvas = canvas ?: windowedCanvas
        val pageArea = Rect(
            area.left,
            area.top + scroll,
            area.right,
            area.bottom + scroll
        )

        activeCanvas.save()
        activeCanvas.clipRect(area)
        activeCanvas.drawColor(Color.WHITE)
        println("DEBUG: Drawing ${strokes.size} strokes")

        try {
            strokes.forEach { stroke ->
                if (ignoredStrokeIds.contains(stroke.id)) {
                    println("DEBUG: Skipping ignored stroke ${stroke.id}")
                    return@forEach
                }

                val bounds = strokeBounds(stroke)
                println("DEBUG: Checking stroke with bounds=$bounds against pageArea=$pageArea")
                if (!bounds.intersect(pageArea)) {
                    println("DEBUG: Stroke out of drawing area, skipping")
                    return@forEach
                }

                println("DEBUG: Drawing stroke with color=${stroke.color}, size=${stroke.size}")
                drawStroke(
                    activeCanvas, stroke, IntOffset(0, -scroll)
                )
            }
        } catch (e: Exception) {
            println("DEBUG ERROR: Error drawing strokes: ${e.message}")
            e.printStackTrace()
        }

        activeCanvas.restore()
        println("DEBUG: drawArea completed")
    }

    fun updateScroll(_delta: Int) {
        var delta = _delta

        if (scroll + delta < 0) {
            delta = -scroll
        }

        scroll += delta

        // Scroll bitmap
        val config = windowedBitmap.config ?: Bitmap.Config.ARGB_8888
        val tmp = windowedBitmap.copy(config, false)
        windowedCanvas.drawColor(Color.WHITE)
        windowedCanvas.drawBitmap(tmp, 0f, -delta.toFloat(), Paint())
        tmp.recycle()

        // Draw the new area
        val canvasOffset = if (delta > 0) windowedCanvas.height - delta else 0
        drawArea(
            area = Rect(
                0,
                canvasOffset,
                windowedCanvas.width,
                canvasOffset + kotlin.math.abs(delta)
            ),
        )

        persistBitmapDebounced()
    }

    fun updateDimensions(newWidth: Int, newHeight: Int) {
        if (newWidth != viewWidth || newHeight != viewHeight) {
            viewWidth = newWidth
            viewHeight = newHeight

            // Recreate bitmap and canvas with new dimensions
            windowedBitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888)
            windowedCanvas = Canvas(windowedBitmap)
            drawArea(Rect(0, 0, viewWidth, viewHeight))
            persistBitmapDebounced()
        }
    }

    private fun strokeBounds(stroke: Stroke): Rect {
        return Rect(
            stroke.left.toInt(),
            stroke.top.toInt(),
            stroke.right.toInt(),
            stroke.bottom.toInt()
        )
    }

    fun drawStroke(canvas: Canvas, stroke: Stroke, offset: IntOffset) {
        val paint = Paint().apply {
            color = stroke.color
            strokeWidth = stroke.size
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        try {
            // Use the pen name string to determine the drawing method
            when (stroke.pen.penName) {
                "BALLPEN" -> drawBallPenStroke(canvas, paint, stroke.size,
                    stroke.points.map { point ->
                        androidx.compose.ui.geometry.Offset(
                            x = point.x + offset.x,
                            y = point.y + offset.y
                        )
                    }
                )
                "MARKER" -> drawMarkerStroke(canvas, paint, stroke.size,
                    stroke.points.map { point ->
                        androidx.compose.ui.geometry.Offset(
                            x = point.x + offset.x,
                            y = point.y + offset.y
                        )
                    }
                )
                "FOUNTAIN" -> drawFountainPenStroke(canvas, paint, stroke.size,
                    stroke.points.map { point ->
                        androidx.compose.ui.geometry.Offset(
                            x = point.x + offset.x,
                            y = point.y + offset.y
                        )
                    }
                )
                else -> drawBallPenStroke(canvas, paint, stroke.size,
                    stroke.points.map { point ->
                        androidx.compose.ui.geometry.Offset(
                            x = point.x + offset.x,
                            y = point.y + offset.y
                        )
                    }
                )
            }
        } catch (e: Exception) {
            println("Error drawing stroke: ${e.message}")
        }
    }

    private fun drawBallPenStroke(
        canvas: Canvas, paint: Paint, strokeSize: Float, points: List<androidx.compose.ui.geometry.Offset>
    ) {
        val path = android.graphics.Path()
        if (points.isEmpty()) return

        val prePoint = android.graphics.PointF(points[0].x, points[0].y)
        path.moveTo(prePoint.x, prePoint.y)

        for (point in points) {
            if (kotlin.math.abs(prePoint.y - point.y) >= 30) continue
            path.quadTo(prePoint.x, prePoint.y, point.x, point.y)
            prePoint.x = point.x
            prePoint.y = point.y
        }

        canvas.drawPath(path, paint)
    }

    private fun drawMarkerStroke(
        canvas: Canvas, paint: Paint, strokeSize: Float, points: List<androidx.compose.ui.geometry.Offset>
    ) {
        val modifiedPaint = Paint(paint).apply {
            this.alpha = 100
        }

        val path = android.graphics.Path()
        if (points.isEmpty()) return

        path.moveTo(points[0].x, points[0].y)

        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
        }

        canvas.drawPath(path, modifiedPaint)
    }

    private fun drawFountainPenStroke(
        canvas: Canvas, paint: Paint, strokeSize: Float, points: List<androidx.compose.ui.geometry.Offset>
    ) {
        if (points.isEmpty()) return

        val path = android.graphics.Path()
        val prePoint = android.graphics.PointF(points[0].x, points[0].y)
        path.moveTo(prePoint.x, prePoint.y)

        for (i in 1 until points.size) {
            val point = points[i]
            if (kotlin.math.abs(prePoint.y - point.y) >= 30) continue

            path.quadTo(prePoint.x, prePoint.y, point.x, point.y)
            prePoint.x = point.x
            prePoint.y = point.y

            // Vary the stroke width based on pressure (simulated)
            val pressureFactor = 1.0f - (i.toFloat() / points.size) * 0.5f
            paint.strokeWidth = strokeSize * pressureFactor

            canvas.drawPath(path, paint)
            path.reset()
            path.moveTo(point.x, point.y)
        }
    }

    private fun drawBallPenStroke(
        canvas: Canvas, paint: Paint, strokeSize: Float, points: List<androidx.compose.ui.geometry.Offset>
    ) {
        val path = android.graphics.Path()
        if (points.isEmpty()) return

        val prePoint = android.graphics.PointF(points[0].x, points[0].y)
        path.moveTo(prePoint.x, prePoint.y)

        for (point in points) {
            if (kotlin.math.abs(prePoint.y - point.y) >= 30) continue
            path.quadTo(prePoint.x, prePoint.y, point.x, point.y)
            prePoint.x = point.x
            prePoint.y = point.y
        }

        canvas.drawPath(path, paint)
    }

    private fun drawMarkerStroke(
        canvas: Canvas, paint: Paint, strokeSize: Float, points: List<androidx.compose.ui.geometry.Offset>
    ) {
        val modifiedPaint = Paint(paint).apply {
            this.alpha = 100
        }

        val path = android.graphics.Path()
        if (points.isEmpty()) return

        path.moveTo(points[0].x, points[0].y)

        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
        }

        canvas.drawPath(path, modifiedPaint)
    }

    private fun drawFountainPenStroke(
        canvas: Canvas, paint: Paint, strokeSize: Float, points: List<androidx.compose.ui.geometry.Offset>
    ) {
        if (points.isEmpty()) return

        val path = android.graphics.Path()
        val prePoint = android.graphics.PointF(points[0].x, points[0].y)
        path.moveTo(prePoint.x, prePoint.y)

        for (i in 1 until points.size) {
            val point = points[i]
            if (kotlin.math.abs(prePoint.y - point.y) >= 30) continue

            path.quadTo(prePoint.x, prePoint.y, point.x, point.y)
            prePoint.x = point.x
            prePoint.y = point.y

            // Vary the stroke width based on pressure (simulated)
            val pressureFactor = 1.0f - (i.toFloat() / points.size) * 0.5f
            paint.strokeWidth = strokeSize * pressureFactor

            canvas.drawPath(path, paint)
            path.reset()
            path.moveTo(point.x, point.y)
        }
    }
}