// app/src/main/java/com/wyldsoft/notes/components/BackupSettingsDialog.kt
package com.wyldsoft.notes.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wyldsoft.notes.backup.rememberBackupManager
import com.wyldsoft.notes.services.GoogleDriveBackupService
import kotlinx.coroutines.launch

@Composable
fun BackupSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val backupManager = rememberBackupManager(context)

    // Observe backup state and error message
    val backupState by backupManager.backupState.collectAsState()
    val errorMessage by backupManager.errorMessage.collectAsState()

    // Check if the user is signed in
    var isSignedIn by remember { mutableStateOf(backupManager.isSignedIn()) }

    // Create launcher for Google Sign-in
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        backupManager.handleSignInResult(result.data)
        isSignedIn = backupManager.isSignedIn()
    }

    // Update sign-in state when the dialog is shown
    LaunchedEffect(Unit) {
        isSignedIn = backupManager.isSignedIn()
    }

    Dialog(onDismissRequest = {
        backupManager.resetState()
        onDismiss()
    }) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "Backup Settings",
                style = MaterialTheme.typography.h6,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Status Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Google Drive Backup",
                        style = MaterialTheme.typography.subtitle1
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status:",
                            style = MaterialTheme.typography.body2
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = if (isSignedIn) "Signed In" else "Not Signed In",
                            color = if (isSignedIn) Color.Green else Color.Red,
                            style = MaterialTheme.typography.body2
                        )
                    }

                    // Show backup state if not idle
                    if (backupState != GoogleDriveBackupService.BackupState.IDLE) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Backup Status: ${backupState.name.lowercase().capitalize()}",
                            style = MaterialTheme.typography.body2
                        )

                        if (backupState == GoogleDriveBackupService.BackupState.ERROR && !errorMessage.isNullOrEmpty()) {
                            Text(
                                text = errorMessage ?: "",
                                color = Color.Red,
                                style = MaterialTheme.typography.caption
                            )
                        }
                    }
                }
            }

            // Sign In/Out Button
            if (isSignedIn) {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            backupManager.signOut()
                            isSignedIn = backupManager.isSignedIn()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = "Sign Out"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out of Google Drive")
                }
            } else {
                Button(
                    onClick = {
                        backupManager.signIn(context as androidx.activity.ComponentActivity, signInLauncher)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Login,
                        contentDescription = "Sign In"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign In to Google Drive")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Backup Button (enabled only if signed in)
            Button(
                onClick = {
                    coroutineScope.launch {
                        backupManager.performBackup()
                    }
                },
                enabled = isSignedIn && backupState != GoogleDriveBackupService.BackupState.UPLOADING,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = "Backup Now"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Backup Now")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress indicator during operations
            if (backupState == GoogleDriveBackupService.BackupState.AUTHENTICATING ||
                backupState == GoogleDriveBackupService.BackupState.PREPARING ||
                backupState == GoogleDriveBackupService.BackupState.UPLOADING) {

                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when(backupState) {
                        GoogleDriveBackupService.BackupState.AUTHENTICATING -> "Authenticating..."
                        GoogleDriveBackupService.BackupState.PREPARING -> "Preparing backup..."
                        GoogleDriveBackupService.BackupState.UPLOADING -> "Uploading to Google Drive..."
                        else -> ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.caption
                )
            }

            // Success message
            if (backupState == GoogleDriveBackupService.BackupState.SUCCESS) {
                Text(
                    text = "Backup completed successfully!",
                    color = Color.Green,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.body2
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Close button
            TextButton(
                onClick = {
                    backupManager.resetState()
                    onDismiss()
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Close")
            }
        }
    }
}