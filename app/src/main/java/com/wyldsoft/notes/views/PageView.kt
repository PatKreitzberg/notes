package com.wyldsoft.notes.views

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
import com.wyldsoft.notes.utils.Stroke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import com.wyldsoft.notes.transform.ViewportTransformer
import androidx.core.graphics.createBitmap
import com.wyldsoft.notes.history.HistoryManager
import com.wyldsoft.notes.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log
import androidx.compose.runtime.mutableStateListOf

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
    val tag="PageView:"
    var windowedBitmap = createBitmap(viewWidth, viewHeight)
    var windowedCanvas = Canvas(windowedBitmap)
    var strokes = listOf<Stroke>()
    private var strokesById: HashMap<String, Stroke> = hashMapOf()
    private val saveTopic = MutableSharedFlow<Unit>()
    var height by mutableIntStateOf(viewHeight)

    // Two separate flows for different operations
    private val _strokesAdded = MutableStateFlow<List<Stroke>>(emptyList())
    val strokesAdded = _strokesAdded.asStateFlow()

    private val _strokesRemoved = MutableStateFlow<List<String>>(emptyList())
    val strokesRemoved = _strokesRemoved.asStateFlow()

    // Combined flow for backward compatibility
    private val _strokesChanged = MutableStateFlow<List<Stroke>>(emptyList())
    val strokesChanged = _strokesChanged.asStateFlow()

    // transformer for scrolling, zoom, etc
    private var _viewportTransformer: ViewportTransformer? = null
    val viewportTransformer: ViewportTransformer
        get() = _viewportTransformer ?: throw IllegalStateException("ViewportTransformer not initialized")


    private val _visibleStrokes = mutableStateListOf<Stroke>()
    val visibleStrokes: List<Stroke>
        get() = _visibleStrokes.toList()


    init {
        coroutineScope.launch {
            saveTopic.debounce(1000).collect {
                launch { persistBitmap() }
            }
        }
        // Register as the active page
        (context.applicationContext as? com.wyldsoft.notes.NotesApp)?.setActivePageId(id)
        (context.applicationContext as? com.wyldsoft.notes.NotesApp)?.setActivePage(this)

        windowedCanvas.drawColor(Color.WHITE)
    }

    fun deactivate() {
        val app = context.applicationContext as? com.wyldsoft.notes.NotesApp
        if (app?.activePageView?.id == id) {
            app.activePageView = null
            app.setActivePageId("")
        }
    }

    fun updateVisibleStrokes() {
        _visibleStrokes.clear()

        // Add strokes that intersect with the current viewport
        strokes.forEach { stroke ->
            if (isStrokeVisible(stroke)) {
                _visibleStrokes.add(stroke)
                println("stroke: is  visible")
            } else{
                println("stroke: not visible")
            }
        }
    }

    fun initializeViewportTransformer(
        context: Context,
        coroutineScope: CoroutineScope,
        settingsRepository: SettingsRepository = SettingsRepository(context)
    ) {
        _viewportTransformer = ViewportTransformer(
            context = context,
            coroutineScope = coroutineScope,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            settingsRepository = settingsRepository
        )

        // Initialize with current height
        _viewportTransformer?.updateDocumentHeight(height)

        // Apply saved settings
        val settings = settingsRepository.getSettings()
        _viewportTransformer?.updatePaginationState(settings.isPaginationEnabled)
        _viewportTransformer?.updatePaperSizeState(settings.paperSize)

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
                updateVisibleStrokes()

                // Redraw the area
                drawArea(rect)

                // Make sure to refresh the display
                coroutineScope.launch {
                    com.wyldsoft.notes.classes.drawing.DrawingManager.forceUpdate.emit(rect)
                }
            }
        }
        updateVisibleStrokes()
    }

    private fun indexStrokes() {
        coroutineScope.launch {
            strokesById = hashMapOf(*strokes.map { s -> s.id to s }.toTypedArray())
        }
    }

    fun addStrokes(strokesToAdd: List<Stroke>, registerChange: Boolean = true) {
        strokes += strokesToAdd
        strokesToAdd.forEach {
            val bottomPlusPadding = it.bottom + 50
            if (bottomPlusPadding > height) height = bottomPlusPadding.toInt()
        }

        indexStrokes()
        persistBitmapDebounced()

        // Notify that strokes have been added
        coroutineScope.launch {
            _strokesAdded.emit(strokesToAdd)
            // For backward compatibility
            _strokesChanged.emit(strokesToAdd)

            // Register note change for syncing
            (context.applicationContext as? com.wyldsoft.notes.NotesApp)?.let { app ->
                if (registerChange) {
                    println("registerNoteChanged  addStrokes $registerChange")
                    app.syncManager.changeTracker.registerNoteChanged(id)
                }
            }
        }
        updateVisibleStrokes()
    }

    fun removeStrokes(strokeIds: List<String>) {
        // Remove the strokes
        strokes = strokes.filter { s -> !strokeIds.contains(s.id) }

        indexStrokes()
        computeHeight()
        persistBitmapDebounced()

        // Notify that strokes have been removed using their IDs
        coroutineScope.launch {
            _strokesRemoved.emit(strokeIds)
            // For backward compatibility, emit an empty list to signal deletion
            _strokesChanged.emit(emptyList())

            // Register note change for syncing
            println("registerNoteChanged removeStrokes")
            (context.applicationContext as? com.wyldsoft.notes.NotesApp)?.let { app ->
                app.syncManager.changeTracker.registerNoteChanged(id)
            }
        }
        updateVisibleStrokes()
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
        val activeCanvas = canvas ?: windowedCanvas

        val height = area.bottom-area.top
        val width = area.right - area.left
        Log.w(tag, "drawArea rect (left, top) (right, bot): (${area.left}, ${area.top}) to (${area.right}, ${area.bottom})  height: $height width: $width")

        // Save canvas state
        activeCanvas.save()
        activeCanvas.clipRect(area)
        activeCanvas.drawColor(Color.WHITE)

        visibleStrokes.forEach { stroke ->
            if (ignoredStrokeIds.contains(stroke.id)) {
                return@forEach
            }

            if (!isStrokeVisible(stroke)) {
                // Skip stroke if it's not visible in current viewport
                //println("scroll skip Stroke in drawArea. Area: (${area.left}, ${area.top}) to (${area.right}, ${area.bottom})")
                return@forEach
            }

            // Draw the stroke with proper transformation
            drawStroke(activeCanvas, stroke)
        }

        // Restore canvas state
        activeCanvas.restore()
    }

    /**
     * Draws all visible strokes to the canvas
     * This method is called when the page is first loaded
     */
    fun drawCanvasToViewport() {
        // Get the current viewport
        val viewport = viewportTransformer.getCurrentViewportInPageCoordinates()

        // Create a rect that covers the entire viewport
        val viewportRect = Rect(
            0,
            viewport.top.toInt(),
            viewport.right.toInt(),
            viewport.bottom.toInt()
        )

        // Draw all strokes in the viewport
        drawArea(viewportRect)

        println("DEBUG: Auto-draw complete for viewport: $viewportRect")
    }

    /**
     * Gets the history manager for this page
     */
    fun getHistoryManager(): HistoryManager? {
        val app = (context.applicationContext as? com.wyldsoft.notes.NotesApp)
        val manager = app?.historyRepository?.getHistoryManager(id)
        println("undo: Retrieved history manager for page $id: ${manager != null}")
        return manager
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

    fun strokeBounds(stroke: Stroke): RectF {
        return RectF(stroke.left, stroke.top, stroke.right, stroke.bottom)
    }

    fun isStrokeVisible(stroke: Stroke): Boolean {
        return viewportTransformer.isRectVisible(strokeBounds(stroke))
    }

    fun drawStroke(canvas: Canvas, stroke: Stroke) {
        // Check if stroke is visible first
        if (!isStrokeVisible(stroke)) {
            println("scroll drawStroke skip stroke=${stroke.createdScrollY}")
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
                //Log.w(tag, "stroke transformed (${viewX}, ${viewY})")
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