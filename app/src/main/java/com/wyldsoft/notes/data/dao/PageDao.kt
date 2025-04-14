package com.wyldsoft.notes.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.wyldsoft.notes.data.entity.Page
import com.wyldsoft.notes.data.relation.PageWithStrokes
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Data Access Object for the [Page] entity.
 *
 * Provides methods to interact with the pages table in the database.
 */
@Dao
interface PageDao {
    /**
     * Insert a new page into the database.
     *
     * @param page The page to insert
     * @return The ID of the inserted page
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: Page): Long

    /**
     * Insert multiple pages into the database.
     *
     * @param pages The list of pages to insert
     * @return The list of IDs of the inserted pages
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<Page>): List<Long>

    /**
     * Update an existing page in the database.
     *
     * @param page The page to update
     */
    @Update
    suspend fun updatePage(page: Page)

    /**
     * Update the lastModifiedAt field of a page.
     *
     * @param pageId ID of the page to update
     * @param lastModifiedAt New lastModifiedAt value
     */
    @Query("UPDATE pages SET lastModifiedAt = :lastModifiedAt WHERE id = :pageId")
    suspend fun updateLastModifiedAt(pageId: String, lastModifiedAt: Date = Date())

    /**
     * Delete a page from the database.
     *
     * @param page The page to delete
     */
    @Delete
    suspend fun deletePage(page: Page)

    /**
     * Delete a page by its ID.
     *
     * @param pageId ID of the page to delete
     */
    @Query("DELETE FROM pages WHERE id = :pageId")
    suspend fun deletePageById(pageId: String)

    /**
     * Get all pages for a specific notebook.
     *
     * @param notebookId ID of the notebook
     * @return List of pages in the notebook
     */
    @Query("SELECT * FROM pages WHERE notebookId = :notebookId ORDER BY pageNumber ASC")
    suspend fun getPagesForNotebook(notebookId: String): List<Page>

    /**
     * Get all pages for a specific notebook as a Flow.
     *
     * @param notebookId ID of the notebook
     * @return Flow of pages in the notebook
     */
    @Query("SELECT * FROM pages WHERE notebookId = :notebookId ORDER BY pageNumber ASC")
    fun getPagesForNotebookFlow(notebookId: String): Flow<List<Page>>

    /**
     * Get a page by its ID.
     *
     * @param pageId ID of the page to retrieve
     * @return The page with the given ID, or null if not found
     */
    @Query("SELECT * FROM pages WHERE id = :pageId")
    suspend fun getPageById(pageId: String): Page?

    /**
     * Get a page by its ID as a Flow.
     *
     * @param pageId ID of the page to retrieve
     * @return Flow of the page with the given ID
     */
    @Query("SELECT * FROM pages WHERE id = :pageId")
    fun getPageByIdFlow(pageId: String): Flow<Page?>

    /**
     * Get a page with all its strokes.
     *
     * @param pageId ID of the page to retrieve
     * @return The page with all its strokes
     */
    @Transaction
    @Query("SELECT * FROM pages WHERE id = :pageId")
    suspend fun getPageWithStrokes(pageId: String): PageWithStrokes?

    /**
     * Get a page with all its strokes as a Flow.
     *
     * @param pageId ID of the page to retrieve
     * @return Flow of the page with all its strokes
     */
    @Transaction
    @Query("SELECT * FROM pages WHERE id = :pageId")
    fun getPageWithStrokesFlow(pageId: String): Flow<PageWithStrokes?>

    /**
     * Get the highest page number for a specific notebook.
     *
     * @param notebookId ID of the notebook
     * @return The highest page number in the notebook
     */
    @Query("SELECT MAX(pageNumber) FROM pages WHERE notebookId = :notebookId")
    suspend fun getMaxPageNumber(notebookId: String): Int?

    /**
     * Update page numbers for a specific notebook.
     * Increments page numbers greater than or equal to the specified starting point.
     *
     * @param notebookId ID of the notebook
     * @param startingPageNumber The page number to start incrementing from
     */
    @Query("UPDATE pages SET pageNumber = pageNumber + 1 WHERE notebookId = :notebookId AND pageNumber >= :startingPageNumber")
    suspend fun incrementPageNumbers(notebookId: String, startingPageNumber: Int)

    /**
     * Update the notebook ID of a page (for moving pages between notebooks).
     *
     * @param pageId ID of the page to move
     * @param newNotebookId ID of the destination notebook
     * @param newPageNumber New page number in the destination notebook
     */
    @Query("UPDATE pages SET notebookId = :newNotebookId, pageNumber = :newPageNumber WHERE id = :pageId")
    suspend fun movePageToNotebook(pageId: String, newNotebookId: String, newPageNumber: Int)
}