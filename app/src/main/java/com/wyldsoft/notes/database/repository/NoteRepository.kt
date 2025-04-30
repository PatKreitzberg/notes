package com.wyldsoft.notes.database.repository

import com.wyldsoft.notes.database.dao.NoteDao
import com.wyldsoft.notes.database.dao.StrokeDao
import com.wyldsoft.notes.database.dao.StrokePointDao
import com.wyldsoft.notes.database.entity.NoteEntity
import com.wyldsoft.notes.database.entity.StrokeEntity
import com.wyldsoft.notes.database.entity.StrokePointEntity
import com.wyldsoft.notes.utils.Stroke
import com.wyldsoft.notes.utils.StrokePoint
import kotlinx.coroutines.flow.Flow
import java.util.Date
import android.content.Context


/**
 * Repository class for handling note-related database operations
 */
class NoteRepository(
    private val context: Context,
    private val noteDao: NoteDao,
    private val strokeDao: StrokeDao,
    private val strokePointDao: StrokePointDao
) {
    /**
     * Gets all notes as a Flow
     */
    fun getAllNotes(): Flow<List<NoteEntity>> {
        return noteDao.getAllNotes()
    }

    /**
     * Gets a note by ID
     */
    suspend fun getNoteById(noteId: String): NoteEntity? {
        return noteDao.getNoteById(noteId)
    }

    /**
     * Creates a new note
     */
    suspend fun createNote(
        id: String,
        title: String,
        width: Int,
        height: Int
    ): NoteEntity {
        val now = Date()
        val note = NoteEntity(
            id = id,
            title = title,
            createdAt = now,
            updatedAt = now,
            width = width,
            height = height
        )
        noteDao.insertNote(note)
        return note
    }

    /**
     * Updates a note's metadata
     */
    suspend fun updateNote(note: NoteEntity) {
        val updatedNote = note.copy(updatedAt = Date())
        noteDao.updateNote(updatedNote)

        // Register note change for syncing
        (context.applicationContext as? com.wyldsoft.notes.NotesApp)?.let { app ->
            app.syncManager.changeTracker.registerNoteChanged(note.id)
        }
    }

    /**
     * Deletes a note and all its strokes
     */
    suspend fun deleteNote(noteId: String) {
        // The foreign key constraint will automatically delete related strokes and points
        noteDao.deleteNoteById(noteId)
    }

    /**
     * Gets all strokes for a note
     */
    suspend fun getStrokesForNote(noteId: String): List<Stroke> {
        val strokeEntities = strokeDao.getStrokesForNote(noteId)
        return strokeEntities.map { strokeEntity ->
            val points = strokePointDao.getPointsForStroke(strokeEntity.id)
            convertToStroke(strokeEntity, points)
        }
    }

    /**
     * Saves strokes for a note
     */
    suspend fun saveStrokes(noteId: String, strokes: List<Stroke>) {
        strokes.forEach { stroke ->
            // Create stroke entity
            val strokeEntity = StrokeEntity(
                id = stroke.id,
                noteId = noteId,
                penName = stroke.pen.penName,
                size = stroke.size,
                color = stroke.color,
                top = stroke.top,
                bottom = stroke.bottom,
                left = stroke.left,
                right = stroke.right,
                createdAt = stroke.createdAt,
                updatedAt = stroke.updatedAt,
                createdScrollY = stroke.createdScrollY
            )
            strokeDao.insertStroke(strokeEntity)

            // Create point entities
            val pointEntities = stroke.points.mapIndexed { index, point ->
                StrokePointEntity(
                    strokeId = stroke.id,
                    x = point.x,
                    y = point.y,
                    pressure = point.pressure,
                    size = point.size,
                    tiltX = point.tiltX,
                    tiltY = point.tiltY,
                    timestamp = point.timestamp,
                    sequenceNumber = index
                )
            }
            strokePointDao.insertPoints(pointEntities)
            (context.applicationContext as? com.wyldsoft.notes.NotesApp)?.let { app ->
                app.syncManager.changeTracker.registerNoteChanged(noteId)
            }
        }

        // Update the note's updatedAt timestamp
        noteDao.getNoteById(noteId)?.let { note ->
            updateNote(note)
        }
    }

    /**
     * Deletes strokes by their IDs
     */
    suspend fun deleteStrokes(noteId: String, strokeIds: List<String>) {
        strokeDao.deleteStrokesByIds(strokeIds)
        // The foreign key constraint will automatically delete related points

        // Update the note's updatedAt timestamp
        noteDao.getNoteById(noteId)?.let { note ->
            updateNote(note)
        }

        (context.applicationContext as? com.wyldsoft.notes.NotesApp)?.let { app ->
            app.syncManager.changeTracker.registerNoteChanged(noteId)
        }
    }

    /**
     * Deletes all strokes for a note
     */
    suspend fun deleteStrokesForNote(noteId: String) {
        // Use the strokeDao to delete all strokes for the note
        strokeDao.deleteStrokesForNote(noteId)

        // The foreign key constraint will automatically delete related points
    }

    /**
     * Converts database entities to a Stroke object
     */
    private fun convertToStroke(
        strokeEntity: StrokeEntity,
        pointEntities: List<StrokePointEntity>
    ): Stroke {
        val points = pointEntities.map { point ->
            StrokePoint(
                x = point.x,
                y = point.y,
                pressure = point.pressure,
                size = point.size,
                tiltX = point.tiltX,
                tiltY = point.tiltY,
                timestamp = point.timestamp
            )
        }

        return Stroke(
            id = strokeEntity.id,
            size = strokeEntity.size,
            pen = com.wyldsoft.notes.utils.Pen.fromString(strokeEntity.penName),
            color = strokeEntity.color,
            top = strokeEntity.top,
            bottom = strokeEntity.bottom,
            left = strokeEntity.left,
            right = strokeEntity.right,
            points = points,
            pageId = strokeEntity.noteId,
            createdAt = strokeEntity.createdAt,
            updatedAt = strokeEntity.updatedAt,
            createdScrollY = strokeEntity.createdScrollY
        )
    }
}