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
import androidx.compose.material.Divider
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.wyldsoft.notes.NotesApp
import com.wyldsoft.notes.data.entity.Notebook
import com.wyldsoft.notes.data.entity.Page
import com.wyldsoft.notes.viewmodels.NotebookViewModel
import com.wyldsoft.notes.viewmodels.PageViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Composable for displaying the notebook detail screen with its pages.
 *
 * @param navController Navigation controller for navigating between screens
 * @param notebookId ID of the notebook to display
 */
@ExperimentalFoundationApi
@Composable
fun NotebookDetailView(
    navController: NavController,
    notebookId: String
) {
    val app = NotesApp.getInstance()
    val notebookViewModel: NotebookViewModel = viewModel(
        factory = NotebookViewModel.Factory(app.notebookRepository)
    )
    val pageViewModel: PageViewModel = viewModel(
        factory = PageViewModel.Factory(app.pageRepository, app.strokeRepository)
    )

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var notebook by remember { mutableStateOf<Notebook?>(null) }
    var pages by remember { mutableStateOf<List<Page>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Fetch notebook and pages
    LaunchedEffect(notebookId) {
        isLoading = true
        notebook = app.notebookRepository.getNotebookById(notebookId)
        pages = app.pageRepository.getPagesForNotebook(notebookId)
        isLoading = false
    }

    var showAddPageConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(notebook?.title ?: "Notebook") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddPageConfirmation = true },
                backgroundColor = MaterialTheme.colors.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Page",
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
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (notebook == null) {
                Text(
                    text = "Notebook not found",
                    color = Color.Red,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Notebook info
                    NotebookInfoHeader(notebook = notebook!!, pageCount = pages.size)

                    Divider()

                    // Pages grid or empty state
                    if (pages.isEmpty()) {
                        EmptyPagesView(
                            onAddPage = { showAddPageConfirmation = true }
                        )
                    } else {
                        PagesGrid(
                            pages = pages,
                            onPageClick = { pageId ->
                                // Navigate to editor with this page
                                navController.navigate("editor/$pageId")
                            },
                            onDeletePage = { pageId ->
                                scope.launch {
                                    pageViewModel.deletePage(pageId)
                                    // Refresh pages list
                                    pages = app.pageRepository.getPagesForNotebook(notebookId)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddPageConfirmation) {
        AlertDialog(
            onDismissRequest = { showAddPageConfirmation = false },
            title = { Text("Add New Page") },
            text = { Text("Add a new page to this notebook?") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            // Get screen dimensions for the page size
                            val metrics = context.resources.displayMetrics
                            val width = metrics.widthPixels
                            val height = metrics.heightPixels

                            // Create the page
                            val pageId = pageViewModel.createPage(notebookId, width, height)
                            showAddPageConfirmation = false

                            // Navigate to the editor with the new page
                            navController.navigate("editor/$pageId")
                        }
                    }
                ) {
                    Text("Add Page")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showAddPageConfirmation = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Composable for displaying the notebook information header.
 *
 * @param notebook The notebook to display
 * @param pageCount Number of pages in the notebook
 */
@Composable
fun NotebookInfoHeader(
    notebook: Notebook,
    pageCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color indicator for the notebook
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(Color(notebook.coverColor))
                    .border(1.dp, Color.Gray)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = notebook.title,
                    style = MaterialTheme.typography.h5
                )

                Text(
                    text = "$pageCount pages | ${notebook.pageType.name}",
                    style = MaterialTheme.typography.caption
                )

                Text(
                    text = "Created: ${formatDate(notebook.createdAt)}",
                    style = MaterialTheme.typography.caption
                )

                Text(
                    text = "Last modified: ${formatDate(notebook.lastModifiedAt)}",
                    style = MaterialTheme.typography.caption
                )
            }
        }
    }
}

/**
 * Composable for displaying the grid of pages.
 *
 * @param pages List of pages to display
 * @param onPageClick Callback for when a page is clicked
 * @param onDeletePage Callback for when a page is deleted
 */
@Composable
fun PagesGrid(
    pages: List<Page>,
    onPageClick: (String) -> Unit,
    onDeletePage: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(pages) { page ->
            PageItem(
                page = page,
                onClick = { onPageClick(page.id) },
                onDelete = { onDeletePage(page.id) }
            )
        }
    }
}

/**
 * Composable for displaying a single page item.
 *
 * @param page The page to display
 * @param onClick Callback for when the page is clicked
 * @param onDelete Callback for when the page is deleted
 */
@Composable
fun PageItem(
    page: Page,
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
            // Page preview (blank for now, would show thumbnail in the future)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .background(Color.White)
                    .border(1.dp, Color.LightGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = Color.LightGray,
                    modifier = Modifier.size(40.dp)
                )
            }

            // Page info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = "Page ${page.pageNumber}",
                    style = MaterialTheme.typography.body1
                )

                Text(
                    text = "Modified: ${formatDate(page.lastModifiedAt)}",
                    style = MaterialTheme.typography.caption,
                    maxLines = 1
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
            tint = Color.Gray
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Page") },
            text = { Text("Are you sure you want to delete Page ${page.pageNumber}? This cannot be undone.") },
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
 * Composable for displaying an empty state when no pages exist.
 *
 * @param onAddPage Callback for when the user wants to add a new page
 */
@Composable
fun EmptyPagesView(onAddPage: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No Pages",
            fontSize = 24.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Add your first page to start taking notes",
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onAddPage) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Page")
        }
    }
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