package com.wyldsoft.notes.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wyldsoft.notes.database.dao.*
import com.wyldsoft.notes.database.entity.*
import com.wyldsoft.notes.database.util.Converters

@Database(
    entities = [
        NoteEntity::class,
        StrokeEntity::class,
        StrokePointEntity::class,
        SettingsEntity::class,
        FolderEntity::class,
        NotebookEntity::class,
        PageNotebookJoin::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun strokeDao(): StrokeDao
    abstract fun strokePointDao(): StrokePointDao
    abstract fun settingsDao(): SettingsDao
    abstract fun folderDao(): FolderDao
    abstract fun notebookDao(): NotebookDao
    abstract fun pageNotebookDao(): PageNotebookDao

    companion object {
        @Volatile
        private var INSTANCE: NotesDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create folders table
                database.execSQL(
                    """
                CREATE TABLE IF NOT EXISTS folders (
                    id TEXT NOT NULL PRIMARY KEY,
                    path TEXT NOT NULL,
                    name TEXT NOT NULL,
                    parentId TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """
                )

                // Create notebooks table
                database.execSQL(
                    """
                CREATE TABLE IF NOT EXISTS notebooks (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    folderId TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY (folderId) REFERENCES folders (id) ON DELETE SET NULL
                )
                """
                )

                // Create page_notebook_join table
                database.execSQL(
                    """
                CREATE TABLE IF NOT EXISTS page_notebook_join (
                    pageId TEXT NOT NULL,
                    notebookId TEXT NOT NULL,
                    addedAt INTEGER NOT NULL,
                    PRIMARY KEY (pageId, notebookId),
                    FOREIGN KEY (pageId) REFERENCES notes (id) ON DELETE CASCADE,
                    FOREIGN KEY (notebookId) REFERENCES notebooks (id) ON DELETE CASCADE
                )
                """
                )

                // Create indices
                database.execSQL("CREATE INDEX IF NOT EXISTS index_notebooks_folderId ON notebooks (folderId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_page_notebook_join_pageId ON page_notebook_join (pageId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_page_notebook_join_notebookId ON page_notebook_join (notebookId)")
            }
        }

        fun getDatabase(context: Context): NotesDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NotesDatabase::class.java,
                    "notes_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}