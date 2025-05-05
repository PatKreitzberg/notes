package com.wyldsoft.notes.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wyldsoft.notes.database.entity.HistoryActionEntity

/**
 * Data Access Object for the history_actions table
 */
@Dao
interface HistoryActionDao {
    /**
     * Gets all actions for a note in sequence order
     */
    @Query("SELECT * FROM history_actions WHERE noteId = :noteId ORDER BY sequenceNumber ASC")
    suspend fun getActionsForNote(noteId: String): List<HistoryActionEntity>

    /**
     * Gets the last N actions for a note (for limiting storage)
     */
    @Query("SELECT * FROM history_actions WHERE noteId = :noteId ORDER BY sequenceNumber DESC LIMIT :limit")
    suspend fun getLastActionsForNote(noteId: String, limit: Int): List<HistoryActionEntity>

    /**
     * Gets the highest sequence number for a note
     */
    @Query("SELECT MAX(sequenceNumber) FROM history_actions WHERE noteId = :noteId")
    suspend fun getMaxSequenceNumber(noteId: String): Int?

    /**
     * Inserts a new action
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: HistoryActionEntity)

    /**
     * Inserts multiple actions at once
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActions(actions: List<HistoryActionEntity>)

    /**
     * Removes actions above a certain sequence number (for redo-truncation)
     */
    @Query("DELETE FROM history_actions WHERE noteId = :noteId AND sequenceNumber > :sequenceNumber")
    suspend fun deleteActionsAboveSequence(noteId: String, sequenceNumber: Int)

    /**
     * Cleans up old actions, keeping only the most recent ones
     */
    @Query("DELETE FROM history_actions WHERE noteId = :noteId AND sequenceNumber NOT IN (SELECT sequenceNumber FROM history_actions WHERE noteId = :noteId ORDER BY sequenceNumber DESC LIMIT :limit)")
    suspend fun pruneHistoryToLimit(noteId: String, limit: Int)

    /**
     * Deletes all history for a note
     */
    @Query("DELETE FROM history_actions WHERE noteId = :noteId")
    suspend fun clearHistoryForNote(noteId: String)
}