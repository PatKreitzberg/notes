package com.wyldsoft.notes.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wyldsoft.notes.NotesApp

@Composable
fun HomeSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val app = NotesApp.getApp(context)
    var showSyncDialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .padding(16.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.h6,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Google Drive sync settings button
            Button(
                onClick = {
                    showSyncDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync Settings"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Google Drive Sync")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Close button
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Close")
            }
        }
    }

    if (showSyncDialog) {
        SyncSettingsDialog(
            syncManager = app.syncManager,
            onDismiss = { showSyncDialog = false }
        )
    }
}