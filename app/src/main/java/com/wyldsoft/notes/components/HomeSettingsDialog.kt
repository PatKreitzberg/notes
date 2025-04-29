// app/src/main/java/com/wyldsoft/notes/components/HomeSettingsDialog.kt
package com.wyldsoft.notes.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wyldsoft.notes.backup.rememberBackupManager

@Composable
fun HomeSettingsDialog(
    onDismiss: () -> Unit
) {
    var showBackupDialog by remember { mutableStateOf(false) }

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

            // Backup settings button
            Button(
                onClick = {
                    showBackupDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Backup,
                        contentDescription = "Backup Settings"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Backup and Restore")
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

    if (showBackupDialog) {
        BackupSettingsDialog(
            onDismiss = { showBackupDialog = false }
        )
    }
}