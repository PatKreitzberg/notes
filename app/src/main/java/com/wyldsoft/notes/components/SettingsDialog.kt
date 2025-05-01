// app/src/main/java/com/wyldsoft/notes/components/SettingsDialog.kt
package com.wyldsoft.notes.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Book
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wyldsoft.notes.NotesApp
import com.wyldsoft.notes.database.entity.NotebookEntity
import com.wyldsoft.notes.settings.PaperSize
import com.wyldsoft.notes.settings.SettingsModel
import com.wyldsoft.notes.settings.SettingsRepository
import com.wyldsoft.notes.settings.TemplateType
import kotlinx.coroutines.launch

@Composable
fun SettingsDialog(
    settingsRepository: SettingsRepository,
    currentNoteName: String,
    currentNoteId: String, // Add note ID
    onUpdateViewportTransformer: (Boolean) -> Unit,
    onUpdatePageDimensions: (PaperSize) -> Unit,
    onUpdateTemplate: (TemplateType) -> Unit,
    onUpdateNoteName: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val app = NotesApp.getApp(context) // Get application context
    val coroutineScope = rememberCoroutineScope()
    val settings = settingsRepository.getSettings()

    var isPaginationEnabled by remember { mutableStateOf(settings.isPaginationEnabled) }
    var paperSize by remember { mutableStateOf(settings.paperSize) }
    var template by remember { mutableStateOf(settings.template) }
    var noteName by remember { mutableStateOf(currentNoteName) }

    var paperSizeExpanded by remember { mutableStateOf(false) }
    var templateExpanded by remember { mutableStateOf(false) }

    // Add notebook management state
    var showNotebookSection by remember { mutableStateOf(false) }

    // Get notebooks data
    val notebookRepository = app.notebookRepository
    val pageNotebookRepository = app.pageNotebookRepository

    val notebooks by notebookRepository.getAllNotebooks().collectAsState(initial = emptyList())
    val notebooksContainingPage = pageNotebookRepository.getNotebooksContainingPage(currentNoteId)
        .collectAsState(initial = emptyList())

    // Track selected notebook IDs
    val selectedNotebookIds = remember {
        mutableStateListOf<String>().apply {
            // Initialize with current notebooks
            notebooksContainingPage.value.forEach { notebook ->
                add(notebook.id)
            }
        }
    }

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

            // Note Name Field
            Text(text = "Note Name")
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = noteName,
                onValueChange = { noteName = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

            // Notebooks Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showNotebookSection = !showNotebookSection }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Book,
                    contentDescription = "Notebooks",
                    tint = MaterialTheme.colors.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Manage Notebooks",
                    style = MaterialTheme.typography.subtitle1,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (showNotebookSection)
                        Icons.Default.Backup // Using as a "collapse" icon
                    else
                        Icons.Default.Backup, // Using as an "expand" icon
                    contentDescription = if (showNotebookSection) "Collapse" else "Expand",
                    tint = MaterialTheme.colors.primary
                )
            }

            // Notebook selection section
            if (showNotebookSection) {
                Spacer(modifier = Modifier.height(8.dp))
                if (notebooks.isEmpty()) {
                    Text(
                        text = "No notebooks available. Create notebooks in the home screen first.",
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp) // Fixed height for notebook list
                            .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        items(notebooks) { notebook ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedNotebookIds.contains(notebook.id),
                                    onCheckedChange = { isChecked ->
                                        if (isChecked) {
                                            selectedNotebookIds.add(notebook.id)
                                        } else {
                                            selectedNotebookIds.remove(notebook.id)
                                        }
                                    }
                                )

                                Text(
                                    text = notebook.title,
                                    style = MaterialTheme.typography.body1,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Button row at the bottom
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
                        // Save note name if it has changed and is not empty
                        if (noteName.isNotBlank() && noteName != currentNoteName) {
                            onUpdateNoteName(noteName)
                        }

                        // Save notebook selections
                        if (showNotebookSection) {
                            coroutineScope.launch {
                                // Get current notebooks
                                val currentIds = notebooksContainingPage.value.map { it.id }

                                // Remove page from notebooks it's no longer in
                                val toRemove = currentIds.filter { it !in selectedNotebookIds }
                                for (notebookId in toRemove) {
                                    pageNotebookRepository.removePageFromNotebook(currentNoteId, notebookId)
                                }

                                // Add page to new notebooks
                                val toAdd = selectedNotebookIds.filter { it !in currentIds }
                                for (notebookId in toAdd) {
                                    pageNotebookRepository.addPageToNotebook(currentNoteId, notebookId)
                                }
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