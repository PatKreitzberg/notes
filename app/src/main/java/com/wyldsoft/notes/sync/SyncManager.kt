// app/src/main/java/com/wyldsoft/notes/sync/SyncManager.kt
package com.wyldsoft.notes.sync

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wyldsoft.notes.database.repository.NoteRepository
import com.wyldsoft.notes.database.entity.NoteEntity
import com.wyldsoft.notes.database.repository.NotebookRepository
import com.wyldsoft.notes.database.repository.PageNotebookRepository
import com.wyldsoft.notes.utils.Stroke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.concurrent.TimeUnit

enum class SyncState {
    IDLE, CONNECTING, SYNCING, SUCCESS, ERROR, CONFLICT
}

enum class SyncFrequency(val intervalMinutes: Long) {
    REALTIME(5),
    HOURLY(60),
    DAILY(60 * 24)
}

class SyncManager(
    private val context: Context,
    private val noteRepository: NoteRepository,
    private val notebookRepository: NotebookRepository, // Add this
    private val pageNotebookRepository: PageNotebookRepository, // Add this
    val driveServiceWrapper: DriveServiceWrapper,
    private val coroutineScope: CoroutineScope
) {
    // Sync state
    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    private var isSyncInProgress = false

    private val _syncProgress = MutableStateFlow(0f)
    val syncProgress: StateFlow<Float> = _syncProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Date?>(null)
    val lastSyncTime: StateFlow<Date?> = _lastSyncTime.asStateFlow()

    // Sync settings
    var syncOnlyOnWifi by mutableStateOf(true)
    var autoSyncEnabled by mutableStateOf(true)
    var syncFrequency by mutableStateOf(SyncFrequency.REALTIME)

    // Components
    val changeTracker = ChangeTracker(noteRepository, coroutineScope)
    val conflictResolver = ConflictResolver(context)
    private val networkMonitor = NetworkMonitor(context)

    init {
        // Initialize sync settings from preferences
        loadSyncSettings()

        // Set up automatic sync if enabled
        if (autoSyncEnabled) {
            setupAutomaticSync()
        }

        // Listen for connectivity changes
        monitorNetworkChanges()
    }

    /**
     * Performs a full synchronization operation
     */
    suspend fun performSync(): Boolean {
        android.util.Log.d("SyncManager", "Starting sync process")
        if (isSyncInProgress) {
            println("sync: is syncing true")
            android.util.Log.d("SyncManager", "Sync already in progress, skipping this request")
            return false
        }

        isSyncInProgress = true
        changeTracker.beginSync()

        if (!networkMonitor.canSync(syncOnlyOnWifi)) {
            android.util.Log.d("SyncManager", "Cannot sync: Wi-Fi not available and sync is set to Wi-Fi only")
            _errorMessage.value = "Cannot sync: Wi-Fi not available and sync is set to Wi-Fi only"
            _syncState.value = SyncState.ERROR
            return false
        }

        try {
            _syncState.value = SyncState.CONNECTING
            android.util.Log.d("SyncManager", "Connecting to Google Drive")

            // Check Google Drive connection
            if (!driveServiceWrapper.isSignedIn()) {
                android.util.Log.d("SyncManager", "Not signed in to Google Drive")
                _errorMessage.value = "Not signed in to Google Drive. Please sign in first."
                _syncState.value = SyncState.ERROR
                return false
            }

            _syncState.value = SyncState.SYNCING
            _syncProgress.value = 0.1f
            android.util.Log.d("SyncManager", "Connected, beginning sync process")

            // Download remote changes first
            android.util.Log.d("SyncManager", "Downloading remote changes")
            val remoteChanges = downloadChanges()


            android.util.Log.d("SyncManager", "Checking database after download")
            val allNotes = noteRepository.getAllNotesSync() // We'll need to create this method
            android.util.Log.d("SyncManager", "Total notes in database: ${allNotes.size}")
            allNotes.forEach { note ->
                android.util.Log.d("SyncManager", "Note in DB: ${note.id} - ${note.title}")
                // Check if note is in any notebook
                val notebookCount = pageNotebookRepository.getNotebookCountForPage(note.id)
                android.util.Log.d("SyncManager", "Note belongs to ${notebookCount} notebooks")
            }


            _syncProgress.value = 0.5f
            android.util.Log.d("SyncManager", "Downloaded ${remoteChanges.size} remote changes")

            // Then upload local changes
            android.util.Log.d("SyncManager", "Uploading local changes")
            val localChangesSynced = uploadChanges()
            _syncProgress.value = 0.9f
            android.util.Log.d("SyncManager", "Local changes sync completed: $localChangesSynced")

            // Update last sync time
            _lastSyncTime.value = Date()
            saveSyncSettings()

            _syncState.value = SyncState.SUCCESS
            _syncProgress.value = 1.0f
            android.util.Log.d("SyncManager", "Sync completed successfully")
            return true

        } catch (e: IllegalStateException) {
            // Specific handling for authentication errors
            android.util.Log.e("SyncManager", "Authentication error: ${e.message}", e)
            _errorMessage.value = e.message ?: "Authentication error"
            _syncState.value = SyncState.ERROR
            return false
        } catch (e: Exception) {
            // General error handling
            android.util.Log.e("SyncManager", "Sync failed: ${e.message}", e)
            _errorMessage.value = "Sync failed: ${e.message}"
            _syncState.value = SyncState.ERROR
            return false
        } finally {
            // Important: Always reset the flag when done, even if there was an error
            isSyncInProgress = false
            changeTracker.endSync()
        }
    }


    /**
     * Downloads changes from Google Drive
     */
    private suspend fun downloadChanges(): List<NoteEntity> = withContext(Dispatchers.IO) {
        // Get last sync time
        val lastSync = _lastSyncTime.value ?: Date(0) // If never synced, use epoch time
        android.util.Log.d("SyncManager", "Downloading changes since: $lastSync")

        // Get metadata file to determine changes
        val metadataFile = driveServiceWrapper.getMetadataFile()
        android.util.Log.d("SyncManager", "Metadata file found: ${metadataFile != null}")

        // Build list of files that changed remotely
        val changedFiles = driveServiceWrapper.getChangedFiles(lastSync)
        android.util.Log.d("SyncManager", "Found ${changedFiles.size} changed files")

        if (changedFiles.isEmpty()) {
            android.util.Log.d("SyncManager", "No changed files found - checking if this is initial sync")
            // If this is potentially an initial sync (no notes exist locally), try with epoch time
            if (noteRepository.getNotesCount() == 0) {
                android.util.Log.d("SyncManager", "No local notes found - attempting to download all remote notes")
                val allFiles = driveServiceWrapper.getChangedFiles(Date(0))
                android.util.Log.d("SyncManager", "Found ${allFiles.size} total files on Drive")
                // Use all files instead if we found some
                if (allFiles.isNotEmpty()) {
                    return@withContext downloadFilesFromDrive(allFiles, lastSync)
                }
            }
            return@withContext emptyList()
        }

        return@withContext downloadFilesFromDrive(changedFiles, lastSync)
    }

    /**
     * Helper method to download and process files from Drive
     */
    private suspend fun downloadFilesFromDrive(files: List<DriveFileInfo>, lastSync: Date): List<NoteEntity> {
        val downloadedNotes = mutableListOf<NoteEntity>()
        val notebookMap = mutableMapOf<String, String>() // Map of notebook title to ID

        // Process each file
        for ((index, fileInfo) in files.withIndex()) {
            // Update progress
            _syncProgress.value = 0.1f + (0.4f * index / files.size.toFloat())
            android.util.Log.d("SyncManager", "Processing file ${index+1}/${files.size}: ${fileInfo.name}")

            try {
                // Skip files that don't look like notes (based on naming convention)
                if (!fileInfo.name.contains("_") || !fileInfo.name.endsWith(".json")) {
                    android.util.Log.d("SyncManager", "Skipping non-note file: ${fileInfo.name}")
                    continue
                }

                // Download file
                val fileContent = driveServiceWrapper.downloadFile(fileInfo.id)
                android.util.Log.d("SyncManager", "Downloaded file content length: ${fileContent.length}")

                // Parse full file content as JSON
                val jsonObj = try {
                    org.json.JSONObject(fileContent)
                } catch (e: Exception) {
                    android.util.Log.e("SyncManager", "Error parsing JSON: ${e.message}", e)
                    continue
                }

                // Extract notebook info if present
                val notebooks = if (jsonObj.has("notebooks")) {
                    try {
                        val notebooksArray = jsonObj.getJSONArray("notebooks")
                        val notebookTitles = mutableListOf<String>()
                        for (i in 0 until notebooksArray.length()) {
                            notebookTitles.add(notebooksArray.getString(i))
                        }
                        notebookTitles
                    } catch (e: Exception) {
                        android.util.Log.e("SyncManager", "Error parsing notebooks: ${e.message}", e)
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                // Deserialize to note
                val remoteNote = try {
                    NoteSerializer.deserialize(fileContent)
                } catch (e: Exception) {
                    android.util.Log.e("SyncManager", "Error deserializing note: ${e.message}", e)
                    continue
                }

                android.util.Log.d("SyncManager", "Deserialized note: ${remoteNote.id} - ${remoteNote.title}")
                android.util.Log.d("SyncManager", "Note belongs to notebooks: ${notebooks.joinToString()}")

                // Check for conflicts
                val localNote = noteRepository.getNoteById(remoteNote.id)

                if (localNote != null) {
                    android.util.Log.d("SyncManager", "Found existing local note with same ID")
                    // Potential conflict - check if both have changed
                    if (localNote.updatedAt > lastSync && remoteNote.updatedAt > lastSync) {
                        android.util.Log.d("SyncManager", "Conflict detected - both local and remote notes modified")
                        // Conflict! Resolve it
                        val resolution = conflictResolver.resolveConflict(
                            localNote = localNote,
                            remoteNote = remoteNote
                        )

                        when (resolution) {
                            is Resolution.UseLocal -> {
                                android.util.Log.d("SyncManager", "Resolution: Using local version")
                                // Keep local, upload it to override remote
                                uploadNote(localNote)
                            }
                            is Resolution.UseRemote -> {
                                android.util.Log.d("SyncManager", "Resolution: Using remote version")
                                // Use remote version
                                saveRemoteNote(remoteNote, fileInfo)
                                downloadedNotes.add(remoteNote)

                                // Remember notebooks this note belongs to
                                if (notebooks.isNotEmpty()) {
                                    associateNoteWithNotebooks(remoteNote.id, notebooks, notebookMap)
                                }
                            }
                            is Resolution.KeepBoth -> {
                                android.util.Log.d("SyncManager", "Resolution: Keeping both versions")
                                // Create a duplicate note with new ID
                                val duplicateNote = createDuplicateNote(remoteNote)
                                downloadedNotes.add(duplicateNote)

                                // Remember notebooks this note belongs to
                                if (notebooks.isNotEmpty()) {
                                    associateNoteWithNotebooks(duplicateNote.id, notebooks, notebookMap)
                                }
                            }
                        }
                    } else if (remoteNote.updatedAt > localNote.updatedAt) {
                        android.util.Log.d("SyncManager", "Remote note is newer, using it")
                        // Remote is newer, use it
                        saveRemoteNote(remoteNote, fileInfo)
                        downloadedNotes.add(remoteNote)

                        // Remember notebooks this note belongs to
                        if (notebooks.isNotEmpty()) {
                            associateNoteWithNotebooks(remoteNote.id, notebooks, notebookMap)
                        }
                    } else {
                        android.util.Log.d("SyncManager", "Local note is newer, keeping it")
                    }
                    // If local is newer, we'll upload it in the upload phase
                } else {
                    android.util.Log.d("SyncManager", "No local copy, saving remote note")
                    // No local copy, just save it
                    saveRemoteNote(remoteNote, fileInfo)
                    downloadedNotes.add(remoteNote)

                    // Remember notebooks this note belongs to
                    if (notebooks.isNotEmpty()) {
                        associateNoteWithNotebooks(remoteNote.id, notebooks, notebookMap)
                    }
                }
            } catch (e: Exception) {
                // Log error but continue with next file
                android.util.Log.e("SyncManager", "Error downloading file ${fileInfo.name}: ${e.message}", e)
            }
        }

        // Create a default notebook if no notebooks were specified
        if (downloadedNotes.isNotEmpty() && notebookMap.isEmpty()) {
            android.util.Log.d("SyncManager", "No notebooks specified for any notes. Creating default notebook.")
            val defaultNotebookId = ensureDefaultSyncNotebook()

            // Add all notes to the default notebook
            for (note in downloadedNotes) {
                // Only add if not already in a notebook
                val notebookCount = pageNotebookRepository.getNotebookCountForPage(note.id)
                if (notebookCount == 0) {
                    android.util.Log.d("SyncManager", "Adding note ${note.id} to default notebook")
                    pageNotebookRepository.addPageToNotebook(note.id, defaultNotebookId)
                }
            }
        }

        android.util.Log.d("SyncManager", "Downloaded and processed ${downloadedNotes.size} notes with ${notebookMap.size} notebooks")
        return downloadedNotes
    }

    /**
     * Helper method to associate a note with its notebooks
     */
    private suspend fun associateNoteWithNotebooks(
        noteId: String,
        notebookTitles: List<String>,
        notebookMap: MutableMap<String, String>
    ) {
        for (title in notebookTitles) {
            // Get or create notebook
            val notebookId = notebookMap.getOrPut(title) {
                // Check if notebook already exists
                val existingNotebooks = notebookRepository.getAllNotebooksSync()
                val existingNotebook = existingNotebooks.find { it.title == title }

                if (existingNotebook != null) {
                    android.util.Log.d("SyncManager", "Found existing notebook: $title")
                    existingNotebook.id
                } else {
                    // Create new notebook
                    android.util.Log.d("SyncManager", "Creating new notebook: $title")
                    val notebook = notebookRepository.createNotebook(title)
                    notebook.id
                }
            }

            // Add note to notebook
            android.util.Log.d("SyncManager", "Adding note $noteId to notebook: $title")
            pageNotebookRepository.addPageToNotebook(noteId, notebookId)
        }
    }

    /**
     * Ensures a default notebook exists for synced notes
     * @return ID of the default notebook
     */
    private suspend fun ensureDefaultSyncNotebook(): String {
        // Search for existing default notebook
        val allNotebooks = notebookRepository.getAllNotebooksSync()
        val defaultNotebook = allNotebooks.find { it.title == "Google Drive Sync" }

        if (defaultNotebook != null) {
            android.util.Log.d("SyncManager", "Found existing default sync notebook: ${defaultNotebook.id}")
            return defaultNotebook.id
        }

        // Create a new default notebook
        android.util.Log.d("SyncManager", "Creating new default sync notebook")
        val notebook = notebookRepository.createNotebook("Google Drive Sync")
        return notebook.id
    }



    /**
     * Uploads local changes to Google Drive
     */
    private suspend fun uploadChanges(): Boolean = withContext(Dispatchers.IO) {
        val lastSync = _lastSyncTime.value ?: Date(0)
        android.util.Log.d("SyncManager", "Getting notes changed since ${lastSync}")

        // Get notes that changed locally since last sync
        val changedNotes = changeTracker.getChangedNotesSince(lastSync)
        android.util.Log.d("SyncManager", "Found ${changedNotes.size} notes to upload")

        for ((index, note) in changedNotes.withIndex()) {
            // Update progress
            _syncProgress.value = 0.5f + (0.4f * index / changedNotes.size.toFloat())
            android.util.Log.d("SyncManager", "Uploading note ${note.id}: ${note.title}")

            try {
                uploadNote(note)
                android.util.Log.d("SyncManager", "Successfully uploaded note ${note.id}")
            } catch (e: Exception) {
                // Log error but continue with next note
                android.util.Log.e("SyncManager", "Error uploading note ${note.title}: ${e.message}", e)
            }
        }

        // After successful upload, clear the change tracking
        if (changedNotes.isNotEmpty()) {
            android.util.Log.d("SyncManager", "Clearing change tracking after successful upload")
            changeTracker.clearChanges()
        }

        return@withContext true
    }

    /**
     * Uploads a single note to Google Drive
     */
    private suspend fun uploadNote(note: NoteEntity) {
        // Get strokes for this note
        val strokes = noteRepository.getStrokesForNote(note.id)

        // Get notebooks this note belongs to
        val notebooks = pageNotebookRepository.getNotebooksContainingPageSync(note.id)
        val notebookTitles = notebooks.map { it.title }

        android.util.Log.d("SyncManager", "Uploading note ${note.id} with ${strokes.size} strokes to notebooks: ${notebookTitles.joinToString()}")

        // Serialize note and strokes
        val noteData = NoteSerializer.serialize(note, strokes, notebookTitles)

        // Check if note already exists on Drive
        val existingFile = driveServiceWrapper.findNoteFile(note.id)

        if (existingFile != null) {
            // Update existing file
            driveServiceWrapper.updateFile(existingFile.id, noteData, note.title)
        } else {
            // Create new file
            driveServiceWrapper.createNoteFile(note.id, noteData, note.title)
        }

        // Update metadata
        updateSyncMetadata(note)
    }

    /**
     * Saves a remote note to local storage
     */
    private suspend fun saveRemoteNote(remoteNote: NoteEntity, fileInfo: DriveFileInfo) {
        try {
            android.util.Log.d("SyncManager", "Saving remote note: ${remoteNote.id} - ${remoteNote.title}")

            // FIRST: Save note to database
            // We need to make sure the note exists before we try to save strokes
            noteRepository.createOrUpdateNote(remoteNote)
            android.util.Log.d("SyncManager", "Note entity saved successfully")

            // THEN: Check if we need to download strokes
            val noteContent = driveServiceWrapper.downloadFile(fileInfo.id)

            try {
                val strokes = NoteSerializer.deserializeStrokes(noteContent, remoteNote.id)
                android.util.Log.d("SyncManager", "Deserialized ${strokes.size} strokes")

                // Delete existing strokes and replace with remote ones
                noteRepository.deleteStrokesForNote(remoteNote.id)
                android.util.Log.d("SyncManager", "Deleted existing strokes")

                if (strokes.isNotEmpty()) {
                    noteRepository.saveStrokes(remoteNote.id, strokes)
                    android.util.Log.d("SyncManager", "Saved ${strokes.size} strokes")
                }
            } catch (e: Exception) {
                android.util.Log.e("SyncManager", "Error processing strokes: ${e.message}", e)
                // Continue without strokes if there's an error
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncManager", "Error saving remote note: ${e.message}", e)
            throw e
        }
    }

    /**
     * Creates a duplicate of a note with a new ID
     */
    private suspend fun createDuplicateNote(sourceNote: NoteEntity): NoteEntity {
        val newId = java.util.UUID.randomUUID().toString()
        val newTitle = "${sourceNote.title} (Copy)"

        // Create new note
        val duplicateNote = NoteEntity(
            id = newId,
            title = newTitle,
            createdAt = Date(),
            updatedAt = Date(),
            width = sourceNote.width,
            height = sourceNote.height
        )

        // Save to repository
        noteRepository.createNote(
            duplicateNote.id,
            duplicateNote.title,
            duplicateNote.width,
            duplicateNote.height
        )

        // Deserialize strokes from source note
        val noteFile = driveServiceWrapper.findNoteFile(sourceNote.id)
        if (noteFile != null) {
            val noteContent = driveServiceWrapper.downloadFile(noteFile.id)
            val sourceStrokes = NoteSerializer.deserializeStrokes(noteContent, sourceNote.id)

            // Create new strokes with new note ID
            val newStrokes = sourceStrokes.map { stroke ->
                stroke.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    pageId = newId
                )
            }

            // Save new strokes
            noteRepository.saveStrokes(newId, newStrokes)
        }

        return duplicateNote
    }

    /**
     * Updates the sync metadata file
     */
    private suspend fun updateSyncMetadata(note: NoteEntity) {
        // Get existing metadata file
        val metadataFile = driveServiceWrapper.getMetadataFile()

        // Read existing metadata
        val metadata = if (metadataFile != null) {
            val content = driveServiceWrapper.downloadFile(metadataFile.id)
            try {
                SyncMetadata.fromJson(content)
            } catch (e: Exception) {
                SyncMetadata() // Create new if parsing fails
            }
        } else {
            SyncMetadata()
        }

        // Update note entry
        metadata.noteEntries[note.id] = SyncMetadata.NoteEntry(
            id = note.id,
            lastModified = note.updatedAt,
            lastSynced = Date()
        )

        // Save updated metadata
        val metadataContent = metadata.toJson()
        if (metadataFile != null) {
            driveServiceWrapper.updateFile(metadataFile.id, metadataContent, "notes_sync_metadata.json")
        } else {
            driveServiceWrapper.createMetadataFile(metadataContent)
        }
    }

    /**
     * Sets up automatic sync based on current settings
     */
    private fun setupAutomaticSync() {
        // Set up periodic work request
        val workManager = androidx.work.WorkManager.getInstance(context)

        // Cancel any existing sync work
        workManager.cancelUniqueWork("notes_sync")

        if (autoSyncEnabled) {
            // Create constraints
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(
                    if (syncOnlyOnWifi)
                        androidx.work.NetworkType.UNMETERED
                    else
                        androidx.work.NetworkType.CONNECTED
                )
                .build()

            // Create periodic work request
            val syncWork = androidx.work.PeriodicWorkRequestBuilder<SyncWorker>(
                syncFrequency.intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            // Enqueue work
            workManager.enqueueUniquePeriodicWork(
                "notes_sync",
                androidx.work.ExistingPeriodicWorkPolicy.REPLACE,
                syncWork
            )
        }
    }

    /**
     * Monitors network connectivity changes
     */
    private fun monitorNetworkChanges() {
        coroutineScope.launch {
            networkMonitor.networkStatus.collect { networkStatus ->
                // If we regain connectivity and auto-sync is enabled, trigger a sync
                if (networkStatus.isConnected &&
                    autoSyncEnabled &&
                    (!syncOnlyOnWifi || networkStatus.isWifi)) {

                    coroutineScope.launch {
                        performSync()
                    }
                }
            }
        }
    }

    /**
     * Loads sync settings from SharedPreferences
     */
    private fun loadSyncSettings() {
        val prefs = context.getSharedPreferences("sync_settings", Context.MODE_PRIVATE)

        syncOnlyOnWifi = prefs.getBoolean("sync_only_wifi", true)
        autoSyncEnabled = prefs.getBoolean("auto_sync_enabled", true)
        syncFrequency = SyncFrequency.values()[
            prefs.getInt("sync_frequency", SyncFrequency.REALTIME.ordinal)
        ]

        // Load last sync time
        val lastSyncMs = prefs.getLong("last_sync_time", 0)
        _lastSyncTime.value = if (lastSyncMs > 0) Date(lastSyncMs) else null
    }

    /**
     * Saves sync settings to SharedPreferences
     */
    private fun saveSyncSettings() {
        val prefs = context.getSharedPreferences("sync_settings", Context.MODE_PRIVATE)

        prefs.edit()
            .putBoolean("sync_only_wifi", syncOnlyOnWifi)
            .putBoolean("auto_sync_enabled", autoSyncEnabled)
            .putInt("sync_frequency", syncFrequency.ordinal)
            .putLong("last_sync_time", _lastSyncTime.value?.time ?: 0)
            .apply()

        // Update automatic sync schedule if needed
        if (autoSyncEnabled) {
            setupAutomaticSync()
        }
    }

    /**
     * Force a refresh of sync settings
     */
    fun updateSyncSettings(
        newSyncOnlyOnWifi: Boolean,
        newAutoSyncEnabled: Boolean,
        newSyncFrequency: SyncFrequency
    ) {
        syncOnlyOnWifi = newSyncOnlyOnWifi
        autoSyncEnabled = newAutoSyncEnabled
        syncFrequency = newSyncFrequency

        saveSyncSettings()
    }

    /**
     * Resets error state
     */
    fun resetErrorState() {
        _errorMessage.value = null
        if (_syncState.value == SyncState.ERROR) {
            _syncState.value = SyncState.IDLE
        }
    }
}