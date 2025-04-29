// 2. Create NotebookEntity.kt
package com.wyldsoft.notes.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "notebooks",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("folderId")]
)
data class NotebookEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val folderId: String?,
    val createdAt: Date,
    val updatedAt: Date
)