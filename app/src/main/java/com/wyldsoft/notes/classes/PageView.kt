package com.wyldsoft.notes.classes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import com.wyldsoft.notes.classes.drawing.DrawingManager
import com.wyldsoft.notes.utils.Stroke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import com.wyldsoft.notes.transform.ViewportTransformer

/**
 * Responsible for managing the page content and rendering.
 * Handles stroke storage, bitmap rendering, and persistence.
 */
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
    private val saveTopic = MutableSharedFlow<Unit>()
    var height by mutableIntStateOf(viewHeight)

    // transformer for scrolling, zoom, etc
    private var _viewportTransformer: ViewportTransformer? = null
    val viewportTransformer: ViewportTransformer
        get() = _viewportTransformer ?: throw IllegalStateException("ViewportTransformer not initialized")


    init {
        coroutineScope.launch {
            saveTopic.debounce(1000).collect {
                launch { persistBitmap() }
            }
        }

        windowedCanvas.drawColor(Color.WHITE)
    }

    fun initializeViewportTransformer(
        context: Context,
        coroutineScope: CoroutineScope
    ) {
        _viewportTransformer = ViewportTransformer(
            context = context,
            coroutineScope = coroutineScope,
            viewWidth = viewWidth,
            viewHeight = viewHeight
        )

        // Initialize with current height
        _viewportTransformer?.updateDocumentHeight(height)

        // Listen to viewport changes
        coroutineScope.launch {
            _viewportTransformer?.viewportChanged?.collect {
                // Redraw the visible area
                val viewport = _viewportTransformer?.getCurrentViewportInPageCoordinates() ?: return@collect
                val rect = Rect(
                    0,
                    viewport.top.toInt(),
                    viewport.right.toInt(),
                    viewport.bottom.toInt()
                )

                // Redraw the area
                drawArea(rect)

                // Make sure to refresh the display
                coroutineScope.launch {
                    com.wyldsoft.notes.classes.drawing.DrawingManager.forceUpdate.emit(rect)
                }
            }
        }
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

    private fun computeHeight() {
        if (strokes.isEmpty()) {
            height = viewHeight
            viewportTransformer.updateDocumentHeight(height)
            return
        }

        val maxStrokeBottom = strokes.maxOf { it.bottom } + 50
        height = maxStrokeBottom.toInt().coerceAtLeast(viewHeight)
        viewportTransformer.updateDocumentHeight(height)
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

    fun persistBitmapDebounced() {
        coroutineScope.launch {
            saveTopic.emit(Unit)
        }
    }

    fun drawArea(
        area: Rect,
        ignoredStrokeIds: List<String> = listOf(),
        canvas: Canvas? = null
    ) {
        val activeCanvas = canvas ?: windowedCanvas

        // Save canvas state
        activeCanvas.save()
        activeCanvas.clipRect(area)
        activeCanvas.drawColor(Color.WHITE)

        strokes.forEach { stroke ->
            if (ignoredStrokeIds.contains(stroke.id)) {
                return@forEach
            }

            // Check if stroke is visible in current viewport
            val strokeRectF = RectF(
                stroke.left,
                stroke.top,
                stroke.right,
                stroke.bottom
            )

            if (!viewportTransformer.isRectVisible(strokeRectF)) {
                // Skip stroke if it's not visible in current viewport
                return@forEach
            }

            println("scroll drawArea drawStroke")
            // Draw the stroke with proper transformation
            drawStroke(activeCanvas, stroke)
        }

        // Restore canvas state
        activeCanvas.restore()
    }

    fun updateDimensions(newWidth: Int, newHeight: Int) {
        if (newWidth != viewWidth || newHeight != viewHeight) {
            viewWidth = newWidth
            viewHeight = newHeight

            // Update viewport transformer
            if (_viewportTransformer != null) {
                // Re-initialize with new dimensions
                initializeViewportTransformer(context, coroutineScope)
            }

            // Recreate bitmap and canvas with new dimensions
            windowedBitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888)
            windowedCanvas = Canvas(windowedBitmap)
            drawArea(Rect(0, 0, viewWidth, viewHeight))
            persistBitmapDebounced()
        }
    }

    fun isStrokeVisible(stroke: Stroke): Boolean {
        return viewportTransformer.isRectVisible(RectF(stroke.left, stroke.top, stroke.right, stroke.bottom))
    }

    fun drawStroke(canvas: Canvas, stroke: Stroke) {
        // Check if stroke is visible first
        if (!isStrokeVisible(stroke)) {
            return // Skip drawing if stroke is not visible
        }

        val paint = Paint().apply {
            color = stroke.color
            strokeWidth = stroke.size
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        try {
            // Transform the points to view coordinates
            val transformedPoints = stroke.points.map { point ->
                val (viewX, viewY) = viewportTransformer.pageToViewCoordinates(
                    point.x,
                    point.y
                )
                androidx.compose.ui.geometry.Offset(viewX, viewY)
            }

            // Use the pen name to determine drawing method
            when (stroke.pen.penName) {
                "BALLPEN" -> drawBallPenStroke(canvas, paint, stroke.size, transformedPoints)
                "MARKER" -> drawMarkerStroke(canvas, paint, stroke.size, transformedPoints)
                "FOUNTAIN" -> drawFountainPenStroke(canvas, paint, stroke.size, transformedPoints)
                else -> drawBallPenStroke(canvas, paint, stroke.size, transformedPoints)
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
}