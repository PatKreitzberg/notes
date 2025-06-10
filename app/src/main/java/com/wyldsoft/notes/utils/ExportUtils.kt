// app/src/main/java/com/wyldsoft/notes/utils/ExportUtils.kt
package com.wyldsoft.notes.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.pagination.PaginationManager
import com.wyldsoft.notes.settings.PaperSize
import com.wyldsoft.notes.settings.SettingsRepository
import com.wyldsoft.notes.settings.TemplateType
import com.wyldsoft.notes.templates.TemplateRenderer
import com.wyldsoft.notes.refreshingScreen.ViewportTransformer
import com.wyldsoft.notes.views.PageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream

import com.wyldsoft.notes.SCREEN_WIDTH
import com.wyldsoft.notes.SCREEN_HEIGHT

private const val TAG = "ExportUtils"

/**
 * Exports the current page to a PDF file
 *
 * @param context The application context
 * @param pageId The ID of the page to export
 * @return A message indicating success or failure
 */
suspend fun exportPageToPdf(context: Context, pageId: String): String = withContext(Dispatchers.IO) {
    try {
        val app = context.applicationContext as com.wyldsoft.notes.NotesApp
        val noteRepository = app.noteRepository
        val settingsRepository = app.settingsRepository

        // Get the note and its settings
        val note = noteRepository.getNoteById(pageId) ?: throw IOException("Note not found")
        val settings = settingsRepository.getSettings()
        val isPaginationEnabled = settings.isPaginationEnabled
        val paperSize = settings.paperSize
        val template = settings.template

        // Get all strokes for the note
        val strokes = noteRepository.getStrokesForNote(pageId)

        // Determine paper dimensions based on settings
        val paperDimensions = getPaperDimensionsInPixels(context, paperSize)
        val paperWidth = paperDimensions.first
        val paperHeight = paperDimensions.second

        // Save the PDF using appropriate content
        val result = saveFile(context, "Notes-page-${pageId}", "pdf") { outputStream ->
            val document = PdfDocument()

            if (isPaginationEnabled) {
                // Create multiple pages based on pagination
                exportPaginatedPdf(document, context, strokes, paperSize, template, paperWidth, paperHeight)
            } else {
                // Create a single page tall enough for all content
                exportSinglePagePdf(document, context, strokes, paperSize, template, paperWidth)
            }

            // Write the PDF document to the output stream
            document.writeTo(outputStream)
            document.close()
        }

        return@withContext result
    } catch (e: Exception) {
        Log.e(TAG, "Error exporting PDF: ${e.message}", e)
        return@withContext "Error creating PDF: ${e.message}"
    }
}

/**
 * Exports a PDF with pagination enabled (multiple pages)
 */
private fun exportPaginatedPdf(
    document: PdfDocument,
    context: Context,
    strokes: List<Stroke>,
    paperSize: PaperSize,
    templateType: TemplateType,
    paperWidth: Int,
    paperHeight: Int
) {
    // Create pagination manager to determine page boundaries
    val paginationManager = PaginationManager(context)
    paginationManager.updatePaperSize(paperSize)
    paginationManager.isPaginationEnabled = true

    // Create template renderer for drawing templates
    val templateRenderer = TemplateRenderer(context)

    // Group strokes by page
    val strokesByPage = mutableMapOf<Int, MutableList<Stroke>>()

    // Determine which strokes belong to which page
    for (stroke in strokes) {
        // Get the page index for the stroke (using top and bottom positions)
        val topPageIndex = paginationManager.getPageIndexForY(stroke.top)
        val bottomPageIndex = paginationManager.getPageIndexForY(stroke.bottom)

        // If stroke spans multiple pages, add it to all pages it touches
        for (pageIndex in topPageIndex..bottomPageIndex) {
            if (!strokesByPage.containsKey(pageIndex)) {
                strokesByPage[pageIndex] = mutableListOf()
            }
            strokesByPage[pageIndex]?.add(stroke)
        }
    }

    // Skip empty pages - only create pages that have strokes
    for ((pageIndex, pageStrokes) in strokesByPage) {
        // Create a new PDF page for each paginated page with content
        val dimInPostScript = getPaperDimensionsInPostScript(paperSize)
        val scaleX = (dimInPostScript.first.toFloat())/(paperWidth.toFloat())
        val scaleY = (dimInPostScript.second.toFloat())/(paperHeight.toFloat())

        val pageInfo = PdfDocument.PageInfo.Builder((paperWidth*scaleX).toInt(), (paperHeight*scaleY).toInt(), pageIndex + 1).create()
        val page = document.startPage(pageInfo)
        page.canvas.scale(scaleX, scaleY)
        val canvas = page.canvas

        // Fill with white background
        canvas.drawColor(android.graphics.Color.WHITE)

        // Draw the template
        templateRenderer.renderTemplate(
            canvas,
            templateType,
            paperSize,
            paginationManager.getPageTopY(pageIndex), // Viewport top
            paperHeight.toFloat(), // Viewport height
            paperWidth.toFloat(), // Viewport width
            paginationManager // Pass pagination manager for proper template rendering
        )

        // Draw each stroke on the page, adjusting coordinates based on page position
        val pageTop = paginationManager.getPageTopY(pageIndex)
        for (stroke in pageStrokes) {
            drawStrokeOnCanvas(canvas, stroke, pageTop, paperWidth.toFloat())
        }

        println("screen: Scaling canvas after ${page.canvas.width} ${page.canvas.height}")

        println("screen: Scaling canvas after ${page.canvas.width} ${page.canvas.height}")

        // Finish the page
        document.finishPage(page)
    }
}

