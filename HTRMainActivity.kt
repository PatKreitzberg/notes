package com.example.handwritingtotext

import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.common.MlKitException
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var drawingView: DrawingView
    private lateinit var recognizeButton: Button
    private lateinit var clearButton: Button
    private lateinit var resultTextView: TextView

    private var inkBuilder = Ink.builder()
    private lateinit var ink: Ink

    private var recognizer: DigitalInkRecognizer? = null
    private lateinit var model: DigitalInkRecognitionModel
    private var modelDownloaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI components
        drawingView = findViewById(R.id.drawing_view)
        recognizeButton = findViewById(R.id.recognize_button)
        clearButton = findViewById(R.id.clear_button)
        resultTextView = findViewById(R.id.result_text_view)

        // Set up the drawing view to capture ink
        drawingView.setInkBuilder(inkBuilder)

        // Set up model
        setupRecognizer()

        // Set button click listeners
        recognizeButton.setOnClickListener {
            if (modelDownloaded) {
                recognizeText()
            } else {
                showToast("Model still downloading. Please wait.")
            }
        }

        clearButton.setOnClickListener {
            drawingView.clear()
            resultTextView.text = ""
            // Reset ink builder
            inkBuilder = Ink.builder()
            drawingView.setInkBuilder(inkBuilder)
        }
    }

    private fun setupRecognizer() {
        // Create a recognizer for English language
        try {
            val modelIdentifier =
                DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")

            if (modelIdentifier == null) {
                showToast("Model not found for language")
                return
            }

            // Create model
            model = DigitalInkRecognitionModel.builder(modelIdentifier).build()

            // Download the model
            val remoteModelManager = RemoteModelManager.getInstance()

            // Check if model is already downloaded
            remoteModelManager.isModelDownloaded(model)
                .addOnSuccessListener { isDownloaded ->
                    if (isDownloaded) {
                        Log.d(TAG, "Model already downloaded")
                        modelDownloaded = true
                        createRecognizer()
                    } else {
                        downloadModel()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error checking if model is downloaded", e)
                    showToast("Error checking model download status")
                }

        } catch (e: MlKitException) {
            Log.e(TAG, "Error setting up model", e)
            showToast("Error setting up model: ${e.message}")
        }
    }

    private fun downloadModel() {
        val remoteModelManager = RemoteModelManager.getInstance()

        remoteModelManager.download(model, DownloadConditions.Builder().build())
            .addOnSuccessListener {
                Log.d(TAG, "Model download successful")
                showToast("Model downloaded successfully")
                modelDownloaded = true
                createRecognizer()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error downloading model", e)
                showToast("Error downloading model: ${e.message}")
            }
    }

    private fun createRecognizer() {
        recognizer = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(model).build())
    }

    private fun recognizeText() {
        // Get the ink object
        ink = inkBuilder.build()

        if (ink.strokes.isEmpty()) {
            showToast("Please write something first")
            return
        }

        // Recognize the ink
        recognizer?.recognize(ink)
            ?.addOnSuccessListener { result ->
                if (result.candidates.isEmpty()) {
                    resultTextView.text = "No text recognized"
                    return@addOnSuccessListener
                }

                // Display the top recognition result
                val recognizedText = result.candidates[0].text
                resultTextView.text = recognizedText
            }
            ?.addOnFailureListener { e ->
                Log.e(TAG, "Error during recognition", e)
                showToast("Recognition failed: ${e.message}")
            }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizer?.close()
    }
}