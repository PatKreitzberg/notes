package com.wyldsoft.notes.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.wyldsoft.notes.database.dao.NoteDao
import com.wyldsoft.notes.database.dao.SettingsDao
import com.wyldsoft.notes.database.dao.StrokeDao
import com.wyldsoft.notes.database.dao.StrokePointDao
import com.wyldsoft.notes.database.entity.NoteEntity
import com.wyldsoft.notes.database.entity.SettingsEntity
import com.wyldsoft.notes.database.entity.StrokeEntity
import com.wyldsoft.notes.database.entity.StrokePointEntity
import com.wyldsoft.notes.database.util.Converters

/**
 * Main database class for the Notes application
 */
@Database(
    entities = [
        NoteEntity::class,
        StrokeEntity::class,
        StrokePointEntity::class,
        SettingsEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun strokeDao(): StrokeDao
    abstract fun strokePointDao(): StrokePointDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: NotesDatabase? = null

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