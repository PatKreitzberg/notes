// 1. Create FolderRepository.kt
package com.wyldsoft.notes.database.repository

import com.wyldsoft.notes.database.dao.FolderDao
import com.wyldsoft.notes.database.entity.FolderEntity
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.UUID

class FolderRepository(private val folderDao: FolderDao) {

    fun getRootFolders(): Flow<List<FolderEntity>> {
        return folderDao.getRootFolders()
    }

    fun getSubFolders(parentId: String): Flow<List<FolderEntity>> {
        return folderDao.getSubFolders(parentId)
    }

    suspend fun getFolderById(folderId: String): FolderEntity? {
        return folderDao.getFolderById(folderId)
    }

    suspend fun createFolder(name: String, parentId: String? = null): FolderEntity {
        val now = Date()
        val path = if (parentId != null) {
            val parent = folderDao.getFolderById(parentId)
            if (parent != null) {
                "${parent.path}/$name"
            } else {
                "/$name"
            }
        } else {
            "/$name"
        }

        val folder = FolderEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            path = path,
            parentId = parentId,
            createdAt = now,
            updatedAt = now
        )

        folderDao.insertFolder(folder)
        return folder
    }

    suspend fun updateFolder(folder: FolderEntity) {
        val updatedFolder = folder.copy(updatedAt = Date())
        folderDao.updateFolder(updatedFolder)
    }

    suspend fun deleteFolder(folderId: String) {
        folderDao.deleteFolderById(folderId)
    }
}
