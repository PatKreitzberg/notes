package com.wyldsoft.notes.models

import java.util.Date

data class FolderModel(
    val id: String,
    val name: String,
    val path: String,
    val parentId: String?,
    val createdAt: Date,
    val updatedAt: Date,
    val isExpanded: Boolean = false,
    val hasSubFolders: Boolean = false
)