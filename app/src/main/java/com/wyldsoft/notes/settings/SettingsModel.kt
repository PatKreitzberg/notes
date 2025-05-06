// Update app/src/main/java/com/wyldsoft/notes/settings/SettingsModel.kt
package com.wyldsoft.notes.settings

enum class PaperSize {
    LETTER, A4
}

enum class TemplateType {
    BLANK, GRID, RULED
}

// Change to a data class with proper constructor parameters
data class SettingsModel(
    var isPaginationEnabled: Boolean = true,
    var paperSize: PaperSize = PaperSize.LETTER,
    var template: TemplateType = TemplateType.BLANK
) {
    fun toMap(): Map<String, String> {
        return mapOf(
            "pagination" to isPaginationEnabled.toString(),
            "paperSize" to paperSize.name,
            "template" to template.name
        )
    }

    companion object {
        fun fromMap(map: Map<String, String>): SettingsModel {
            return SettingsModel(
                isPaginationEnabled = map["pagination"]?.toBoolean() ?: true,
                paperSize = map["paperSize"]?.let { PaperSize.valueOf(it) } ?: PaperSize.LETTER,
                template = map["template"]?.let { TemplateType.valueOf(it) } ?: TemplateType.BLANK
            )
        }
    }
}