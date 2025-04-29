// 1. Create FolderEntity.kt
package com.wyldsoft.notes.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey
    val id: String,
    val path: String,
    val name: String,
    val parentId: String?,
    val createdAt: Date,
    val updatedAt: Date
)



