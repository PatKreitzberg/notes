package com.wyldsoft.notes.database.repository

import android.content.Context
import com.wyldsoft.notes.database.dao.HistoryActionDao
import com.wyldsoft.notes.history.HistoryManager
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository for managing history managers for notes
 */
class HistoryRepository(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val historyActionDao: HistoryActionDao,
    private val maxStoredActions: Int = 30
) {
    // Cache of history managers by note ID
    private val historyManagers = ConcurrentHashMap<String, HistoryManager>()

    /**
     * Gets or creates a history manager for a note
     */
    fun getHistoryManager(noteId: String): HistoryManager {
        return historyManagers.getOrPut(noteId) {
            HistoryManager(
                context = context,
                noteId = noteId,
                coroutineScope = coroutineScope,
                historyActionDao = historyActionDao,
                maxStoredActions = maxStoredActions
            )
        }
    }

    /**
     * Clears the history manager for a note
     */
    fun clearHistoryForNote(noteId: String) {
        historyManagers[noteId]?.clearHistory()
        historyManagers.remove(noteId)
    }

    /**
     * Clears all history managers
     */
    fun clearAllHistory() {
        historyManagers.forEach { (_, manager) ->
            manager.clearHistory()
        }
        historyManagers.clear()
    }
}