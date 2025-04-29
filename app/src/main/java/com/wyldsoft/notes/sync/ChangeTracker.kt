// app/src/main/java/com/wyldsoft/notes/sync/ChangeTracker.kt
package com.wyldsoft.notes.sync

import com.wyldsoft.notes.database.entity.NoteEntity
import com.wyldsoft.notes.database.repository.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
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

    // Observable changed notes
    private val _changedNotes = MutableStateFlow<List<NoteEntity>>(emptyList())
    val changedNotes: StateFlow<List<NoteEntity>> = _changedNotes.asStateFlow()

    init {
        // Listen for note changes
        setupListeners()
    }

    private fun setupListeners() {
        coroutineScope.launch {
            noteRepository.getAllNotes().collect { notes ->
                // Don't do anything special yet, we'll track changes through explicit calls
            }
        }
    }

    /**
     * Register that a note has changed
     */
    fun registerNoteChanged(noteId: String) {
        changedNoteIds[noteId] = Date()
        updateChangedNotes()
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