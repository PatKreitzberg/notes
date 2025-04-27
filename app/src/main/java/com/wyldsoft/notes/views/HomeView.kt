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
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.wyldsoft.notes.NotesApp
import com.wyldsoft.notes.database.entity.NoteEntity
import com.wyldsoft.notes.utils.noRippleClickable
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@ExperimentalFoundationApi
@Composable
fun HomeView(navController: NavController) {
    val context = LocalContext.current
    val app = NotesApp.getApp(context)
    val noteRepository = app.noteRepository
    val scope = rememberCoroutineScope()

    // Collect notes from repository
    val notes by noteRepository.getAllNotes().collectAsState(initial = emptyList())

    var showSettingsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Notes") },
                actions = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(24.dp)
                            .noRippleClickable { showSettingsDialog = true }
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("editor") },
                backgroundColor = MaterialTheme.colors.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create new note")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (notes.isEmpty()) {
                EmptyNotesView()
            } else {
                NotesGrid(
                    notes = notes,
                    onNoteClick = { note ->
                        navController.navigate("editor/${note.id}")
                    },
                    onDeleteClick = { note ->
                        scope.launch {
                            noteRepository.deleteNote(note.id)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun EmptyNotesView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No notes yet",
                style = MaterialTheme.typography.h5
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Create a new note with the + button",
                style = MaterialTheme.typography.body1,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun NotesGrid(
    notes: List<NoteEntity>,
    onNoteClick: (NoteEntity) -> Unit,
    onDeleteClick: (NoteEntity) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(notes) { note ->
            NoteItem(
                note = note,
                onClick = { onNoteClick(note) },
                onDeleteClick = { onDeleteClick(note) }
            )
        }
    }
}

@Composable
fun NoteItem(
    note: NoteEntity,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFFFF8E1))
            .border(1.dp, Color(0xFFFFE0B2), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.h6,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Last edited: ${dateFormat.format(note.updatedAt)}",
                style = MaterialTheme.typography.caption,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.weight(1f))

            // Delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete note",
                    tint = Color.Gray,
                    modifier = Modifier
                        .size(24.dp)
                        .noRippleClickable(onDeleteClick)
                )
            }
        }
    }
}