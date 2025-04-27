package com.wyldsoft.notes.database.util

import androidx.room.TypeConverter
import com.wyldsoft.notes.settings.PaperSize
import com.wyldsoft.notes.settings.TemplateType
import com.wyldsoft.notes.utils.Pen
import java.util.Date

/**
 * Type converters for Room database
 * Handles conversion between complex types and primitive types that can be stored in SQLite
 */
class Converters {
    // Date converters
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    // Enum converters
    @TypeConverter
    fun fromPenName(value: String): Pen {
        return Pen.fromString(value)
    }

    @TypeConverter
    fun penToPenName(pen: Pen): String {
        return pen.penName
    }

    @TypeConverter
    fun fromPaperSizeName(value: String): PaperSize {
        return try {
            PaperSize.valueOf(value)
        } catch (e: IllegalArgumentException) {
            PaperSize.LETTER // Default value
        }
    }

    @TypeConverter
    fun paperSizeToName(paperSize: PaperSize): String {
        return paperSize.name
    }

    @TypeConverter
    fun fromTemplateName(value: String): TemplateType {
        return try {
            TemplateType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            TemplateType.BLANK // Default value
        }
    }

    @TypeConverter
    fun templateTypeToName(templateType: TemplateType): String {
        return templateType.name
    }
}