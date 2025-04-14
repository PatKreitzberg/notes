package com.wyldsoft.notes.data.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.wyldsoft.notes.data.entity.Notebook
import com.wyldsoft.notes.data.entity.Page

/**
 * Represents a relationship between a Notebook and its Pages.
 *
 * This class is used by Room to fetch a notebook with all its pages in a single query.
 *
 * @property notebook The notebook entity
 * @property pages List of pages that belong to the notebook
 */
data class NotebookWithPages(
    @Embedded val notebook: Notebook,
    @Relation(
        parentColumn = "id",
        entityColumn = "notebookId"
    )
    val pages: List<Page>
)