package com.wyldsoft.notes.data.repository

import com.wyldsoft.notes.data.dao.NotebookDao
import com.wyldsoft.notes.data.dao.PageDao
import com.wyldsoft.notes.data.entity.Page
import com.wyldsoft.notes.data.relation.PageWithStrokes
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.UUID

/**
 * Repository for pages.
 *
 * Provides a clean API for interacting with pages in the database.
 *
 * @property pageDao Data access object for pages
 * @property notebookDao Data access object for notebooks
 */
class PageRepository(
    private val pageDao: PageDao,
    private val notebookDao: NotebookDao
) {
    /**
     * Get all pages for a specific notebook.
     *
     * @param notebookId ID of the notebook
     * @return List of pages in the notebook
     */
    suspend fun getPagesForNotebook(notebookId: String): List<Page> {
        return pageDao.getPagesForNotebook(notebookId)
    }

    /**
     * Get all pages for a specific notebook as a Flow.
     *
     * @param notebookId ID of the notebook
     * @return Flow of pages in the notebook
     */
    fun getPagesForNotebookFlow(notebookId: String): Flow<List<Page>> {
        return pageDao.getPagesForNotebookFlow(notebookId)
    }

    /**
     * Get a page by its ID.
     *
     * @param pageId ID of the page to retrieve
     * @return The page with the given ID, or null if not found
     */
    suspend fun getPageById(pageId: String): Page? {
        return pageDao.getPageById(pageId)
    }

    /**
     * Get a page by its ID as a Flow.
     *
     * @param pageId ID of the page to retrieve
     * @return Flow of the page with the given ID
     */
    fun getPageByIdFlow(pageId: String): Flow<Page?> {
        return pageDao.getPageByIdFlow(pageId)
    }

    /**
     * Get a page with all its strokes.
     *
     * @param pageId ID of the page to retrieve
     * @return The page with all its strokes
     */
    suspend fun getPageWithStrokes(pageId: String): PageWithStrokes? {
        return pageDao.getPageWithStrokes(pageId)
    }

    /**
     * Get a page with all its strokes as a Flow.
     *
     * @param pageId ID of the page to retrieve
     * @return Flow of the page with all its strokes
     */
    fun getPageWithStrokesFlow(pageId: String): Flow<PageWithStrokes?> {
        return pageDao.getPageWithStrokesFlow(pageId)
    }

    /**
     * Create a new page in a notebook.
     *
     * @param notebookId ID of the notebook
     * @param width Width of the page
     * @param height Height of the page
     * @return ID of the created page
     */
    suspend fun createPage(notebookId: String, width: Int, height: Int): String {
        val pageId = UUID.randomUUID().toString()

        // Get the highest page number and increment it
        val maxPageNumber = pageDao.getMaxPageNumber(notebookId) ?: 0
        val newPageNumber = maxPageNumber + 1

        val page = Page(
            id = pageId,
            notebookId = notebookId,
            pageNumber = newPageNumber,
            width = width,
            height = height,
            createdAt = Date(),
            lastModifiedAt = Date()
        )

        pageDao.insertPage(page)

        // Update the notebook's lastModifiedAt
        notebookDao.updateLastModifiedAt(notebookId)

        return pageId
    }

    /**
     * Update a page.
     *
     * @param page The page to update
     */
    suspend fun updatePage(page: Page) {
        pageDao.updatePage(page.copy(lastModifiedAt = Date()))
        notebookDao.updateLastModifiedAt(page.notebookId)
    }

    /**
     * Delete a page.
     *
     * @param pageId ID of the page to delete
     */
    suspend fun deletePage(pageId: String) {
        val page = pageDao.getPageById(pageId)
        page?.let {
            pageDao.deletePageById(pageId)
            notebookDao.updateLastModifiedAt(it.notebookId)
        }
    }

    /**
     * Update the lastModifiedAt field of a page.
     *
     * @param pageId ID of the page to update
     */
    suspend fun updateLastModifiedAt(pageId: String) {
        val page = pageDao.getPageById(pageId)
        page?.let {
            pageDao.updateLastModifiedAt(pageId)
            notebookDao.updateLastModifiedAt(it.notebookId)
        }
    }

    /**
     * Move a page to a different position within the same notebook.
     *
     * @param pageId ID of the page to move
     * @param newPosition New position for the page
     */
    suspend fun movePageToPosition(pageId: String, newPosition: Int) {
        val page = pageDao.getPageById(pageId) ?: return
        val pages = pageDao.getPagesForNotebook(page.notebookId)

        // Remove the page from the current list
        val pagesWithoutCurrent = pages.filter { it.id != pageId }

        // Calculate the new positions
        val updatedPages = pagesWithoutCurrent.mapIndexed { index, p ->
            if (index < newPosition) {
                p.copy(pageNumber = index + 1)
            } else {
                p.copy(pageNumber = index + 2)
            }
        }

        // Update the target page's position
        val updatedPage = page.copy(pageNumber = newPosition + 1, lastModifiedAt = Date())

        // Save all updates
        pageDao.updatePage(updatedPage)
        updatedPages.forEach { pageDao.updatePage(it) }

        // Update notebook
        notebookDao.updateLastModifiedAt(page.notebookId)
    }

    /**
     * Move a page to a different notebook.
     *
     * @param pageId ID of the page to move
     * @param newNotebookId ID of the destination notebook
     */
    suspend fun movePageToNotebook(pageId: String, newNotebookId: String) {
        val page = pageDao.getPageById(pageId) ?: return
        val oldNotebookId = page.notebookId

        if (oldNotebookId == newNotebookId) return

        // Get the highest page number in the destination notebook
        val maxPageNumber = pageDao.getMaxPageNumber(newNotebookId) ?: 0
        val newPageNumber = maxPageNumber + 1

        // Move the page
        pageDao.movePageToNotebook(pageId, newNotebookId, newPageNumber)

        // Update both notebooks
        notebookDao.updateLastModifiedAt(oldNotebookId)
        notebookDao.updateLastModifiedAt(newNotebookId)
    }
}