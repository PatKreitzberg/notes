// app/src/main/java/com/wyldsoft/notes/components/SyncSettingsDialog.kt
package com.wyldsoft.notes.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wyldsoft.notes.sync.SyncFrequency
import com.wyldsoft.notes.sync.SyncManager
import com.wyldsoft.notes.sync.SyncState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SyncSettingsDialog(
    syncManager: SyncManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Collect state
    val syncState by syncManager.syncState.collectAsState()
    val syncProgress by syncManager.syncProgress.collectAsState()
    val errorMessage by syncManager.errorMessage.collectAsState()
    val lastSyncTime by syncManager.lastSyncTime.collectAsState()

    // Get sign-in status
    var isSignedIn by remember { mutableStateOf(syncManager.driveServiceWrapper.isSignedIn()) }

    // Local state for settings
    var syncOnlyOnWifi by remember { mutableStateOf(syncManager.syncOnlyOnWifi) }
    var autoSyncEnabled by remember { mutableStateOf(syncManager.autoSyncEnabled) }
    var syncFrequency by remember { mutableStateOf(syncManager.syncFrequency) }

    // Create launcher for Google Sign-in
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        coroutineScope.launch {
            val success = syncManager.driveServiceWrapper.handleSignInResult(result.data)
            isSignedIn = syncManager.driveServiceWrapper.isSignedIn()
        }
    }

    // Update sign-in state when dialog is shown
    LaunchedEffect(Unit) {
        isSignedIn = syncManager.driveServiceWrapper.isSignedIn()
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "Sync Settings",
                style = MaterialTheme.typography.h6,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Google Sign-in Card
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
                        text = "Google Drive Account",
                        style = MaterialTheme.typography.subtitle1
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isSignedIn) "Signed In" else "Not Signed In",
                        color = if (isSignedIn) Color.Green else Color.Red,
                        style = MaterialTheme.typography.body1
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Sign In/Out Button
                    if (isSignedIn) {
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    syncManager.driveServiceWrapper.signOut()
                                    isSignedIn = syncManager.driveServiceWrapper.isSignedIn()
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
                                syncManager.driveServiceWrapper.signIn(signInLauncher)
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
                }
            }

            // Sync Status
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
                        text = "Sync Status",
                        style = MaterialTheme.typography.subtitle1
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val statusText = when (syncState) {
                        SyncState.IDLE -> "Ready to sync"
                        SyncState.CONNECTING -> "Connecting..."
                        SyncState.SYNCING -> "Syncing..."
                        SyncState.SUCCESS -> "Sync successful"
                        SyncState.ERROR -> "Sync failed"
                        SyncState.CONFLICT -> "Conflict detected"
                    }

                    val statusColor = when (syncState) {
                        SyncState.SUCCESS -> Color.Green
                        SyncState.ERROR -> Color.Red
                        else -> Color.Gray
                    }

                    Text(
                        text = statusText,
                        color = statusColor,
                        style = MaterialTheme.typography.body1
                    )

                    // Last sync time
                    if (lastSyncTime != null) {
                        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                        Text(
                            text = "Last synced: ${dateFormat.format(lastSyncTime)}",
                            style = MaterialTheme.typography.body2,
                            color = Color.Gray
                        )
                    } else {
                        Text(
                            text = "Never synced",
                            style = MaterialTheme.typography.body2,
                            color = Color.Gray
                        )
                    }

                    // Show error message if any
                    if (syncState == SyncState.ERROR && !errorMessage.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage!!,
                            color = Color.Red,
                            style = MaterialTheme.typography.caption
                        )
                    }

                    // Show progress if syncing
                    if (syncState == SyncState.SYNCING || syncState == SyncState.CONNECTING) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = syncProgress,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Sync Settings
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
                        text = "Settings",
                        style = MaterialTheme.typography.subtitle1
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Auto-sync toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Auto-sync",
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = autoSyncEnabled,
                            onCheckedChange = { autoSyncEnabled = it }
                        )
                    }

                    // WiFi-only toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Sync only on Wi-Fi",
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = syncOnlyOnWifi,
                            onCheckedChange = { syncOnlyOnWifi = it }
                        )
                    }

                    // Sync frequency
                    if (autoSyncEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Sync Frequency")

                        // Radio buttons for sync frequency
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = syncFrequency == SyncFrequency.REALTIME,
                                onClick = { syncFrequency = SyncFrequency.REALTIME }
                            )
                            Text(
                                text = "Real-time (5 min)",
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = syncFrequency == SyncFrequency.HOURLY,
                                onClick = { syncFrequency = SyncFrequency.HOURLY }
                            )
                            Text(
                                text = "Hourly",
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = syncFrequency == SyncFrequency.DAILY,
                                onClick = { syncFrequency = SyncFrequency.DAILY }
                            )
                            Text(
                                text = "Daily",
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }

            // Manual sync button
            Button(
                onClick = {
                    coroutineScope.launch {
                        syncManager.performSync()
                    }
                },
                enabled = isSignedIn && (syncState == SyncState.IDLE || syncState == SyncState.SUCCESS || syncState == SyncState.ERROR),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Sync Now"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sync Now")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Button row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss
                ) {
                    Text("Cancel")
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        // Save settings
                        syncManager.updateSyncSettings(
                            syncOnlyOnWifi,
                            autoSyncEnabled,
                            syncFrequency
                        )
                        onDismiss()
                    }
                ) {
                    Text("Save")
                }
            }
        }
    }
}