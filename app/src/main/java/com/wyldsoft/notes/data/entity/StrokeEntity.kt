package com.wyldsoft.notes.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wyldsoft.notes.utils.Pen
import java.util.Date
import java.util.UUID

/**
 * Entity representing a stroke in the database.
 *
 * A stroke belongs to a page and contains drawing data including size, color, position, etc.
 * The actual points of the stroke are stored in [StrokePointEntity].
 *
 * @property id Unique identifier for the stroke
 * @property pageId Foreign key reference to the parent page
 * @property size Size/thickness of the stroke
 * @property penName Type of pen used for the stroke (BALLPEN, MARKER, FOUNTAIN)
 * @property color Color of the stroke
 * @property top Top position of stroke's bounding box
 * @property bottom Bottom position of stroke's bounding box
 * @property left Left position of stroke's bounding box
 * @property right Right position of stroke's bounding box
 * @property createdAt Date when the stroke was created
 */
@Entity(
    tableName = "strokes",
    foreignKeys = [
        ForeignKey(
            entity = Page::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("pageId")
    ]
)
data class StrokeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val pageId: String,
    val size: Float,
    val penName: String,
    val color: Int,
    val top: Float,
    val bottom: Float,
    val left: Float,
    val right: Float,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)