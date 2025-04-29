// app/src/main/java/com/wyldsoft/notes/components/ConflictResolutionDialog.kt (continued)
package com.wyldsoft.notes.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wyldsoft.notes.sync.NoteConflict
import com.wyldsoft.notes.sync.Resolution
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ConflictResolutionDialog(
    conflict: NoteConflict,
    onResolution: (Resolution) -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "Sync Conflict",
                style = MaterialTheme.typography.h6,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "The note \"${conflict.localNote.title}\" has been modified both locally and remotely.",
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Show note details
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Local version details
                    Text(
                        text = "Local Version",
                        style = MaterialTheme.typography.subtitle1,
                        color = Color.Blue
                    )

                    Text(
                        text = "Modified: ${dateFormat.format(conflict.localNote.updatedAt)}",
                        style = MaterialTheme.typography.body2
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Remote version details
                    Text(
                        text = "Remote Version",
                        style = MaterialTheme.typography.subtitle1,
                        color = Color.Green
                    )

                    Text(
                        text = "Modified: ${dateFormat.format(conflict.remoteNote.updatedAt)}",
                        style = MaterialTheme.typography.body2
                    )
                }
            }

            // Resolution options
            Text(
                text = "Choose how to resolve this conflict:",
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Button(
                onClick = { onResolution(Resolution.UseLocal) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text("Keep Local Version")
            }

            Button(
                onClick = { onResolution(Resolution.UseRemote) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text("Use Remote Version")
            }

            Button(
                onClick = { onResolution(Resolution.KeepBoth) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text("Keep Both Versions")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Cancel")
            }
        }
    }
}