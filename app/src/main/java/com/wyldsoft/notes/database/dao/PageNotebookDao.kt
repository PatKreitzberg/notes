// 3. Create PageNotebookDao.kt
package com.wyldsoft.notes.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.wyldsoft.notes.database.entity.NoteEntity
import com.wyldsoft.notes.database.entity.NotebookEntity
import com.wyldsoft.notes.database.entity.PageNotebookJoin
import kotlinx.coroutines.flow.Flow

@Dao
interface PageNotebookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPageToNotebook(join: PageNotebookJoin)

    @Query("DELETE FROM page_notebook_join WHERE pageId = :pageId AND notebookId = :notebookId")
    suspend fun removePageFromNotebook(pageId: String, notebookId: String)

    @Query("DELETE FROM page_notebook_join WHERE pageId = :pageId")
    suspend fun removePageFromAllNotebooks(pageId: String)

    @Query("SELECT * FROM notes INNER JOIN page_notebook_join ON notes.id = page_notebook_join.pageId WHERE page_notebook_join.notebookId = :notebookId ORDER BY notes.updatedAt DESC")
    fun getPagesInNotebook(notebookId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notebooks INNER JOIN page_notebook_join ON notebooks.id = page_notebook_join.notebookId WHERE page_notebook_join.pageId = :pageId")
    fun getNotebooksContainingPage(pageId: String): Flow<List<NotebookEntity>>

    @Query("SELECT COUNT(*) FROM page_notebook_join WHERE pageId = :pageId")
    suspend fun getNotebookCountForPage(pageId: String): Int
}