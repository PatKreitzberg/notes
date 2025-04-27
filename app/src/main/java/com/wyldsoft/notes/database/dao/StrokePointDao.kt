package com.wyldsoft.notes.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wyldsoft.notes.database.entity.StrokePointEntity

/**
 * Data Access Object for the stroke_points table
 */
@Dao
interface StrokePointDao {
    @Query("SELECT * FROM stroke_points WHERE strokeId = :strokeId ORDER BY sequenceNumber")
    suspend fun getPointsForStroke(strokeId: String): List<StrokePointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<StrokePointEntity>)

    @Query("DELETE FROM stroke_points WHERE strokeId = :strokeId")
    suspend fun deletePointsForStroke(strokeId: String)

    @Query("DELETE FROM stroke_points WHERE strokeId IN (:strokeIds)")
    suspend fun deletePointsForStrokes(strokeIds: List<String>)
}