/**
 * Exports a PDF with pagination disabled (single tall page)
 */
private fun exportSinglePagePdf(
    document: PdfDocument,
    context: Context,
    strokes: List<Stroke>,
    paperSize: PaperSize,
    templateType: TemplateType,
    paperWidth: Int
) {
    // Calculate height based on stroke positions
    val bottomMargin = 100 // Extra margin at the bottom (in pixels)
    val pageHeight = if (strokes.isEmpty()) {
        // Default height from paper size if no strokes
        getPaperDimensionsInPixels(context, paperSize).second
    } else {
        // Find the bottom-most stroke position and add margin
        val maxBottom = strokes.maxOf { it.bottom }
        (maxBottom + bottomMargin).toInt()
    }

    // Create a single page with calculated height
    val pageInfo = PdfDocument.PageInfo.Builder(paperWidth, pageHeight, 1).create()
    val page = document.startPage(pageInfo)
    val canvas = page.canvas

    // Fill with white background
    canvas.drawColor(android.graphics.Color.WHITE)

    // Create template renderer
    val templateRenderer = TemplateRenderer(context)

    // Create a viewport transformer for proper template rendering without pagination
    val viewportTransformer = ViewportTransformer(
        context,
        CoroutineScope(Dispatchers.IO),
        paperWidth,
        pageHeight,
        SettingsRepository(context)
    )
    viewportTransformer.updatePaginationState(false)
    viewportTransformer.updatePaperSizeState(paperSize)

    // Draw the template repeatedly to fill the page height
    // This is needed because the template is designed for viewport-sized chunks
    var yOffset = 0f
    val chunkHeight = 1000f // Render template in chunks to avoid memory issues

    while (yOffset < pageHeight) {
        templateRenderer.renderTemplate(
            canvas,
            templateType,
            paperSize,
            yOffset, // Viewport top
            chunkHeight, // Viewport height
            paperWidth.toFloat(), // Viewport width
            null // No pagination manager for non-paginated mode
        )
        yOffset += chunkHeight
    }

    // Draw all strokes at their absolute positions
    for (stroke in strokes) {
        drawStrokeOnCanvas(canvas, stroke, 0f, paperWidth.toFloat())
    }

    // Finish the page
    document.finishPage(page)
}

/**
 * Helper function to draw a stroke on canvas with proper positioning
 */
