package com.wyldsoft.notes.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Entity representing a single action in the history stack for undo/redo operations
 */
@Entity(
    tableName = "history_actions",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("noteId"), Index("sequenceNumber")]
)
data class HistoryActionEntity(
    @PrimaryKey
    val id: String,
    val noteId: String,
    val actionType: String, // ADD_STROKES, DELETE_STROKES, MOVE_STROKES
    val actionData: String, // JSON data representing the action details
    val sequenceNumber: Int, // Position in the history stack
    val createdAt: Date
)