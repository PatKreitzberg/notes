// Update app/src/main/java/com/wyldsoft/notes/settings/SettingsRepository.kt
package com.wyldsoft.notes.settings

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SettingsRepository(private val context: Context) {
    private val sharedPreferences = context.getSharedPreferences("notes_settings", Context.MODE_PRIVATE)
    val settings = mutableStateOf(loadSettings())

    private fun loadSettings(): SettingsModel {
        val allPrefs = sharedPreferences.all.mapValues { it.value.toString() }
        return SettingsModel.fromMap(allPrefs)
    }

    suspend fun saveSettings(settings: SettingsModel) {
        withContext(Dispatchers.IO) {
            with(sharedPreferences.edit()) {
                settings.toMap().forEach { (key, value) ->
                    putString(key, value)
                }
                apply()
            }
        }
        this.settings.value = settings
    }

    suspend fun updatePagination(enabled: Boolean) {
        val updatedSettings = settings.value.copy(isPaginationEnabled = enabled)
        saveSettings(updatedSettings)
    }

    suspend fun updatePaperSize(size: PaperSize) {
        val updatedSettings = settings.value.copy(paperSize = size)
        saveSettings(updatedSettings)
    }

    suspend fun updateTemplate(template: TemplateType) {
        val updatedSettings = settings.value.copy(template = template)
        saveSettings(updatedSettings)
    }

    fun getSettings(): SettingsModel {
        return settings.value
    }
}