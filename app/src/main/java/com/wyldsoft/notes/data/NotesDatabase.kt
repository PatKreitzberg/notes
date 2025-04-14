package com.wyldsoft.notes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.wyldsoft.notes.data.dao.NotebookDao
import com.wyldsoft.notes.data.dao.PageDao
import com.wyldsoft.notes.data.dao.StrokeDao
import com.wyldsoft.notes.data.dao.StrokePointDao
import com.wyldsoft.notes.data.entity.Notebook
import com.wyldsoft.notes.data.entity.Page
import com.wyldsoft.notes.data.entity.StrokeEntity
import com.wyldsoft.notes.data.entity.StrokePointEntity
import java.util.Date

/**
 * Type converters for Room database.
 *
 * Provides conversion methods between Java types and SQLite types
 * that Room cannot automatically convert.
 */
class Converters {
    /**
     * Convert Date to Long for storage in SQLite.
     *
     * @param date The Date to convert
     * @return The timestamp as Long
     */
    @TypeConverter
    fun fromDate(date: Date?): Long? {
        return date?.time
    }

    /**
     * Convert Long to Date when reading from SQLite.
     *
     * @param timestamp The timestamp as Long
     * @return The converted Date
     */
    @TypeConverter
    fun toDate(timestamp: Long?): Date? {
        return timestamp?.let { Date(it) }
    }
}

/**
 * Main database class for the Notes application.
 *
 * This is the central access point for database operations.
 */
@Database(
    entities = [
        Notebook::class,
        Page::class,
        StrokeEntity::class,
        StrokePointEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class NotesDatabase : RoomDatabase() {
    /**
     * Access to Notebook DAO.
     */
    abstract fun notebookDao(): NotebookDao

    /**
     * Access to Page DAO.
     */
    abstract fun pageDao(): PageDao

    /**
     * Access to Stroke DAO.
     */
    abstract fun strokeDao(): StrokeDao

    /**
     * Access to StrokePoint DAO.
     */
    abstract fun strokePointDao(): StrokePointDao

    companion object {
        // Singleton instance
        @Volatile
        private var INSTANCE: NotesDatabase? = null

        /**
         * Get or create the database instance.
         *
         * @param context Application context
         * @return The NotesDatabase instance
         */
        fun getDatabase(context: Context): NotesDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NotesDatabase::class.java,
                    "notes_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}