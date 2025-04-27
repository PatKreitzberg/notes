package com.wyldsoft.notes.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.wyldsoft.notes.database.entity.StrokeEntity

/**
 * Data Access Object for the strokes table
 */
@Dao
interface StrokeDao {
    @Query("SELECT * FROM strokes WHERE noteId = :noteId")
    suspend fun getStrokesForNote(noteId: String): List<StrokeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStroke(stroke: StrokeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStrokes(strokes: List<StrokeEntity>)

    @Delete
    suspend fun deleteStroke(stroke: StrokeEntity)

    @Query("DELETE FROM strokes WHERE id IN (:strokeIds)")
    suspend fun deleteStrokesByIds(strokeIds: List<String>)

    @Query("DELETE FROM strokes WHERE noteId = :noteId")
    suspend fun deleteStrokesForNote(noteId: String)
}