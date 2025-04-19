package com.wyldsoft.notes.handwritingrecognition

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wyldsoft.notes.classes.SnackConf
import com.wyldsoft.notes.classes.SnackState
import com.wyldsoft.notes.utils.EditorState
import kotlinx.coroutines.launch

/**
 * Dialog to display handwriting recognition results.
 *
 * @param state The editor state containing recognition results
 * @param onDismiss Callback for when the dialog is dismissed
 * @param onRetry Callback to retry recognition with the same selection
 */
@Composable
fun RecognitionResultDialog(
    state: EditorState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Only show if we have recognized text
    state.recognizedText?.let { text ->
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Recognition Result") },
            text = {
                Column {
                    Text(
                        text = "Recognized text:",
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = text,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Recognized Text", text)
                                    clipboard.setPrimaryClip(clip)

                                    scope.launch {
                                        SnackState.globalSnackFlow.emit(
                                            SnackConf(
                                                text = "Text copied to clipboard",
                                                duration = 2000
                                            )
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy to clipboard"
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Not correct? You can try again with the same selection or select different strokes.",
                        style = MaterialTheme.typography.caption
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onDismiss
                ) {
                    Text("Close")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onRetry()
                        onDismiss()
                    }
                ) {
                    Text("Try Again")
                }
            }
        )
    }
}

/**
 * Dialog informing the user about handwriting recognition mode.
 *
 * @param onDismiss Callback for when the dialog is dismissed
 * @param onConfirm Callback for when the user confirms to enter recognition mode
 */
@Composable
fun RecognitionInstructionsDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Handwriting Recognition") },
        text = {
            Column {
                Text(
                    text = "Select handwritten content to convert to text:",
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text("1. Use your finger to draw a selection around the text you want to recognize")

                Spacer(modifier = Modifier.height(4.dp))

                Text("2. Once selected, tap 'Recognize' to convert to text")

                Spacer(modifier = Modifier.height(4.dp))

                Text("3. For best results, use clear handwriting and select only a few words at a time")
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm
            ) {
                Text("Start Selecting")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}