package com.wyldsoft.notes.views

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.wyldsoft.notes.NotesApp
import com.wyldsoft.notes.data.entity.Notebook
import com.wyldsoft.notes.data.entity.PageType
import com.wyldsoft.notes.data.relation.NotebookWithPages
import com.wyldsoft.notes.viewmodels.NotebookListUiState
import com.wyldsoft.notes.viewmodels.NotebookViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Composable for displaying the list of notebooks.
 *
 * @param navController Navigation controller for navigating between screens
 */
@ExperimentalFoundationApi
@Composable
fun NotebookListView(navController: NavController) {
    val notebookRepository = NotesApp.getInstance().notebookRepository
    val viewModel: NotebookViewModel = viewModel(
        factory = NotebookViewModel.Factory(notebookRepository)
    )

    val uiState by viewModel.uiState.collectAsState()
    val notebooks by viewModel.notebooksWithPages.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var showNewNotebookDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Notebooks") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewNotebookDialog = true },
                backgroundColor = MaterialTheme.colors.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Notebook",
                    tint = Color.White
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState) {
                is NotebookListUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is NotebookListUiState.Success -> {
                    if (notebooks.isEmpty()) {
                        EmptyNotebooksView(onCreateNotebook = { showNewNotebookDialog = true })
                    } else {
                        NotebooksGrid(
                            notebooks = notebooks,
                            onNotebookClick = { notebookId ->
                                // Navigate to notebook detail screen
                                navController.navigate("notebook/$notebookId")
                            },
                            onDeleteNotebook = { notebookId ->
                                scope.launch {
                                    viewModel.deleteNotebook(notebookId)
                                }
                            }
                        )
                    }
                }

                is NotebookListUiState.Error -> {
                    val errorMessage = (uiState as NotebookListUiState.Error).message
                    Text(
                        text = "Error: $errorMessage",
                        color = Color.Red,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    )
                }
            }
        }
    }

    if (showNewNotebookDialog) {
        NewNotebookDialog(
            onDismiss = { showNewNotebookDialog = false },
            onConfirm = { title, pageType, color ->
                scope.launch {
                    val notebookId = viewModel.createNotebook(title, pageType, color)
                    showNewNotebookDialog = false
                    // Navigate to the new notebook
                    navController.navigate("notebook/$notebookId")
                }
            }
        )
    }
}

/**
 * Composable for displaying an empty state when no notebooks exist.
 *
 * @param onCreateNotebook Callback for when the user wants to create a new notebook
 */
@Composable
fun EmptyNotebooksView(onCreateNotebook: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Book,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No Notebooks",
            fontSize = 24.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Create your first notebook to get started",
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onCreateNotebook) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create Notebook")
        }
    }
}

/**
 * Composable for displaying the grid of notebooks.
 *
 * @param notebooks List of notebooks with their pages
 * @param onNotebookClick Callback for when a notebook is clicked
 * @param onDeleteNotebook Callback for when a notebook is deleted
 */
@Composable
fun NotebooksGrid(
    notebooks: List<NotebookWithPages>,
    onNotebookClick: (String) -> Unit,
    onDeleteNotebook: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(notebooks) { notebookWithPages ->
            NotebookItem(
                notebook = notebookWithPages.notebook,
                pageCount = notebookWithPages.pages.size,
                onClick = { onNotebookClick(notebookWithPages.notebook.id) },
                onDelete = { onDeleteNotebook(notebookWithPages.notebook.id) }
            )
        }
    }
}

/**
 * Composable for displaying a single notebook item.
 *
 * @param notebook The notebook to display
 * @param pageCount Number of pages in the notebook
 * @param onClick Callback for when the notebook is clicked
 * @param onDelete Callback for when the notebook is deleted
 */
@Composable
fun NotebookItem(
    notebook: Notebook,
    pageCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        Column {
            // Notebook cover
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color(notebook.coverColor))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = notebook.title.take(1).uppercase(),
                    fontSize = 40.sp,
                    color = Color.White
                )
            }

            // Notebook info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = notebook.title,
                    style = MaterialTheme.typography.h6,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "$pageCount pages | ${notebook.pageType.name}",
                    style = MaterialTheme.typography.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "Modified: ${formatDate(notebook.lastModifiedAt)}",
                    style = MaterialTheme.typography.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Delete button
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Delete",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(24.dp)
                .clickable { showDeleteConfirmation = true },
            tint = Color.White
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Notebook") },
            text = { Text("Are you sure you want to delete '${notebook.title}'? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirmation = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDeleteConfirmation = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Dialog for creating a new notebook.
 *
 * @param onDismiss Callback for when the dialog is dismissed
 * @param onConfirm Callback for when a new notebook is confirmed
 */
@Composable
fun NewNotebookDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, PageType, Int) -> Unit
) {
    var notebookTitle by remember { mutableStateOf("") }
    var selectedPageType by remember { mutableStateOf(PageType.A4) }
    var selectedColor by remember { mutableStateOf(0xFF1E88E5.toInt()) } // Default blue

    val colors = listOf(
        0xFF1E88E5.toInt(), // Blue
        0xFFE53935.toInt(), // Red
        0xFF43A047.toInt(), // Green
        0xFFFFB300.toInt(), // Amber
        0xFF8E24AA.toInt(), // Purple
        0xFF00897B.toInt(), // Teal
        0xFFFF6F00.toInt(), // Orange
        0xFF546E7A.toInt()  // Blue Grey
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Notebook") },
        text = {
            Column {
                OutlinedTextField(
                    value = notebookTitle,
                    onValueChange = { notebookTitle = it },
                    label = { Text("Notebook Title") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Page Type:")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PageTypeOption(
                        type = PageType.A4,
                        isSelected = selectedPageType == PageType.A4,
                        onClick = { selectedPageType = PageType.A4 }
                    )

                    PageTypeOption(
                        type = PageType.LETTER,
                        isSelected = selectedPageType == PageType.LETTER,
                        onClick = { selectedPageType = PageType.LETTER }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Cover Color:")
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(100.dp)
                ) {
                    items(colors) { color ->
                        ColorOption(
                            color = color,
                            isSelected = selectedColor == color,
                            onClick = { selectedColor = color }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (notebookTitle.isNotBlank()) {
                        onConfirm(notebookTitle, selectedPageType, selectedColor)
                    }
                },
                enabled = notebookTitle.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Composable for displaying a page type option.
 *
 * @param type The page type
 * @param isSelected Whether this option is selected
 * @param onClick Callback for when the option is clicked
 */
@Composable
fun PageTypeOption(
    type: PageType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colors.primary else Color.LightGray,
                shape = RoundedCornerShape(4.dp)
            )
            .background(if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.1f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = type.name,
            color = if (isSelected) MaterialTheme.colors.primary else Color.Gray
        )
    }
}

/**
 * Composable for displaying a color option.
 *
 * @param color The color
 * @param isSelected Whether this option is selected
 * @param onClick Callback for when the option is clicked
 */
@Composable
fun ColorOption(
    color: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(40.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(color))
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = Color.White,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick)
    )
}

/**
 * Format a date to a readable string.
 *
 * @param date The date to format
 * @return The formatted date string
 */
private fun formatDate(date: Date): String {
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return formatter.format(date)
}