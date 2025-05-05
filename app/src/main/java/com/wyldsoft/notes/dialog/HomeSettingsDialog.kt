package com.wyldsoft.notes.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.wyldsoft.notes.gesture.GestureAction
import com.wyldsoft.notes.gesture.GestureType
import com.wyldsoft.notes.gesture.GestureHandler
import com.wyldsoft.notes.gesture.GestureMapping
import kotlinx.coroutines.launch

@Composable
fun HomeSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val app = NotesApp.getApp(context)
    var showSyncDialog by remember { mutableStateOf(false) }
    var showGestureDialog by remember { mutableStateOf(false) }  // Add this

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

            Spacer(modifier = Modifier.height(8.dp))

            // Gesture settings button
            Button(
                onClick = {
                    showGestureDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = "Gesture Settings"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gesture Settings")
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

    if (showGestureDialog) {
        GestureSettingsDialog(
            onDismiss = { showGestureDialog = false }
        )
    }
}

// Create a new composable for the gesture settings dialog
@Composable
fun GestureSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Create a temporary gesture handler to access the mappings
    // In a real app, you'd get this from a singleton or dependency injection
    val gestureHandler = remember { GestureHandler(context, scope) }

    // Create a mutable state of the mappings
    val gestureMappings = remember {
        mutableStateMapOf<GestureType, GestureAction>().apply {
            putAll(gestureHandler.getGestureMappings())
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f) // Take up 90% of screen height for scrolling
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .padding(16.dp)
        ) {
            Text(
                text = "Gesture Settings",
                style = MaterialTheme.typography.h6,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Show a scrollable list of gesture mappings
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(GestureType.values()) { gestureType ->
                    GestureMappingItem(
                        gestureType = gestureType,
                        selectedAction = gestureMappings[gestureType] ?: GestureAction.NONE,
                        onActionSelected = { action ->
                            gestureMappings[gestureType] = action
                        }
                    )

                    Divider(
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            // Save and cancel buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
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
                        // Save the gesture mappings
                        scope.launch {
                            gestureMappings.forEach { (gesture, action) ->
                                gestureHandler.setGestureAction(gesture, action)
                            }
                        }
                        onDismiss()
                    }
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
fun GestureMappingItem(
    gestureType: GestureType,
    selectedAction: GestureAction,
    onActionSelected: (GestureAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = gestureType.displayName,
            style = MaterialTheme.typography.subtitle1
        )

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = selectedAction.displayName,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                GestureAction.values().forEach { action ->
                    DropdownMenuItem(
                        onClick = {
                            onActionSelected(action)
                            expanded = false
                        }
                    ) {
                        Text(action.displayName)
                    }
                }
            }
        }
    }
}