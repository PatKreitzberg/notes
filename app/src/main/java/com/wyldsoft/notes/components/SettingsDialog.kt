// app/src/main/java/com/wyldsoft/notes/components/SettingsDialog.kt
package com.wyldsoft.notes.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wyldsoft.notes.settings.PaperSize
import com.wyldsoft.notes.settings.SettingsModel
import com.wyldsoft.notes.settings.SettingsRepository
import com.wyldsoft.notes.settings.TemplateType
import kotlinx.coroutines.launch

@Composable
fun SettingsDialog(
    settingsRepository: SettingsRepository,
    onUpdateViewportTransformer: (Boolean) -> Unit,
    onUpdatePageDimensions: (PaperSize) -> Unit,
    onUpdateTemplate: (TemplateType) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val settings = settingsRepository.getSettings()

    var isPaginationEnabled by remember { mutableStateOf(settings.isPaginationEnabled) }
    var paperSize by remember { mutableStateOf(settings.paperSize) }
    var template by remember { mutableStateOf(settings.template) }

    var paperSizeExpanded by remember { mutableStateOf(false) }
    var templateExpanded by remember { mutableStateOf(false) }

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

            // Pagination Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Enable Pagination",
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = isPaginationEnabled,
                    onCheckedChange = { checked ->
                        isPaginationEnabled = checked
                        coroutineScope.launch {
                            settingsRepository.updatePagination(checked)
                            onUpdateViewportTransformer(checked)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Paper Size Dropdown
            Text(text = "Paper Size")
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                    .clickable { paperSizeExpanded = true }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = when (paperSize) {
                    PaperSize.LETTER -> "Letter (8.5\" x 11\")"
                    PaperSize.A4 -> "A4 (210mm x 297mm)"
                })

                DropdownMenu(
                    expanded = paperSizeExpanded,
                    onDismissRequest = { paperSizeExpanded = false }
                ) {
                    DropdownMenuItem(onClick = {
                        paperSize = PaperSize.LETTER
                        paperSizeExpanded = false
                        coroutineScope.launch {
                            settingsRepository.updatePaperSize(PaperSize.LETTER)
                            onUpdatePageDimensions(PaperSize.LETTER)
                        }
                    }) {
                        Text("Letter (8.5\" x 11\")")
                    }

                    DropdownMenuItem(onClick = {
                        paperSize = PaperSize.A4
                        paperSizeExpanded = false
                        coroutineScope.launch {
                            settingsRepository.updatePaperSize(PaperSize.A4)
                            onUpdatePageDimensions(PaperSize.A4)
                        }
                    }) {
                        Text("A4 (210mm x 297mm)")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Template Dropdown
            Text(text = "Template")
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                    .clickable { templateExpanded = true }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = when (template) {
                    TemplateType.BLANK -> "Blank"
                    TemplateType.GRID -> "Grid"
                    TemplateType.RULED -> "Ruled Lines"
                })

                DropdownMenu(
                    expanded = templateExpanded,
                    onDismissRequest = { templateExpanded = false }
                ) {
                    DropdownMenuItem(onClick = {
                        template = TemplateType.BLANK
                        templateExpanded = false
                        coroutineScope.launch {
                            settingsRepository.updateTemplate(TemplateType.BLANK)
                            onUpdateTemplate(TemplateType.BLANK)
                        }
                    }) {
                        Text("Blank")
                    }

                    DropdownMenuItem(onClick = {
                        template = TemplateType.GRID
                        templateExpanded = false
                        coroutineScope.launch {
                            settingsRepository.updateTemplate(TemplateType.GRID)
                            onUpdateTemplate(TemplateType.GRID)
                        }
                    }) {
                        Text("Grid")
                    }

                    DropdownMenuItem(onClick = {
                        template = TemplateType.RULED
                        templateExpanded = false
                        coroutineScope.launch {
                            settingsRepository.updateTemplate(TemplateType.RULED)
                            onUpdateTemplate(TemplateType.RULED)
                        }
                    }) {
                        Text("Ruled Lines")
                    }
                }
            }
        }
    }
}