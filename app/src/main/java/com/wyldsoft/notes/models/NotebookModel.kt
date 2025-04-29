package com.wyldsoft.notes.models

import java.util.Date

data class NotebookModel(
    val id: String,
    val title: String,
    val folderId: String?,
    val createdAt: Date,
    val updatedAt: Date,
    val pageCount: Int = 0
)