package com.wyldsoft.notes.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.wyldsoft.notes.gestures.GestureAction
import com.wyldsoft.notes.gestures.GestureMapping
import com.wyldsoft.notes.gestures.GestureSettingsManager
import com.wyldsoft.notes.gestures.GestureType

/**
 * Screen for configuring gesture mappings.
 *
 * @param navController Navigation controller for screen navigation
 */
@Composable
fun GestureSettingsView(navController: NavController) {
    val context = LocalContext.current
    val gestureSettingsManager = remember { GestureSettingsManager(context) }
    var gestureMappings by remember { mutableStateOf(gestureSettingsManager.getMappings()) }
    var showResetConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gesture Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showResetConfirmation = true }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset to defaults"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "Customize Gestures",
                style = MaterialTheme.typography.h5,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.1f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Important: Gestures work with finger touch only, not with stylus input.",
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "This allows you to draw normally with your stylus while using finger gestures for navigation and tool selection.",
                        style = MaterialTheme.typography.body2
                    )
                }
            }

            Text(
                text = "Assign actions to gestures by selecting from the dropdown menus below.",
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(gestureMappings) { mapping ->
                    GestureMappingItem(
                        mapping = mapping,
                        onActionChanged = { gestureType, action ->
                            // Update in the database
                            gestureSettingsManager.updateMapping(gestureType, action)

                            // Update the local state
                            gestureMappings = gestureMappings.map {
                                if (it.gestureType == gestureType) it.copy(action = action) else it
                            }
                        }
                    )
                }
            }
        }

        // Reset confirmation dialog
        if (showResetConfirmation) {
            AlertDialog(
                onDismissRequest = { showResetConfirmation = false },
                title = { Text("Reset Gesture Settings") },
                text = { Text("Are you sure you want to reset all gesture settings to default?") },
                confirmButton = {
                    Button(
                        onClick = {
                            gestureSettingsManager.resetToDefaults()
                            gestureMappings = gestureSettingsManager.getMappings()
                            showResetConfirmation = false
                        }
                    ) {
                        Text("Reset")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showResetConfirmation = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

/**
 * Item for a single gesture mapping with dropdown for action selection.
 *
 * @param mapping The gesture mapping
 * @param onActionChanged Callback for when the action is changed
 */
@Composable
fun GestureMappingItem(
    mapping: GestureMapping,
    onActionChanged: (GestureType, GestureAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val gestureName = when (mapping.gestureType) {
        GestureType.SINGLE_FINGER_DOUBLE_TAP -> "Single Finger Double Tap"
        GestureType.TWO_FINGER_DOUBLE_TAP -> "Two Finger Double Tap"
        GestureType.THREE_FINGER_DOUBLE_TAP -> "Three Finger Double Tap"
        GestureType.FOUR_FINGER_DOUBLE_TAP -> "Four Finger Double Tap"
        GestureType.SWIPE_UP -> "Swipe Up"
        GestureType.SWIPE_DOWN -> "Swipe Down"
        GestureType.SWIPE_LEFT -> "Swipe Left"
        GestureType.SWIPE_RIGHT -> "Swipe Right"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Gesture name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = gestureName,
                    fontWeight = FontWeight.Bold
                )
            }

            // Action dropdown
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = getActionName(mapping.action),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select Action"
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
                                onActionChanged(mapping.gestureType, action)
                                expanded = false
                            }
                        ) {
                            Text(getActionName(action))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Get a human-readable name for an action.
 *
 * @param action The action
 * @return Human-readable name
 */
fun getActionName(action: GestureAction): String {
    return when (action) {
        GestureAction.UNDO -> "Undo"
        GestureAction.REDO -> "Redo"
        GestureAction.TOGGLE_TOOLBAR -> "Toggle Toolbar"
        GestureAction.CLEAR_PAGE -> "Clear Page"
        GestureAction.NEXT_PAGE -> "Next Page"
        GestureAction.PREVIOUS_PAGE -> "Previous Page"
        GestureAction.CHANGE_PEN_COLOR -> "Change Pen Color"
        GestureAction.TOGGLE_ERASER -> "Toggle Eraser"
        GestureAction.NONE -> "No Action"
    }
}