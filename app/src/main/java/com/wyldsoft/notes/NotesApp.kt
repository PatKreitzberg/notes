package com.wyldsoft.notes

import android.app.Application
import android.content.Context
import com.wyldsoft.notes.database.NotesDatabase
import com.wyldsoft.notes.database.repository.*
import com.wyldsoft.notes.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.lsposed.hiddenapibypass.HiddenApiBypass

class NotesApp : Application() {
    // Application-level coroutine scope
    private val applicationScope = CoroutineScope(SupervisorJob())

    // Database instance
    private lateinit var database: NotesDatabase

    // Repositories
    lateinit var noteRepository: NoteRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var folderRepository: FolderRepository
        private set

    lateinit var notebookRepository: NotebookRepository
        private set

    lateinit var pageNotebookRepository: PageNotebookRepository
        private set

    override fun onCreate() {
        super.onCreate()
        checkHiddenApiBypass()
        initializeDatabase()
    }

    private fun checkHiddenApiBypass() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
    }

    private fun initializeDatabase() {
        // Initialize database
        database = NotesDatabase.getDatabase(this)

        // Initialize repositories
        noteRepository = NoteRepository(
            database.noteDao(),
            database.strokeDao(),
            database.strokePointDao()
        )

        settingsRepository = SettingsRepository(
            this,
            applicationScope,
            database.settingsDao()
        )

        folderRepository = FolderRepository(
            database.folderDao()
        )

        notebookRepository = NotebookRepository(
            database.notebookDao()
        )

        pageNotebookRepository = PageNotebookRepository(
            database.pageNotebookDao()
        )
    }

    companion object {
        // Helper function to get the application instance from a context
        fun getApp(context: Context): NotesApp {
            return context.applicationContext as NotesApp
        }
    }
}