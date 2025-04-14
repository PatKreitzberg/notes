package com.wyldsoft.notes.data.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.wyldsoft.notes.data.entity.Page
import com.wyldsoft.notes.data.entity.StrokeEntity

/**
 * Represents a relationship between a Page and its Strokes.
 *
 * This class is used by Room to fetch a page with all its strokes in a single query.
 *
 * @property page The page entity
 * @property strokes List of strokes that belong to the page
 */
data class PageWithStrokes(
    @Embedded val page: Page,
    @Relation(
        parentColumn = "id",
        entityColumn = "pageId"
    )
    val strokes: List<StrokeEntity>
)