// app/src/main/java/com/wyldsoft/notes/components/SyncStatusIndicator.kt
package com.wyldsoft.notes.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wyldsoft.notes.sync.SyncState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SyncStatusIndicator(
    syncState: SyncState,
    lastSyncTime: Date?,
    onClick: () -> Unit
) {
    var showTooltip by remember { mutableStateOf(false) }

    // Auto-hide tooltip after a delay
    LaunchedEffect(showTooltip) {
        if (showTooltip) {
            delay(3000)
            showTooltip = false
        }
    }

    Box(
        modifier = Modifier
            .padding(8.dp)
            .wrapContentSize()
    ) {
        // Status indicator
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(getStatusColor(syncState))
                .clickable {
                    showTooltip = !showTooltip
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            when (syncState) {
                SyncState.SYNCING, SyncState.CONNECTING -> {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Syncing",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                SyncState.SUCCESS -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Sync successful",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                SyncState.ERROR -> {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Sync error",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                SyncState.CONFLICT -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Sync conflict",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = "Sync status",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Tooltip
        AnimatedVisibility(
            visible = showTooltip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    .background(Color.DarkGray)
                    .padding(8.dp)
            ) {
                Text(
                    text = when (syncState) {
                        SyncState.IDLE -> "Ready to sync"
                        SyncState.CONNECTING -> "Connecting..."
                        SyncState.SYNCING -> "Syncing..."
                        SyncState.SUCCESS -> "Sync successful"
                        SyncState.ERROR -> "Sync failed"
                        SyncState.CONFLICT -> "Conflict detected"
                    },
                    color = Color.White,
                    fontSize = 12.sp
                )

                if (lastSyncTime != null) {
                    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                    Text(
                        text = "Last sync: ${dateFormat.format(lastSyncTime)}",
                        color = Color.LightGray,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

private fun getStatusColor(syncState: SyncState): Color {
    return when (syncState) {
        SyncState.IDLE -> Color.Gray
        SyncState.CONNECTING -> Color.Blue
        SyncState.SYNCING -> Color.Blue
        SyncState.SUCCESS -> Color.Green
        SyncState.ERROR -> Color.Red
        SyncState.CONFLICT -> Color.Yellow
    }
}