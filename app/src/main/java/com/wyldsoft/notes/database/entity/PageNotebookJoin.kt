package com.wyldsoft.notes.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.Date

@Entity(
    tableName = "page_notebook_join",
    primaryKeys = ["pageId", "notebookId"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("pageId"),
        Index("notebookId")
    ]
)
data class PageNotebookJoin(
    val pageId: String,
    val notebookId: String,
    val addedAt: Date
)