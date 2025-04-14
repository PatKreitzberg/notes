package com.wyldsoft.notes.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

/**
 * Entity representing a page in the database.
 *
 * A page belongs to a notebook and contains stroke data for drawings/notes.
 *
 * @property id Unique identifier for the page
 * @property notebookId Foreign key reference to the parent notebook
 * @property pageNumber Position of the page within the notebook
 * @property createdAt Date when the page was created
 * @property lastModifiedAt Date when the page was last modified
 * @property width Width of the page in pixels
 * @property height Height of the page in pixels
 */
@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = Notebook::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("notebookId")
    ]
)
data class Page(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val notebookId: String,
    val pageNumber: Int,
    val createdAt: Date = Date(),
    val lastModifiedAt: Date = Date(),
    val width: Int,
    val height: Int
)