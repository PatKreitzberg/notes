package com.wyldsoft.notes.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

/**
 * Entity representing a notebook in the database.
 *
 * A notebook is a collection of pages that can be organized together.
 *
 * @property id Unique identifier for the notebook
 * @property title Title of the notebook
 * @property createdAt Date when the notebook was created
 * @property lastModifiedAt Date when the notebook was last modified
 * @property lastSyncedAt Date when the notebook was last synced (for future cloud sync)
 * @property pageType Type of pages in the notebook (e.g., A4, Letter)
 * @property coverColor Color of the notebook cover
 */
@Entity(tableName = "notebooks")
data class Notebook(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Date = Date(),
    val lastModifiedAt: Date = Date(),
    val lastSyncedAt: Date? = null,
    val pageType: PageType = PageType.A4,
    val coverColor: Int = 0xFF1E88E5.toInt() // Default blue color
)

/**
 * Enum defining the possible page types/sizes for notebooks.
 */
enum class PageType {
    A4,
    LETTER
}