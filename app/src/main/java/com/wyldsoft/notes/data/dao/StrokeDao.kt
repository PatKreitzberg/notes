package com.wyldsoft.notes.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.wyldsoft.notes.data.entity.StrokeEntity
import com.wyldsoft.notes.data.relation.StrokeWithPoints
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the [StrokeEntity] entity.
 *
 * Provides methods to interact with the strokes table in the database.
 */
@Dao
interface StrokeDao {
    /**
     * Insert a new stroke into the database.
     *
     * @param stroke The stroke to insert
     * @return The ID of the inserted stroke
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStroke(stroke: StrokeEntity): Long

    /**
     * Insert multiple strokes into the database.
     *
     * @param strokes The list of strokes to insert
     * @return The list of IDs of the inserted strokes
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStrokes(strokes: List<StrokeEntity>): List<Long>

    /**
     * Update an existing stroke in the database.
     *
     * @param stroke The stroke to update
     */
    @Update
    suspend fun updateStroke(stroke: StrokeEntity)

    /**
     * Delete a stroke from the database.
     *
     * @param stroke The stroke to delete
     */
    @Delete
    suspend fun deleteStroke(stroke: StrokeEntity)

    /**
     * Delete a stroke by its ID.
     *
     * @param strokeId ID of the stroke to delete
     */
    @Query("DELETE FROM strokes WHERE id = :strokeId")
    suspend fun deleteStrokeById(strokeId: String)

    /**
     * Delete multiple strokes by their IDs.
     *
     * @param strokeIds List of stroke IDs to delete
     */
    @Query("DELETE FROM strokes WHERE id IN (:strokeIds)")
    suspend fun deleteStrokesByIds(strokeIds: List<String>)

    /**
     * Delete all strokes for a specific page.
     *
     * @param pageId ID of the page
     */
    @Query("DELETE FROM strokes WHERE pageId = :pageId")
    suspend fun deleteStrokesForPage(pageId: String)

    /**
     * Get all strokes for a specific page.
     *
     * @param pageId ID of the page
     * @return List of strokes in the page
     */
    @Query("SELECT * FROM strokes WHERE pageId = :pageId ORDER BY createdAt ASC")
    suspend fun getStrokesForPage(pageId: String): List<StrokeEntity>

    /**
     * Get all strokes for a specific page as a Flow.
     *
     * @param pageId ID of the page
     * @return Flow of strokes in the page
     */
    @Query("SELECT * FROM strokes WHERE pageId = :pageId ORDER BY createdAt ASC")
    fun getStrokesForPageFlow(pageId: String): Flow<List<StrokeEntity>>

    /**
     * Get a stroke by its ID.
     *
     * @param strokeId ID of the stroke to retrieve
     * @return The stroke with the given ID, or null if not found
     */
    @Query("SELECT * FROM strokes WHERE id = :strokeId")
    suspend fun getStrokeById(strokeId: String): StrokeEntity?

    /**
     * Get strokes by their IDs.
     *
     * @param strokeIds List of stroke IDs to retrieve
     * @return List of strokes with the given IDs
     */
    @Query("SELECT * FROM strokes WHERE id IN (:strokeIds)")
    suspend fun getStrokesByIds(strokeIds: List<String>): List<StrokeEntity>

    /**
     * Get a stroke with all its points.
     *
     * @param strokeId ID of the stroke to retrieve
     * @return The stroke with all its points
     */
    @Transaction
    @Query("SELECT * FROM strokes WHERE id = :strokeId")
    suspend fun getStrokeWithPoints(strokeId: String): StrokeWithPoints?

    /**
     * Get strokes with all their points for a specific page.
     *
     * @param pageId ID of the page
     * @return List of strokes with their points
     */
    @Transaction
    @Query("SELECT * FROM strokes WHERE pageId = :pageId ORDER BY createdAt ASC")
    suspend fun getStrokesWithPointsForPage(pageId: String): List<StrokeWithPoints>

    /**
     * Get strokes with all their points for a specific page as a Flow.
     *
     * @param pageId ID of the page
     * @return Flow of strokes with their points
     */
    @Transaction
    @Query("SELECT * FROM strokes WHERE pageId = :pageId ORDER BY createdAt ASC")
    fun getStrokesWithPointsForPageFlow(pageId: String): Flow<List<StrokeWithPoints>>
}