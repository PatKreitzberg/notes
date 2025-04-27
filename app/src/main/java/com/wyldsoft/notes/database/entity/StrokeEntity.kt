package com.wyldsoft.notes.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wyldsoft.notes.utils.Pen
import java.util.Date

/**
 * Entity representing a stroke in the database
 * Foreign key to note ensures strokes are associated with a note
 */
@Entity(
    tableName = "strokes",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("noteId")]
)
data class StrokeEntity(
    @PrimaryKey
    val id: String,
    val noteId: String,
    val penName: String,
    val size: Float,
    val color: Int,
    val top: Float,
    val bottom: Float,
    val left: Float,
    val right: Float,
    val createdAt: Date,
    val updatedAt: Date,
    val createdScrollY: Float
)