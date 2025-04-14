package com.wyldsoft.notes.data.repository

import com.wyldsoft.notes.data.dao.NotebookDao
import com.wyldsoft.notes.data.entity.Notebook
import com.wyldsoft.notes.data.entity.PageType
import com.wyldsoft.notes.data.relation.NotebookWithPages
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.UUID

/**
 * Repository for notebooks.
 *
 * Provides a clean API for interacting with notebooks in the database.
 *
 * @property notebookDao Data access object for notebooks
 */
class NotebookRepository(private val notebookDao: NotebookDao) {
    /**
     * Get all notebooks.
     *
     * @return Flow of all notebooks
     */
    fun getAllNotebooks(): Flow<List<Notebook>> {
        return notebookDao.getAllNotebooks()
    }

    /**
     * Get all notebooks with their pages.
     *
     * @return Flow of all notebooks with their pages
     */
    fun getAllNotebooksWithPages(): Flow<List<NotebookWithPages>> {
        return notebookDao.getAllNotebooksWithPages()
    }

    /**
     * Get a notebook by its ID.
     *
     * @param notebookId ID of the notebook to retrieve
     * @return The notebook with the given ID, or null if not found
     */
    suspend fun getNotebookById(notebookId: String): Notebook? {
        return notebookDao.getNotebookById(notebookId)
    }

    /**
     * Get a notebook by its ID as a Flow.
     *
     * @param notebookId ID of the notebook to retrieve
     * @return Flow of the notebook with the given ID
     */
    fun getNotebookByIdFlow(notebookId: String): Flow<Notebook?> {
        return notebookDao.getNotebookByIdFlow(notebookId)
    }

    /**
     * Get a notebook with all its pages.
     *
     * @param notebookId ID of the notebook to retrieve
     * @return The notebook with all its pages
     */
    suspend fun getNotebookWithPages(notebookId: String): NotebookWithPages? {
        return notebookDao.getNotebookWithPages(notebookId)
    }

    /**
     * Create a new notebook.
     *
     * @param title Title of the notebook
     * @param pageType Type of pages in the notebook
     * @param coverColor Color of the notebook cover
     * @return ID of the created notebook
     */
    suspend fun createNotebook(
        title: String,
        pageType: PageType = PageType.A4,
        coverColor: Int = 0xFF1E88E5.toInt()
    ): String {
        val notebookId = UUID.randomUUID().toString()
        val notebook = Notebook(
            id = notebookId,
            title = title,
            pageType = pageType,
            coverColor = coverColor,
            createdAt = Date(),
            lastModifiedAt = Date()
        )
        notebookDao.insertNotebook(notebook)
        return notebookId
    }

    /**
     * Update a notebook.
     *
     * @param notebook The notebook to update
     */
    suspend fun updateNotebook(notebook: Notebook) {
        notebookDao.updateNotebook(notebook.copy(lastModifiedAt = Date()))
    }

    /**
     * Update the title of a notebook.
     *
     * @param notebookId ID of the notebook to update
     * @param title New title
     */
    suspend fun updateNotebookTitle(notebookId: String, title: String) {
        val notebook = notebookDao.getNotebookById(notebookId)
        notebook?.let {
            notebookDao.updateNotebook(it.copy(title = title, lastModifiedAt = Date()))
        }
    }

    /**
     * Delete a notebook.
     *
     * @param notebookId ID of the notebook to delete
     */
    suspend fun deleteNotebook(notebookId: String) {
        notebookDao.deleteNotebookById(notebookId)
    }

    /**
     * Update the lastModifiedAt field of a notebook.
     *
     * @param notebookId ID of the notebook to update
     */
    suspend fun updateLastModifiedAt(notebookId: String) {
        notebookDao.updateLastModifiedAt(notebookId)
    }

    /**
     * Get the page count for a specific notebook.
     *
     * @param notebookId ID of the notebook
     * @return The number of pages in the notebook
     */
    suspend fun getPageCount(notebookId: String): Int {
        return notebookDao.getPageCount(notebookId)
    }
}