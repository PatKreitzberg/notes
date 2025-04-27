package com.wyldsoft.notes.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing a point within a stroke
 * Foreign key to stroke ensures points are associated with a stroke
 */
@Entity(
    tableName = "stroke_points",
    foreignKeys = [
        ForeignKey(
            entity = StrokeEntity::class,
            parentColumns = ["id"],
            childColumns = ["strokeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("strokeId")]
)
data class StrokePointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val strokeId: String,
    val x: Float,
    val y: Float,
    val pressure: Float,
    val size: Float,
    val tiltX: Int,
    val tiltY: Int,
    val timestamp: Long,
    val sequenceNumber: Int // To maintain the order of points
)