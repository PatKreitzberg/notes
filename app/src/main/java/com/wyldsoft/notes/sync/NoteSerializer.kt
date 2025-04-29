// app/src/main/java/com/wyldsoft/notes/sync/NoteSerializer.kt
package com.wyldsoft.notes.sync

import com.wyldsoft.notes.database.entity.NoteEntity
import com.wyldsoft.notes.utils.Pen
import com.wyldsoft.notes.utils.Stroke
import com.wyldsoft.notes.utils.StrokePoint
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

/**
 * Handles serialization and deserialization of notes and strokes
 */
object NoteSerializer {

    /**
     * Serializes a note and its strokes to JSON
     */
    fun serialize(note: NoteEntity, strokes: List<Stroke>): String {
        val json = JSONObject()

        // Note metadata
        json.put("id", note.id)
        json.put("title", note.title)
        json.put("createdAt", note.createdAt.time)
        json.put("updatedAt", note.updatedAt.time)
        json.put("width", note.width)
        json.put("height", note.height)
        json.put("version", 1) // For future compatibility

        // Serialize strokes
        val strokesArray = JSONArray()
        for (stroke in strokes) {
            val strokeJson = JSONObject()
            strokeJson.put("id", stroke.id)
            strokeJson.put("penName", stroke.pen.penName)
            strokeJson.put("size", stroke.size)
            strokeJson.put("color", stroke.color)
            strokeJson.put("top", stroke.top)
            strokeJson.put("bottom", stroke.bottom)
            strokeJson.put("left", stroke.left)
            strokeJson.put("right", stroke.right)
            strokeJson.put("createdAt", stroke.createdAt.time)
            strokeJson.put("updatedAt", stroke.updatedAt.time)
            strokeJson.put("createdScrollY", stroke.createdScrollY)

            // Serialize points
            val pointsArray = JSONArray()
            for (point in stroke.points) {
                val pointJson = JSONObject()
                pointJson.put("x", point.x)
                pointJson.put("y", point.y)
                pointJson.put("pressure", point.pressure)
                pointJson.put("size", point.size)
                pointJson.put("tiltX", point.tiltX)
                pointJson.put("tiltY", point.tiltY)
                pointJson.put("timestamp", point.timestamp)

                pointsArray.put(pointJson)
            }

            strokeJson.put("points", pointsArray)
            strokesArray.put(strokeJson)
        }

        json.put("strokes", strokesArray)

        return json.toString()
    }

    /**
     * Deserializes a note from JSON
     */
    fun deserialize(json: String): NoteEntity {
        val jsonObj = JSONObject(json)

        return NoteEntity(
            id = jsonObj.getString("id"),
            title = jsonObj.getString("title"),
            createdAt = Date(jsonObj.getLong("createdAt")),
            updatedAt = Date(jsonObj.getLong("updatedAt")),
            width = jsonObj.getInt("width"),
            height = jsonObj.getInt("height")
        )
    }

    /**
     * Deserializes strokes from JSON
     */
    fun deserializeStrokes(json: String, noteId: String): List<Stroke> {
        val jsonObj = JSONObject(json)
        val strokesArray = jsonObj.getJSONArray("strokes")
        val result = mutableListOf<Stroke>()

        for (i in 0 until strokesArray.length()) {
            val strokeJson = strokesArray.getJSONObject(i)

            // Deserialize points
            val pointsArray = strokeJson.getJSONArray("points")
            val points = mutableListOf<StrokePoint>()

            for (j in 0 until pointsArray.length()) {
                val pointJson = pointsArray.getJSONObject(j)

                points.add(
                    StrokePoint(
                        x = pointJson.getDouble("x").toFloat(),
                        y = pointJson.getDouble("y").toFloat(),
                        pressure = pointJson.getDouble("pressure").toFloat(),
                        size = pointJson.getDouble("size").toFloat(),
                        tiltX = pointJson.getInt("tiltX"),
                        tiltY = pointJson.getInt("tiltY"),
                        timestamp = pointJson.getLong("timestamp")
                    )
                )
            }

            // Create stroke
            result.add(
                Stroke(
                    id = strokeJson.getString("id"),
                    size = strokeJson.getDouble("size").toFloat(),
                    pen = Pen.fromString(strokeJson.getString("penName")),
                    color = strokeJson.getInt("color"),
                    top = strokeJson.getDouble("top").toFloat(),
                    bottom = strokeJson.getDouble("bottom").toFloat(),
                    left = strokeJson.getDouble("left").toFloat(),
                    right = strokeJson.getDouble("right").toFloat(),
                    points = points,
                    pageId = noteId,
                    createdAt = Date(strokeJson.getLong("createdAt")),
                    updatedAt = Date(strokeJson.getLong("updatedAt")),
                    createdScrollY = strokeJson.getDouble("createdScrollY").toFloat()
                )
            )
        }

        return result
    }
}