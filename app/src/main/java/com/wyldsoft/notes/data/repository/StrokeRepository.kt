package com.wyldsoft.notes.data.repository

import com.wyldsoft.notes.data.dao.PageDao
import com.wyldsoft.notes.data.dao.StrokeDao
import com.wyldsoft.notes.data.dao.StrokePointDao
import com.wyldsoft.notes.data.entity.StrokeEntity
import com.wyldsoft.notes.data.entity.StrokePointEntity
import com.wyldsoft.notes.data.relation.StrokeWithPoints
import com.wyldsoft.notes.utils.Pen
import com.wyldsoft.notes.utils.Stroke
import com.wyldsoft.notes.utils.StrokePoint
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.UUID

/**
 * Repository for strokes and their points.
 *
 * Provides a clean API for interacting with strokes and points in the database.
 *
 * @property strokeDao Data access object for strokes
 * @property strokePointDao Data access object for stroke points
 * @property pageDao Data access object for pages
 */
class StrokeRepository(
    private val strokeDao: StrokeDao,
    private val strokePointDao: StrokePointDao,
    private val pageDao: PageDao
) {
    /**
     * Get all strokes for a specific page.
     *
     * @param pageId ID of the page
     * @return List of strokes in the page
     */
    suspend fun getStrokesForPage(pageId: String): List<StrokeEntity> {
        return strokeDao.getStrokesForPage(pageId)
    }

    /**
     * Get all strokes for a specific page as a Flow.
     *
     * @param pageId ID of the page
     * @return Flow of strokes in the page
     */
    fun getStrokesForPageFlow(pageId: String): Flow<List<StrokeEntity>> {
        return strokeDao.getStrokesForPageFlow(pageId)
    }

    /**
     * Get strokes with all their points for a specific page.
     *
     * @param pageId ID of the page
     * @return List of strokes with their points
     */
    suspend fun getStrokesWithPointsForPage(pageId: String): List<StrokeWithPoints> {
        return strokeDao.getStrokesWithPointsForPage(pageId)
    }

    /**
     * Get strokes with all their points for a specific page as a Flow.
     *
     * @param pageId ID of the page
     * @return Flow of strokes with their points
     */
    fun getStrokesWithPointsForPageFlow(pageId: String): Flow<List<StrokeWithPoints>> {
        return strokeDao.getStrokesWithPointsForPageFlow(pageId)
    }

    /**
     * Convert a database StrokeWithPoints to a domain Stroke object.
     *
     * @param strokeWithPoints The database stroke with points
     * @return The domain stroke object
     */
    fun convertToStroke(strokeWithPoints: StrokeWithPoints): Stroke {
        val entity = strokeWithPoints.stroke
        val points = strokeWithPoints.points.sortedBy { it.sequence }.map { pointEntity ->
            StrokePoint(
                x = pointEntity.x,
                y = pointEntity.y,
                pressure = pointEntity.pressure,
                size = pointEntity.size,
                tiltX = pointEntity.tiltX,
                tiltY = pointEntity.tiltY,
                timestamp = pointEntity.timestamp
            )
        }

        return Stroke(
            id = entity.id,
            size = entity.size,
            pen = Pen.fromString(entity.penName),
            color = entity.color,
            top = entity.top,
            bottom = entity.bottom,
            left = entity.left,
            right = entity.right,
            points = points,
            pageId = entity.pageId,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    /**
     * Convert a domain Stroke object to database entities (StrokeEntity and StrokePointEntity).
     *
     * @param stroke The domain stroke object
     * @return Pair of stroke entity and list of stroke point entities
     */
    fun convertFromStroke(stroke: Stroke): Pair<StrokeEntity, List<StrokePointEntity>> {
        val strokeEntity = StrokeEntity(
            id = stroke.id,
            pageId = stroke.pageId,
            size = stroke.size,
            penName = stroke.pen.penName,
            color = stroke.color,
            top = stroke.top,
            bottom = stroke.bottom,
            left = stroke.left,
            right = stroke.right,
            createdAt = stroke.createdAt,
            updatedAt = stroke.updatedAt
        )

        val pointEntities = stroke.points.mapIndexed { index, point ->
            StrokePointEntity(
                id = UUID.randomUUID().toString(),
                strokeId = stroke.id,
                x = point.x,
                y = point.y,
                pressure = point.pressure,
                size = point.size,
                tiltX = point.tiltX,
                tiltY = point.tiltY,
                timestamp = point.timestamp,
                sequence = index
            )
        }

        return strokeEntity to pointEntities
    }

    /**
     * Add a stroke to a page.
     *
     * @param stroke The stroke to add
     */
    suspend fun addStroke(stroke: Stroke) {
        val (strokeEntity, pointEntities) = convertFromStroke(stroke)

        strokeDao.insertStroke(strokeEntity)
        strokePointDao.insertStrokePoints(pointEntities)

        // Update page
        val page = pageDao.getPageById(stroke.pageId)
        page?.let {
            pageDao.updateLastModifiedAt(it.id)
        }
    }

    /**
     * Add multiple strokes to a page.
     *
     * @param strokes The list of strokes to add
     */
    suspend fun addStrokes(strokes: List<Stroke>) {
        if (strokes.isEmpty()) return

        println("StrokeRepository.addStrokes: Adding ${strokes.size} strokes")

        val allEntities = strokes.map { convertFromStroke(it) }
        val strokeEntities = allEntities.map { it.first }
        val pointEntitiesLists = allEntities.map { it.second }

        // Insert strokes
        strokeDao.insertStrokes(strokeEntities)

        // Insert points for each stroke
        for (pointEntities in pointEntitiesLists) {
            if (pointEntities.isNotEmpty()) {
                strokePointDao.insertStrokePoints(pointEntities)
                println("Inserted ${pointEntities.size} points for stroke ${pointEntities.first().strokeId}")
            }
        }

        // Update page
        val pageId = strokes.firstOrNull()?.pageId ?: return
        val page = pageDao.getPageById(pageId)
        page?.let {
            pageDao.updateLastModifiedAt(it.id)
        }

        println("StrokeRepository.addStrokes: Completed adding strokes to database")
    }

    /**
     * Delete strokes.
     *
     * @param strokeIds The IDs of the strokes to delete
     */
    suspend fun deleteStrokes(strokeIds: List<String>) {
        if (strokeIds.isEmpty()) return

        // Get one stroke to identify the page
        val stroke = strokeDao.getStrokeById(strokeIds.first())

        // Delete the stroke points first
        strokePointDao.deleteStrokePointsForStrokes(strokeIds)

        // Delete the strokes
        strokeDao.deleteStrokesByIds(strokeIds)

        // Update page
        val pageId = stroke?.pageId ?: return
        val page = pageDao.getPageById(pageId)
        page?.let {
            pageDao.updateLastModifiedAt(it.id)
        }
    }

    /**
     * Delete all strokes for a specific page.
     *
     * @param pageId ID of the page
     */
    suspend fun deleteStrokesForPage(pageId: String) {
        strokeDao.deleteStrokesForPage(pageId)
        pageDao.updateLastModifiedAt(pageId)
    }

    /**
     * Get all domain Stroke objects for a specific page.
     *
     * @param pageId ID of the page
     * @return List of domain Stroke objects
     */
    suspend fun getDomainStrokesForPage(pageId: String): List<Stroke> {
        println("StrokeRepository.getDomainStrokesForPage: Retrieving strokes for page $pageId")

        // Get all strokes with their points
        val strokesWithPoints = strokeDao.getStrokesWithPointsForPage(pageId)
        println("StrokeRepository.getDomainStrokesForPage: Found ${strokesWithPoints.size} strokes in database")

        // Convert them to domain Stroke objects
        val domainStrokes = strokesWithPoints.map { convertToStroke(it) }

        // Log some details for debugging
        domainStrokes.forEachIndexed { index, stroke ->
            println("Stroke ${index+1}: id=${stroke.id}, points=${stroke.points.size}, bounds=(${stroke.left},${stroke.top},${stroke.right},${stroke.bottom})")
        }

        return domainStrokes
    }
}