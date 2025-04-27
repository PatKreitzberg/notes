package com.wyldsoft.notes.settings

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import com.wyldsoft.notes.database.NotesDatabase
import com.wyldsoft.notes.database.dao.SettingsDao
import com.wyldsoft.notes.database.entity.SettingsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Repository class for handling settings-related operations
 * Uses Room database for storage
 */
class SettingsRepository(
    private val context: Context,
    private val coroutineScope: CoroutineScope? = null,
    private val settingsDao: SettingsDao? = null
) {
    // Use the provided DAO or get one from the database
    private val dao = settingsDao ?: NotesDatabase.getDatabase(context).settingsDao()

    // Cached settings as StateFlow for reactive UI updates
    val settings: StateFlow<SettingsModel> = if (coroutineScope != null) {
        dao.getSettings()
            .map { entity ->
                if (entity == null) {
                    // Create default settings if none exist
                    coroutineScope.launch {
                        val defaultSettings = createDefaultSettings()
                        defaultSettings
                    }
                    SettingsModel() // Return default while saving
                } else {
                    convertEntityToModel(entity)
                }
            }
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.Eagerly,
                initialValue = SettingsModel()
            )
    } else {
        // For backward compatibility, use a simple StateFlow
        MutableStateFlow(SettingsModel())
    }

    /**
     * Creates default settings and saves them to the database
     */
    private suspend fun createDefaultSettings(): SettingsModel {
        val defaultSettings = SettingsModel(
            isPaginationEnabled = true,
            paperSize = PaperSize.LETTER,
            template = TemplateType.BLANK
        )
        saveSettings(defaultSettings)
        return defaultSettings
    }

    /**
     * Saves settings to the database
     */
    suspend fun saveSettings(settings: SettingsModel) {
        withContext(Dispatchers.IO) {
            val entity = convertModelToEntity(settings)
            dao.insertSettings(entity)
        }
    }

    /**
     * Updates pagination setting
     */
    suspend fun updatePagination(enabled: Boolean) {
        val updatedSettings = settings.value.copy(isPaginationEnabled = enabled)
        saveSettings(updatedSettings)
    }

    /**
     * Updates paper size setting
     */
    suspend fun updatePaperSize(size: PaperSize) {
        val updatedSettings = settings.value.copy(paperSize = size)
        saveSettings(updatedSettings)
    }

    /**
     * Updates template setting
     */
    suspend fun updateTemplate(template: TemplateType) {
        val updatedSettings = settings.value.copy(template = template)
        saveSettings(updatedSettings)
    }

    /**
     * Gets current settings
     */
    fun getSettings(): SettingsModel {
        return settings.value
    }

    /**
     * Converts database entity to model object
     */
    private fun convertEntityToModel(entity: SettingsEntity): SettingsModel {
        return SettingsModel(
            isPaginationEnabled = entity.isPaginationEnabled,
            paperSize = try {
                PaperSize.valueOf(entity.paperSizeName)
            } catch (e: Exception) {
                PaperSize.LETTER
            },
            template = try {
                TemplateType.valueOf(entity.templateName)
            } catch (e: Exception) {
                TemplateType.BLANK
            }
        )
    }

    /**
     * Converts model object to database entity
     */
    private fun convertModelToEntity(model: SettingsModel): SettingsEntity {
        return SettingsEntity(
            id = SettingsEntity.DEFAULT_SETTINGS_ID,
            isPaginationEnabled = model.isPaginationEnabled,
            paperSizeName = model.paperSize.name,
            templateName = model.template.name
        )
    }
}