package com.wyldsoft.notes.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wyldsoft.notes.data.entity.StrokePointEntity

/**
 * Data Access Object for the [StrokePointEntity] entity.
 *
 * Provides methods to interact with the stroke_points table in the database.
 */
@Dao
interface StrokePointDao {
    /**
     * Insert a new stroke point into the database.
     *
     * @param strokePoint The stroke point to insert
     * @return The ID of the inserted stroke point
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStrokePoint(strokePoint: StrokePointEntity): Long

    /**
     * Insert multiple stroke points into the database.
     *
     * @param strokePoints The list of stroke points to insert
     * @return The list of IDs of the inserted stroke points
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStrokePoints(strokePoints: List<StrokePointEntity>): List<Long>

    /**
     * Get all stroke points for a specific stroke.
     *
     * @param strokeId ID of the stroke
     * @return List of stroke points in the stroke
     */
    @Query("SELECT * FROM stroke_points WHERE strokeId = :strokeId ORDER BY sequence ASC")
    suspend fun getStrokePointsForStroke(strokeId: String): List<StrokePointEntity>

    /**
     * Delete all stroke points for a specific stroke.
     *
     * @param strokeId ID of the stroke
     */
    @Query("DELETE FROM stroke_points WHERE strokeId = :strokeId")
    suspend fun deleteStrokePointsForStroke(strokeId: String)

    /**
     * Delete all stroke points for multiple strokes.
     *
     * @param strokeIds List of stroke IDs
     */
    @Query("DELETE FROM stroke_points WHERE strokeId IN (:strokeIds)")
    suspend fun deleteStrokePointsForStrokes(strokeIds: List<String>)
}