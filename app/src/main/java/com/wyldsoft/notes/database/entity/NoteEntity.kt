package com.wyldsoft.notes.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Entity representing a note in the database
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val createdAt: Date,
    val updatedAt: Date,
    val width: Int,
    val height: Int
)