private fun drawStrokeOnCanvas(
    canvas: Canvas,
    stroke: Stroke,
    pageTop: Float,
    maxWidth: Float
) {
    val paint = Paint().apply {
        color = stroke.color
        strokeWidth = stroke.size
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    try {
        // Adjust stroke coordinates relative to page top
        val adjustedPoints = stroke.points.map { point ->
            // Ensure points stay within page width
            val x = point.x.coerceIn(0f, maxWidth)
            val y = point.y - pageTop
            androidx.compose.ui.geometry.Offset(x, y)
        }

        // Use the pen name to determine drawing method
        when (stroke.pen.penName) {
            "BALLPEN" -> drawBallPenStroke(canvas, paint, stroke.size, adjustedPoints)
            "MARKER" -> drawMarkerStroke(canvas, paint, stroke.size, adjustedPoints)
            "FOUNTAIN" -> drawFountainPenStroke(canvas, paint, stroke.size, adjustedPoints)
            else -> drawBallPenStroke(canvas, paint, stroke.size, adjustedPoints)
        }
    } catch (e: Exception) {
        println("Error drawing stroke: ${e.message}")
    }
}

/**
 * Get paper dimensions in pixels based on paper size
 * @return Pair of (width, height) in pixels
 */
private fun getPaperDimensionsInPixels(context: Context, paperSize: PaperSize): Pair<Int, Int> {
    return when (paperSize) {
        PaperSize.LETTER -> {
            // Letter size: 8.5" x 11"
            val heightToWidthRatio = (11.0f)/(8.5f)
            val widthPx = SCREEN_WIDTH
            val heightPx = (SCREEN_WIDTH*heightToWidthRatio).toInt()

            println("screen: letter $widthPx $heightPx")
            Pair(widthPx, heightPx)
        }
        PaperSize.A4 -> {
            // A4 size: 210mm x 297mm (8.27" x 11.69")
            val heightToWidthRatio = 210.0f / 297.0f
            val widthPx = SCREEN_WIDTH
            val heightPx = (SCREEN_WIDTH*heightToWidthRatio).toInt()
            Pair(widthPx, heightPx)
        }
    }
}

private fun getPaperDimensionsInPostScript(paperSize: PaperSize): Pair<Int, Int> {
    return when (paperSize) {
        PaperSize.LETTER -> {
            // Letter size: 8.5" x 11"
            val heightToWidthRatio = (11.0f)/(8.5f)
            val widthPx = SCREEN_WIDTH
            val heightPx = (SCREEN_WIDTH*heightToWidthRatio).toInt()
            val inchesPerPixel = (8.50f/widthPx)
            val postScriptPerInch = 72.0f
            val pxToPostScriptRatio = inchesPerPixel*postScriptPerInch

            println("screen: letter $widthPx $heightPx")
            Pair((widthPx*pxToPostScriptRatio).toInt(), (heightPx*pxToPostScriptRatio).toInt())
        }
        PaperSize.A4 -> {
            // A4 size: 210mm x 297mm (8.27" x 11.69")
            val heightToWidthRatio = 210.0f / 297.0f
            val widthPx = SCREEN_WIDTH
            val heightPx = (SCREEN_WIDTH*heightToWidthRatio).toInt()
            val inchesPerPixel = (8.27f/widthPx)
            val postScriptPerInch = 72.0f
            val pxToPostScriptRatio = inchesPerPixel*postScriptPerInch

            println("screen: letter $widthPx $heightPx")
            Pair((widthPx*pxToPostScriptRatio).toInt(), (heightPx*pxToPostScriptRatio).toInt())
        }
    }
}


// Draw stroke functions - copied from PageView for consistent rendering
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

/**
 * Exports the current page to a PNG file
 *
 * @param context The application context
 * @param pageId The ID of the page to export
 * @return A message indicating success or failure
 */
suspend fun exportPageToPng(context: Context, pageId: String): String = withContext(Dispatchers.IO) {
    try {
        // First create the bitmap for the page
        val bitmap = drawPageBitmap(context, pageId)

        // Save the PNG using the bitmap
        val result = saveFile(context, "notes-page-${pageId}", "png") { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        }

        // Add link to clipboard
        copyPageImageLinkForObsidian(context, pageId, "png")

        // Recycle the bitmap to free memory
        bitmap.recycle()

        return@withContext result
    } catch (e: Exception) {
        Log.e(TAG, "Error exporting PNG: ${e.message}", e)
        return@withContext "Error creating PNG: ${e.message}"
    }
}

/**
 * Exports the current page to a JPEG file
 *
 * @param context The application context
 * @param pageId The ID of the page to export
 * @return A message indicating success or failure
 */
suspend fun exportPageToJpeg(context: Context, pageId: String): String = withContext(Dispatchers.IO) {
    try {
        // First create the bitmap for the page
        val bitmap = drawPageBitmap(context, pageId)

        // Save the JPEG using the bitmap
        val result = saveFile(context, "notes-page-${pageId}", "jpg") { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        }

        // Add link to clipboard
        copyPageImageLinkForObsidian(context, pageId, "jpg")

        // Recycle the bitmap to free memory
        bitmap.recycle()

        return@withContext result
    } catch (e: Exception) {
        Log.e(TAG, "Error exporting JPEG: ${e.message}", e)
        return@withContext "Error creating JPEG: ${e.message}"
    }
}

/**
 * Draws the page to a bitmap
 *
 * @param context The application context
 * @param pageId The ID of the page to export
 * @return A bitmap of the page
 */
private suspend fun drawPageBitmap(context: Context, pageId: String): Bitmap = withContext(Dispatchers.IO) {
    val app = context.applicationContext as com.wyldsoft.notes.NotesApp
    val noteRepository = app.noteRepository

    // Get the note from the repository
    val note = noteRepository.getNoteById(pageId) ?: throw IOException("Note not found")

    // Create a new bitmap with the note dimensions
    val bitmap = Bitmap.createBitmap(note.width, note.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Fill with white background
    canvas.drawColor(android.graphics.Color.WHITE)

    // Get all strokes for the note
    val strokes = noteRepository.getStrokesForNote(pageId)

    // Create a temporary PageView to draw the strokes
    val tempPage = PageView(
        context = context,
        coroutineScope = CoroutineScope(Dispatchers.IO),
        id = pageId,
        width = note.width,
        viewWidth = note.width,
        viewHeight = note.height
    )

    // Initialize viewport transformer for proper rendering
    tempPage.initializeViewportTransformer(context, CoroutineScope(Dispatchers.IO))

    // Add the strokes to the page
    tempPage.addStrokes(strokes)

    // Draw each stroke directly to the canvas
    for (stroke in strokes) {
        tempPage.drawStroke(canvas, stroke)
    }

    return@withContext bitmap
}

/**
 * Saves a file to the device
 *
 * @param context The application context
 * @param fileName The name of the file
 * @param format The format of the file (pdf, png, jpg)
 * @param dictionary Optional subdirectory to save in
 * @param generateContent A callback to generate the file content
 * @return A message indicating success or failure
 */
private suspend fun saveFile(
    context: Context,
    fileName: String,
    format: String,
    dictionary: String = "",
    generateContent: (OutputStream) -> Unit
): String = withContext(Dispatchers.IO) {
    try {
        val mimeType = when (format.lowercase()) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            else -> return@withContext "Unsupported file format"
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, "$fileName.$format")
            put(MediaStore.Files.FileColumns.MIME_TYPE, mimeType)
            put(
                MediaStore.Files.FileColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOCUMENTS + "/Notes/" + dictionary
            )
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            ?: throw IOException("Failed to create Media Store entry")

        resolver.openOutputStream(uri)?.use { outputStream ->
            generateContent(outputStream)
        }

        return@withContext "File saved successfully as $fileName.$format"
    } catch (e: SecurityException) {
        Log.e(TAG, "Permission error: ${e.message}", e)
        return@withContext "Permission denied. Please allow storage access and try again."
    } catch (e: IOException) {
        Log.e(TAG, "I/O error while saving file: ${e.message}", e)
        return@withContext "An error occurred while saving the file."
    } catch (e: Exception) {
        Log.e(TAG, "Unexpected error: ${e.message}", e)
        return@withContext "Unexpected error occurred. Please try again."
    }
}

/**
 * Copies a link to the exported image to the clipboard for Obsidian
 *
 * @param context The application context
 * @param pageId The ID of the page
 * @param format The format of the file (png, jpg)
 */
private fun copyPageImageLinkForObsidian(context: Context, pageId: String, format: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val textToCopy = """
           [[../attachments/Notable/Pages/notable-page-${pageId}.${format}]]
           [[Notable Link][notable://page-${pageId}]]
       """.trimIndent()
        val clip = ClipData.newPlainText("Notable Page Link", textToCopy)
        clipboard.setPrimaryClip(clip)
    } catch (e: Exception) {
        Log.e(TAG, "Error copying to clipboard: ${e.message}", e)
    }
}