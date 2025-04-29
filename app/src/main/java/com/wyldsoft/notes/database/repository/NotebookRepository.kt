package com.wyldsoft.notes.database.repository

import com.wyldsoft.notes.database.dao.NotebookDao
import com.wyldsoft.notes.database.entity.NotebookEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.UUID

class NotebookRepository(private val notebookDao: NotebookDao) {

    fun getAllNotebooks(): Flow<List<NotebookEntity>> {
        return notebookDao.getAllNotebooks()
    }

    fun getNotebooksInFolder(folderId: String): Flow<List<NotebookEntity>> {
        return notebookDao.getNotebooksInFolder(folderId)
    }

    fun getNotebooksWithoutFolder(): Flow<List<NotebookEntity>> {
        return notebookDao.getNotebooksWithoutFolder()
    }

    suspend fun getNotebookById(notebookId: String): NotebookEntity? {
        return notebookDao.getNotebookById(notebookId)
    }

    suspend fun createNotebook(title: String, folderId: String? = null): NotebookEntity {
        val now = Date()
        val notebook = NotebookEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            folderId = folderId,
            createdAt = now,
            updatedAt = now
        )

        notebookDao.insertNotebook(notebook)
        return notebook
    }

    suspend fun updateNotebook(notebook: NotebookEntity) {
        val updatedNotebook = notebook.copy(updatedAt = Date())
        notebookDao.updateNotebook(updatedNotebook)
    }

    suspend fun deleteNotebook(notebookId: String) {
        notebookDao.deleteNotebookById(notebookId)
    }
}

