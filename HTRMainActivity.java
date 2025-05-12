package com.example.handwritingtotext;


import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.common.MlKitException;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.vision.digitalink.DigitalInkRecognition;
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel;
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier;
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer;
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions;
import com.google.mlkit.vision.digitalink.Ink;
import com.google.mlkit.vision.digitalink.RecognitionResult;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private DrawingView drawingView;
    private Button recognizeButton;
    private Button clearButton;
    private TextView resultTextView;

    private Ink.Builder inkBuilder = Ink.builder();
    private Ink ink;

    private DigitalInkRecognizer recognizer;
    private DigitalInkRecognitionModel model;
    private boolean modelDownloaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        drawingView = findViewById(R.id.drawing_view);
        recognizeButton = findViewById(R.id.recognize_button);
        clearButton = findViewById(R.id.clear_button);
        resultTextView = findViewById(R.id.result_text_view);

        // Set up the drawing view to capture ink
        drawingView.setInkBuilder(inkBuilder);

        // Set up model
        setupRecognizer();

        // Set button click listeners
        recognizeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (modelDownloaded) {
                    recognizeText();
                } else {
                    Toast.makeText(MainActivity.this, "Model still downloading. Please wait.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawingView.clear();
                resultTextView.setText("");
                // Reset ink builder
                inkBuilder = Ink.builder();
                drawingView.setInkBuilder(inkBuilder);
            }
        });
    }

    private void setupRecognizer() {
        // Create a recognizer for english language
        try {
            DigitalInkRecognitionModelIdentifier modelIdentifier =
                    DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US");

            if (modelIdentifier == null) {
                showToast("Model not found for language");
                return;
            }

            // Create model
            model = DigitalInkRecognitionModel.builder(modelIdentifier).build();

            // Download the model
            RemoteModelManager remoteModelManager = RemoteModelManager.getInstance();

            // Check if model is already downloaded
            remoteModelManager.isModelDownloaded(model)
                    .addOnSuccessListener(isDownloaded -> {
                        if (isDownloaded) {
                            Log.d(TAG, "Model already downloaded");
                            modelDownloaded = true;
                            createRecognizer();
                        } else {
                            downloadModel();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error checking if model is downloaded", e);
                        showToast("Error checking model download status");
                    });

        } catch (MlKitException e) {
            Log.e(TAG, "Error setting up model", e);
            showToast("Error setting up model: " + e.getMessage());
        }
    }

    private void downloadModel() {
        RemoteModelManager remoteModelManager = RemoteModelManager.getInstance();

        remoteModelManager.download(model, new DownloadConditions.Builder().build())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Model download successful");
                    showToast("Model downloaded successfully");
                    modelDownloaded = true;
                    createRecognizer();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error downloading model", e);
                    showToast("Error downloading model: " + e.getMessage());
                });
    }

    private void createRecognizer() {
        recognizer = DigitalInkRecognition.getClient(
                DigitalInkRecognizerOptions.builder(model).build());
    }

    private void recognizeText() {
        // Get the ink object
        ink = inkBuilder.build();

        if (ink.getStrokes().isEmpty()) {
            showToast("Please write something first");
            return;
        }

        // Recognize the ink
        recognizer.recognize(ink)
                .addOnSuccessListener(new OnSuccessListener<RecognitionResult>() {
                    @Override
                    public void onSuccess(RecognitionResult result) {
                        if (result.getCandidates().isEmpty()) {
                            resultTextView.setText("No text recognized");
                            return;
                        }

                        // Display the top recognition result
                        String recognizedText = result.getCandidates().get(0).getText();
                        resultTextView.setText(recognizedText);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "Error during recognition", e);
                        showToast("Recognition failed: " + e.getMessage());
                    }
                });
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (recognizer != null) {
            recognizer.close();
        }
    }
}

