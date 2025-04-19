// Create a new file HandwritingRecognitionHelper.kt
package com.wyldsoft.notes.utils

import android.content.Context
import com.wyldsoft.notes.classes.HandwritingRecognizer
import com.wyldsoft.notes.classes.SnackConf
import com.wyldsoft.notes.classes.SnackState
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class HandwritingRecognitionHelper {
    companion object {
        fun recognizeSelectedStrokes(state: EditorState, context: Context) {
            if (state.selectedForRecognition.isEmpty()) {
                // Show message if no strokes selected
                GlobalScope.launch {
                    SnackState.globalSnackFlow.emit(
                        SnackConf(
                            text = "No handwriting selected for recognition",
                            duration = 2000
                        )
                    )
                }
                return
            }

            // Show loading indicator
            GlobalScope.launch {
                SnackState.globalSnackFlow.emit(
                    SnackConf(
                        text = "Recognizing handwriting...",
                        duration = 1000
                    )
                )

                try {
                    // Create and initialize recognizer
                    val recognizer = HandwritingRecognizer(context)
                    val initialized = recognizer.initialize()

                    if (!initialized) {
                        SnackState.globalSnackFlow.emit(
                            SnackConf(
                                text = "Failed to initialize handwriting recognizer",
                                duration = 3000
                            )
                        )
                        return@launch
                    }

                    // Recognize the strokes
                    val results = recognizer.recognizeStrokes(state.selectedForRecognition)

                    // Update state with results
                    if (results.isNotEmpty()) {
                        state.recognizedText = results[0] // Top result
                        state.showRecognitionDialog = true
                    } else {
                        SnackState.globalSnackFlow.emit(
                            SnackConf(
                                text = "No text recognized. Try selecting clearer handwriting.",
                                duration = 3000
                            )
                        )
                    }

                    // Clean up
                    recognizer.close()

                } catch (e: Exception) {
                    println("Handwriting recognition error: ${e.message}")
                    e.printStackTrace()

                    SnackState.globalSnackFlow.emit(
                        SnackConf(
                            text = "Recognition error: ${e.message}",
                            duration = 3000
                        )
                    )
                }
            }
        }
    }
}