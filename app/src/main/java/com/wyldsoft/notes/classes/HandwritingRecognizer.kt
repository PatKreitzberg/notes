package com.wyldsoft.notes.classes

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.MlKitException
// Import necessary classes for model management
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import com.wyldsoft.notes.utils.Stroke
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine


class HandwritingRecognizer(
    private val appContext: Context
) {
    private var recognizer: DigitalInkRecognizer? = null
    private var modelIdentifier: DigitalInkRecognitionModelIdentifier? = null
    private var isInitialized = false

    companion object {
        private const val TAG = "HandwritingRecognizer"
    }

    /**
     * Initializes the recognizer and downloads the model if needed.
     * Must be called before recognizeStrokes.
     * @param languageCode BCP 47 language tag (e.g., "en-US", "fr-FR").
     * @return True if initialization and model download (if needed) were successful, false otherwise.
     */
    suspend fun initialize(languageCode: String = "en-US"): Boolean {
        if (isInitialized) {
            Log.d(TAG, "Recognizer already initialized.")
            if (modelIdentifier?.languageTag == languageCode) {
                return true
            } else {
                Log.w(TAG, "Re-initializing with a different language: $languageCode")
                closeRecognizerInternal()
            }
        }

        try {
            // Step 1: Get the Model Identifier
            val identifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageCode)
            if (identifier == null) {
                Log.e(TAG, "Invalid language tag: $languageCode")
                isInitialized = false
                return false
            }
            this.modelIdentifier = identifier

            // Step 2: Create the Model from the Identifier
            val model = DigitalInkRecognitionModel.builder(identifier).build()

            // Step 3: Get RemoteModelManager and ensure model is downloaded
            val remoteModelManager = RemoteModelManager.getInstance()

            // Use withContext for the download operation which can involve I/O
            val downloadSuccessful = withContext(Dispatchers.IO) {
                try {
                    // Check if model is already downloaded (optional but good practice)
                    val isDownloaded = Tasks.await(remoteModelManager.isModelDownloaded(model)) // Synchronous check inside IO dispatcher
                    if (isDownloaded) {
                        Log.d(TAG,"Model for $languageCode already downloaded.")
                        true // Already downloaded, proceed
                    } else {
                        Log.d(TAG, "Model for $languageCode not downloaded. Starting download...")
                        // DownloadConditions can be customized (e.g., require Wi-Fi)
                        val downloadConditions = DownloadConditions.Builder()
                            // .requireWifi() // Uncomment if you want Wi-Fi only downloads
                            .build()
                        // Await the download Task. This handles downloading if needed.
                        remoteModelManager.download(model, downloadConditions).await()
                        Log.i(TAG, "Model for $languageCode downloaded successfully.")
                        true // Download successful
                    }
                } catch (e: MlKitException) {
                    Log.e(TAG, "Failed to download or check model status for $languageCode: ${e.message}", e)
                    // Handle specific errors like NO_SPACE, METERED_NETWORK_UNALLOWED etc. if needed
                    false // Indicate download/check failure
                } catch (e: Exception) { // Catch other potential exceptions like InterruptedException from await
                    Log.e(TAG, "An unexpected error occurred during model download/check for $languageCode: ${e.message}", e)
                    false // Indicate failure
                }
            }

            if (!downloadSuccessful) {
                Log.e(TAG, "Model setup failed for $languageCode.")
                isInitialized = false
                return false // Exit if model download/check failed
            }

            // Step 4: Create Recognizer Options (using the model, now that we know it's downloaded)
            val options = DigitalInkRecognizerOptions.builder(model).build()

            // Step 5: Get the Recognizer instance
            // This should be done on the main thread or a thread where ML Kit expects it
            // Getting the client itself is usually quick.
            recognizer = DigitalInkRecognition.getClient(options)
            Log.d(TAG, "Recognizer client created for language: $languageCode")

            isInitialized = true
            Log.i(TAG, "HandwritingRecognizer initialized successfully for $languageCode.")
            return true // Initialization successful

        } catch (e: Exception) {
            Log.e(TAG, "Error during recognizer initialization process: ${e.message}", e)
            isInitialized = false
            closeRecognizerInternal() // Clean up if initialization failed midway
            return false
        }
    }


    // --- recognizeStrokes and close methods remain the same as the previous version ---

    suspend fun recognizeStrokes(strokes: List<Stroke>): List<String> {
        if (!isInitialized || recognizer == null) {
            throw IllegalStateException("Recognizer not initialized. Call initialize() first.")
        }
        if (strokes.isEmpty()) {
            Log.d(TAG, "No strokes provided for recognition.")
            return emptyList()
        }

        val inkBuilder = Ink.builder()

        // Convert app's strokes to ML Kit's Ink format
        strokes.forEach { appStroke ->
            val strokeBuilder = Ink.Stroke.builder()
            if (appStroke.points.isNotEmpty()) {
                appStroke.points.forEach { point ->
                    // *** THE FIX IS HERE ***
                    // Use Ink.Point.create instead of Ink.Point.builder()
                    strokeBuilder.addPoint(
                        Ink.Point.create(point.x, point.y, point.timestamp)
                    )
                }
                // Only add the stroke if it has points after processing
                if(strokeBuilder.build().points.isNotEmpty()){ // Check if points were actually added
                    inkBuilder.addStroke(strokeBuilder.build())
                } else {
                    Log.w(TAG,"Stroke skipped because it resulted in zero points after conversion.")
                }
            } else {
                Log.w(TAG, "Skipping empty stroke (no points initially).")
            }
        }

        val ink = inkBuilder.build()
        if (ink.strokes.isEmpty()) {
            Log.w(TAG, "Ink object contains no valid strokes after conversion.")
            return emptyList()
        }

        // Perform recognition using suspendCancellableCoroutine for Task integration
        return try {
            suspendCancellableCoroutine { continuation ->
                recognizer!!.recognize(ink)
                    .addOnSuccessListener { result ->
                        val candidates = result.candidates.map { it.text }
                        if (candidates.isNotEmpty()) {
                            Log.d(TAG, "Recognition successful. Best candidate: ${candidates[0]}")
                        } else {
                            Log.d(TAG, "Recognition successful but no candidates found.")
                        }
                        if (continuation.isActive) {
                            continuation.resume(candidates)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Recognition failed: ${e.message}", e)
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }

                continuation.invokeOnCancellation {
                    Log.d(TAG, "Recognition coroutine cancelled.")
                }
            }
        } catch (e: CancellationException) {
            Log.w(TAG, "Recognition job was cancelled.")
            throw e // Re-throw cancellation exceptions
        } catch (e: Exception) {
            Log.e(TAG, "Error during recognition suspendCancellableCoroutine: ${e.message}", e)
            emptyList() // Return empty list on other errors during the coroutine bridge
        }
    }


    fun close() {
        closeRecognizerInternal()
        Log.d(TAG, "HandwritingRecognizer closed.")
    }


    private fun closeRecognizerInternal() {
        recognizer?.close()
        recognizer = null
        isInitialized = false
        modelIdentifier = null
        Log.d(TAG,"Internal recognizer resources released.")
    }

}