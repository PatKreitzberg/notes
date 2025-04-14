package com.wyldsoft.notes.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.wyldsoft.notes.data.entity.Notebook
import com.wyldsoft.notes.data.relation.NotebookWithPages
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Data Access Object for the [Notebook] entity.
 *
 * Provides methods to interact with the notebooks table in the database.
 */
@Dao
interface NotebookDao {
    /**
     * Insert a new notebook into the database.
     *
     * @param notebook The notebook to insert
     * @return The ID of the inserted notebook
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotebook(notebook: Notebook): Long

    /**
     * Update an existing notebook in the database.
     *
     * @param notebook The notebook to update
     */
    @Update
    suspend fun updateNotebook(notebook: Notebook)

    /**
     * Update the lastModifiedAt field of a notebook.
     *
     * @param notebookId ID of the notebook to update
     * @param lastModifiedAt New lastModifiedAt value
     */
    @Query("UPDATE notebooks SET lastModifiedAt = :lastModifiedAt WHERE id = :notebookId")
    suspend fun updateLastModifiedAt(notebookId: String, lastModifiedAt: Date = Date())

    /**
     * Delete a notebook from the database.
     *
     * @param notebook The notebook to delete
     */
    @Delete
    suspend fun deleteNotebook(notebook: Notebook)

    /**
     * Delete a notebook by its ID.
     *
     * @param notebookId ID of the notebook to delete
     */
    @Query("DELETE FROM notebooks WHERE id = :notebookId")
    suspend fun deleteNotebookById(notebookId: String)

    /**
     * Get all notebooks as a Flow.
     *
     * @return Flow of all notebooks
     */
    @Query("SELECT * FROM notebooks ORDER BY lastModifiedAt DESC")
    fun getAllNotebooks(): Flow<List<Notebook>>

    /**
     * Get a notebook by its ID.
     *
     * @param notebookId ID of the notebook to retrieve
     * @return The notebook with the given ID, or null if not found
     */
    @Query("SELECT * FROM notebooks WHERE id = :notebookId")
    suspend fun getNotebookById(notebookId: String): Notebook?

    /**
     * Get a notebook by its ID as a Flow.
     *
     * @param notebookId ID of the notebook to retrieve
     * @return Flow of the notebook with the given ID
     */
    @Query("SELECT * FROM notebooks WHERE id = :notebookId")
    fun getNotebookByIdFlow(notebookId: String): Flow<Notebook?>

    /**
     * Get a notebook with all its pages.
     *
     * @param notebookId ID of the notebook to retrieve
     * @return The notebook with all its pages
     */
    @Transaction
    @Query("SELECT * FROM notebooks WHERE id = :notebookId")
    suspend fun getNotebookWithPages(notebookId: String): NotebookWithPages?

    /**
     * Get all notebooks with their pages.
     *
     * @return List of all notebooks with their pages
     */
    @Transaction
    @Query("SELECT * FROM notebooks ORDER BY lastModifiedAt DESC")
    fun getAllNotebooksWithPages(): Flow<List<NotebookWithPages>>

    /**
     * Get the page count for a specific notebook.
     *
     * @param notebookId ID of the notebook
     * @return The number of pages in the notebook
     */
    @Query("SELECT COUNT(*) FROM pages WHERE notebookId = :notebookId")
    suspend fun getPageCount(notebookId: String): Int
}