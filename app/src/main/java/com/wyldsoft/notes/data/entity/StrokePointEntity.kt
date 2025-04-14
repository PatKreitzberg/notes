package com.wyldsoft.notes.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Entity representing a point within a stroke in the database.
 *
 * A stroke point belongs to a stroke and contains the coordinates and pressure data
 * for a single point in the drawing.
 *
 * @property id Unique identifier for the stroke point
 * @property strokeId Foreign key reference to the parent stroke
 * @property x X coordinate of the point
 * @property y Y coordinate of the point
 * @property pressure Pressure value of the point (from stylus input)
 * @property size Size value of the point
 * @property tiltX X-axis tilt of the stylus
 * @property tiltY Y-axis tilt of the stylus
 * @property timestamp Timestamp when the point was recorded
 * @property sequence Order of the point within the stroke
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
    indices = [
        Index("strokeId")
    ]
)
data class StrokePointEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val strokeId: String,
    val x: Float,
    val y: Float,
    val pressure: Float,
    val size: Float,
    val tiltX: Int,
    val tiltY: Int,
    val timestamp: Long,
    val sequence: Int // To maintain the order of points
)