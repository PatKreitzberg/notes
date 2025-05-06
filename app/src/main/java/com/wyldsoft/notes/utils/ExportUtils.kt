// app/src/main/java/com/wyldsoft/notes/utils/ExportUtils.kt
package com.wyldsoft.notes.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.wyldsoft.notes.views.PageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream

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
        // First create the bitmap for the page
        val bitmap = drawPageBitmap(context, pageId)

        // Save the PDF using the bitmap
        val result = saveFile(context, "Notes-page-${pageId}", "pdf") { outputStream ->
            // Create a PDF document
            val document = PdfDocument()

            // Create a page with the same dimensions as the bitmap
            val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, 1).create()
            println("bitmap width height ${bitmap.width} ${bitmap.height}")
            val page = document.startPage(pageInfo)

            // Draw the bitmap onto the page
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)

            // Finish the page
            document.finishPage(page)

            // Write the PDF document to the output stream
            document.writeTo(outputStream)
            document.close()
        }

        // Recycle the bitmap to free memory
        bitmap.recycle()

        return@withContext result
    } catch (e: Exception) {
        Log.e(TAG, "Error exporting PDF: ${e.message}", e)
        return@withContext "Error creating PDF: ${e.message}"
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
        val result = saveFile(context, "notable-page-${pageId}", "png") { outputStream ->
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
        val result = saveFile(context, "notable-page-${pageId}", "jpg") { outputStream ->
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
                Environment.DIRECTORY_DOCUMENTS + "/Notable/" + dictionary
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