// Updated ChangeTracker.kt with improved change tracking
package com.wyldsoft.notes.sync

import com.wyldsoft.notes.database.entity.NoteEntity
import com.wyldsoft.notes.database.repository.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks changes to notes for efficient syncing
 */
class ChangeTracker(
    private val noteRepository: NoteRepository,
    private val coroutineScope: CoroutineScope
) {
    // Track changed note IDs since last sync
    private val changedNoteIds = ConcurrentHashMap<String, Date>()

    private var isSyncInProgress = false


    // Observable changed notes
    private val _changedNotes = MutableStateFlow<List<NoteEntity>>(emptyList())
    val changedNotes: StateFlow<List<NoteEntity>> = _changedNotes.asStateFlow()

    init {
        // Listen for note changes
        setupListeners()
    }

    fun beginSync() {
        isSyncInProgress = true
    }

    fun endSync() {
        isSyncInProgress = false
    }

    /**
     * Set up listeners to automatically track changes to notes
     */
    private fun setupListeners() {
        coroutineScope.launch {
            // Monitor all notes for changes
            noteRepository.getAllNotes().collectLatest { notes ->
                // This will be called whenever the notes collection changes
                // We now need to identify which notes have changed

                // For simplicity, we'll compare with our existing tracked notes
                // In a more sophisticated implementation, you might want to compare timestamps
                val currentIds = changedNoteIds.keys.toSet()
                val newIds = notes.map { it.id }.toSet()

                // Find new notes or modified notes
                val potentialChanges = notes.filter { note ->
                    // A note is considered changed if:
                    // 1. It wasn't previously tracked, or
                    // 2. Its update timestamp is newer than our tracked timestamp
                    val existingTimestamp = changedNoteIds[note.id]
                    existingTimestamp == null || note.updatedAt.after(existingTimestamp)
                }

                // Update our tracking for these notes
                potentialChanges.forEach { note ->
                    changedNoteIds[note.id] = note.updatedAt
                }

                // Update the observable state
                updateChangedNotes()
            }

            // Additional listeners could be added here for other events
            // such as stroke additions/removals that might not trigger note updates
        }
    }

    /**
     * Register that a note has changed
     * Call this method whenever a note is modified (e.g., when adding/removing strokes,
     * changing title, etc.)
     */
    fun registerNoteChanged(noteId: String) {
        // Skip if sync is in progress to avoid recursive changes
        if (isSyncInProgress) {
            android.util.Log.d("ChangeTracker", "Ignoring change during sync: $noteId")
            return
        }

        changedNoteIds[noteId] = Date()
        updateChangedNotes()

        android.util.Log.d("ChangeTracker", "Registered note change: $noteId")
    }

    /**
     * Get notes that changed since a specific time
     */
    suspend fun getChangedNotesSince(since: Date): List<NoteEntity> {
        val result = mutableListOf<NoteEntity>()

        // Get IDs of notes that changed since the timestamp
        val changedIds = changedNoteIds.entries
            .filter { it.value.after(since) }
            .map { it.key }

        android.util.Log.d("ChangeTracker", "Getting changes since ${since}. Found ${changedIds.size} changed notes")

        // Get note entities
        for (id in changedIds) {
            val note = noteRepository.getNoteById(id)
            if (note != null && note.updatedAt.after(since)) {
                result.add(note)
            }
        }

        return result
    }

    /**
     * Clear change tracking after sync
     */
    fun clearChanges() {
        changedNoteIds.clear()
        updateChangedNotes()
        android.util.Log.d("ChangeTracker", "Cleared all change tracking")
    }

    /**
     * Update the changed notes flow
     */
    private fun updateChangedNotes() {
        coroutineScope.launch {
            val changedIds = changedNoteIds.keys.toList()
            val notes = mutableListOf<NoteEntity>()

            for (id in changedIds) {
                val note = noteRepository.getNoteById(id)
                if (note != null) {
                    notes.add(note)
                }
            }

            _changedNotes.value = notes
        }
    }
}