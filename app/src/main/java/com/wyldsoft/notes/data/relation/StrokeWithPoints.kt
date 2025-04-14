package com.wyldsoft.notes.data.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.wyldsoft.notes.data.entity.StrokeEntity
import com.wyldsoft.notes.data.entity.StrokePointEntity

/**
 * Represents a relationship between a Stroke and its Points.
 *
 * This class is used by Room to fetch a stroke with all its points in a single query.
 *
 * @property stroke The stroke entity
 * @property points List of points that belong to the stroke
 */
data class StrokeWithPoints(
    @Embedded val stroke: StrokeEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "strokeId"
    )
    val points: List<StrokePointEntity>
)