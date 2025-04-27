package com.wyldsoft.notes.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing application settings
 * Using a single row approach with a constant ID for simplicity
 */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey
    val id: String = DEFAULT_SETTINGS_ID,
    val isPaginationEnabled: Boolean,
    val paperSizeName: String,
    val templateName: String
) {
    companion object {
        const val DEFAULT_SETTINGS_ID = "app_settings"
    }
}