package com.wyldsoft.notes.database.repository

import com.wyldsoft.notes.database.dao.PageNotebookDao
import com.wyldsoft.notes.database.entity.NoteEntity
import com.wyldsoft.notes.database.entity.NotebookEntity
import com.wyldsoft.notes.database.entity.PageNotebookJoin
import kotlinx.coroutines.flow.Flow
import java.util.Date

class PageNotebookRepository(private val pageNotebookDao: PageNotebookDao) {

    suspend fun addPageToNotebook(pageId: String, notebookId: String) {
        val join = PageNotebookJoin(
            pageId = pageId,
            notebookId = notebookId,
            addedAt = Date()
        )
        pageNotebookDao.addPageToNotebook(join)
    }

    suspend fun removePageFromNotebook(pageId: String, notebookId: String) {
        pageNotebookDao.removePageFromNotebook(pageId, notebookId)
    }

    suspend fun getNotebooksContainingPageSync(pageId: String): List<NotebookEntity> {
        return pageNotebookDao.getNotebooksContainingPageSync(pageId)
    }

    suspend fun removePageFromAllNotebooks(pageId: String) {
        pageNotebookDao.removePageFromAllNotebooks(pageId)
    }

    fun getPagesInNotebook(notebookId: String): Flow<List<NoteEntity>> {
        return pageNotebookDao.getPagesInNotebook(notebookId)
    }

    fun getNotebooksContainingPage(pageId: String): Flow<List<NotebookEntity>> {
        return pageNotebookDao.getNotebooksContainingPage(pageId)
    }

    suspend fun getNotebookCountForPage(pageId: String): Int {
        return pageNotebookDao.getNotebookCountForPage(pageId)
    }
}