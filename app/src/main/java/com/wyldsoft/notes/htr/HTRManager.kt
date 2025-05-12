// app/src/main/java/com/wyldsoft/notes/htr/HTRManager.kt
package com.wyldsoft.notes.htr

import android.content.Context
import android.util.Log
import com.google.mlkit.common.MlKitException
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import com.wyldsoft.notes.utils.Stroke
import com.wyldsoft.notes.views.PageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class HTRManager(private val context: Context, private val coroutineScope: CoroutineScope) {
    companion object {
        private const val TAG = "HTRManager"
        private const val LANGUAGE_TAG = "en-US"
    }

    private val _isModelDownloaded = MutableStateFlow(false)
    val isModelDownloaded: StateFlow<Boolean> = _isModelDownloaded

    private var recognizer: DigitalInkRecognizer? = null
    private var model: DigitalInkRecognitionModel? = null

    init {
        setupRecognizer()
    }

    private fun setupRecognizer() {
        coroutineScope.launch {
            try {
                val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(LANGUAGE_TAG)
                    ?: throw MlKitException("Model not found for language", MlKitException.UNAVAILABLE)

                // Create model
                model = DigitalInkRecognitionModel.builder(modelIdentifier).build()

                // Check if model is already downloaded
                val remoteModelManager = RemoteModelManager.getInstance()
                val isDownloaded = try {
                    remoteModelManager.isModelDownloaded(model!!).await()
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking if model is downloaded", e)
                    false
                }

                if (isDownloaded) {
                    Log.d(TAG, "Model already downloaded")
                    _isModelDownloaded.value = true
                    createRecognizer()
                } else {
                    downloadModel()
                }
            } catch (e: MlKitException) {
                Log.e(TAG, "Error setting up model", e)
            }
        }
    }

    private suspend fun downloadModel() {
        withContext(Dispatchers.IO) {
            try {
                val remoteModelManager = RemoteModelManager.getInstance()
                remoteModelManager.download(model!!, DownloadConditions.Builder().build()).await()
                Log.d(TAG, "Model download successful")
                _isModelDownloaded.value = true
                createRecognizer()
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading model", e)
            }
        }
    }

    private fun createRecognizer() {
        if (model != null) {
            recognizer = DigitalInkRecognition.getClient(
                DigitalInkRecognizerOptions.builder(model!!).build()
            )
        }
    }

    suspend fun recognizePageStrokes(page: PageView): String {
        if (!_isModelDownloaded.value || recognizer == null) {
            return "Text recognition model not ready. Please try again later."
        }

        val inkBuilder = Ink.builder()

        // Convert PageView strokes to ML Kit Ink format
        for (stroke in page.strokes) {
            val strokeBuilder = Ink.Stroke.builder()

            for (point in stroke.points) {
                // Convert each point to ML Kit format
                strokeBuilder.addPoint(Ink.Point.create(point.x, point.y, point.timestamp))
            }

            inkBuilder.addStroke(strokeBuilder.build())
        }

        val ink = inkBuilder.build()

        if (ink.strokes.isEmpty()) {
            return "No handwriting found to recognize"
        }

        return withContext(Dispatchers.IO) {
            try {
                val result = recognizer!!.recognize(ink).await()
                if (result.candidates.isEmpty()) {
                    "No text recognized"
                } else {
                    // Return the top recognition result
                    result.candidates[0].text
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during recognition", e)
                "Recognition failed: ${e.message}"
            }
        }
    }

    fun cleanup() {
        recognizer?.close()
    }
}