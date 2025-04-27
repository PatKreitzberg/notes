// app/src/main/java/com/wyldsoft/notes/components/SettingsButton.kt
package com.wyldsoft.notes.components

import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wyldsoft.notes.utils.noRippleClickable

@Composable
fun SettingsButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Icon(
        imageVector = Icons.Default.Settings,
        contentDescription = "Settings",
        modifier = modifier.noRippleClickable(onClick)
    )
}