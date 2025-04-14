package com.wyldsoft.notes

import android.app.Application
import android.content.Context
import com.wyldsoft.notes.data.NotesDatabase
import com.wyldsoft.notes.data.repository.NotebookRepository
import com.wyldsoft.notes.data.repository.PageRepository
import com.wyldsoft.notes.data.repository.StrokeRepository
import io.shipbook.shipbooksdk.ShipBook
import org.lsposed.hiddenapibypass.HiddenApiBypass


class NotesApp : Application() {
    // Database and repository objects
    val database by lazy { NotesDatabase.getDatabase(this) }
    val notebookRepository by lazy { NotebookRepository(database.notebookDao()) }
    val pageRepository by lazy { PageRepository(database.pageDao(), database.notebookDao()) }
    val strokeRepository by lazy {
        StrokeRepository(
            database.strokeDao(),
            database.strokePointDao(),
            database.pageDao()
        )
    }

    companion object {
        private var instance: NotesApp? = null

        fun getInstance(): NotesApp {
            return instance!!
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        checkHiddenApiBypass()
        initializeShipbook()
    }

    private fun checkHiddenApiBypass() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
    }

    private fun initializeShipbook() {
        // Initialize logging if needed
        try {
            // Basic initialization - using only the methods that are available
            ShipBook.start(this, BuildConfig.SHIPBOOK_APP_ID, BuildConfig.SHIPBOOK_APP_KEY)
            // Other initialization methods appear to be unavailable in this version
            // We'll stick with the basic initialization
        } catch (e: Exception) {
            // Handle initialization error
            e.printStackTrace()
        }
    }
}