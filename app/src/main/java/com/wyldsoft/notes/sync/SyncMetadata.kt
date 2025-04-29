// app/src/main/java/com/wyldsoft/notes/sync/SyncMetadata.kt
package com.wyldsoft.notes.sync

import org.json.JSONObject
import java.util.Date

/**
 * Metadata for synchronization
 */
class SyncMetadata {

    // Map of note ID to entry
    val noteEntries = mutableMapOf<String, NoteEntry>()

    // Last full sync time
    var lastSyncTime: Date = Date()

    /**
     * Metadata for a single note
     */
    data class NoteEntry(
        val id: String,
        val lastModified: Date,
        val lastSynced: Date
    )

    /**
     * Converts metadata to JSON
     */
    fun toJson(): String {
        val json = JSONObject()

        json.put("lastSyncTime", lastSyncTime.time)

        val entriesJson = JSONObject()
        for ((id, entry) in noteEntries) {
            val entryJson = JSONObject()
            entryJson.put("id", entry.id)
            entryJson.put("lastModified", entry.lastModified.time)
            entryJson.put("lastSynced", entry.lastSynced.time)

            entriesJson.put(id, entryJson)
        }

        json.put("notes", entriesJson)

        return json.toString()
    }

    companion object {
        /**
         * Creates metadata from JSON
         */
        fun fromJson(json: String): SyncMetadata {
            val result = SyncMetadata()
            val jsonObj = JSONObject(json)

            // Parse last sync time
            if (jsonObj.has("lastSyncTime")) {
                result.lastSyncTime = Date(jsonObj.getLong("lastSyncTime"))
            }

            // Parse note entries
            if (jsonObj.has("notes")) {
                val entriesJson = jsonObj.getJSONObject("notes")
                val keys = entriesJson.keys()

                while (keys.hasNext()) {
                    val id = keys.next()
                    val entryJson = entriesJson.getJSONObject(id)

                    result.noteEntries[id] = NoteEntry(
                        id = entryJson.getString("id"),
                        lastModified = Date(entryJson.getLong("lastModified")),
                        lastSynced = Date(entryJson.getLong("lastSynced"))
                    )
                }
            }

            return result
        }
    }
}