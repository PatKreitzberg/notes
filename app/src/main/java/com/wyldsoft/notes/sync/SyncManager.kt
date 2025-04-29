// app/src/main/java/com/wyldsoft/notes/sync/SyncManager.kt
package com.wyldsoft.notes.sync

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wyldsoft.notes.database.repository.NoteRepository
import com.wyldsoft.notes.database.entity.NoteEntity
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
    val driveServiceWrapper: DriveServiceWrapper,
    private val coroutineScope: CoroutineScope
) {
    // Sync state
    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

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
    private val changeTracker = ChangeTracker(noteRepository, coroutineScope)
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
        if (!networkMonitor.canSync(syncOnlyOnWifi)) {
            _errorMessage.value = "Cannot sync: Wi-Fi not available and sync is set to Wi-Fi only"
            return false
        }

        try {
            _syncState.value = SyncState.CONNECTING

            // Check Google Drive connection
            if (!driveServiceWrapper.isSignedIn()) {
                _errorMessage.value = "Not signed in to Google Drive"
                _syncState.value = SyncState.ERROR
                return false
            }

            _syncState.value = SyncState.SYNCING
            _syncProgress.value = 0.1f

            // Download remote changes first
            val remoteChanges = downloadChanges()
            _syncProgress.value = 0.5f

            // Then upload local changes
            val localChangesSynced = uploadChanges()
            _syncProgress.value = 0.9f

            // Update last sync time
            _lastSyncTime.value = Date()
            saveSyncSettings()

            _syncState.value = SyncState.SUCCESS
            _syncProgress.value = 1.0f
            return true

        } catch (e: Exception) {
            _errorMessage.value = "Sync failed: ${e.message}"
            _syncState.value = SyncState.ERROR
            return false
        }
    }

    /**
     * Downloads changes from Google Drive
     */
    private suspend fun downloadChanges(): List<NoteEntity> = withContext(Dispatchers.IO) {
        // Get last sync time
        val lastSync = _lastSyncTime.value ?: Date(0) // If never synced, use epoch time

        // Get metadata file to determine changes
        val metadataFile = driveServiceWrapper.getMetadataFile()

        // Build list of files that changed remotely
        val changedFiles = driveServiceWrapper.getChangedFiles(lastSync)
        val downloadedNotes = mutableListOf<NoteEntity>()

        // Process each changed file
        for ((index, fileInfo) in changedFiles.withIndex()) {
            // Update progress
            _syncProgress.value = 0.1f + (0.4f * index / changedFiles.size.toFloat())

            try {
                // Download file
                val fileContent = driveServiceWrapper.downloadFile(fileInfo.id)

                // Deserialize to note
                val remoteNote = NoteSerializer.deserialize(fileContent)

                // Check for conflicts
                val localNote = noteRepository.getNoteById(remoteNote.id)

                if (localNote != null) {
                    // Potential conflict - check if both have changed
                    if (localNote.updatedAt > lastSync && remoteNote.updatedAt > lastSync) {
                        // Conflict! Resolve it
                        val resolution = conflictResolver.resolveConflict(
                            localNote = localNote,
                            remoteNote = remoteNote
                        )

                        when (resolution) {
                            is Resolution.UseLocal -> {
                                // Keep local, upload it to override remote
                                uploadNote(localNote)
                            }
                            is Resolution.UseRemote -> {
                                // Use remote version
                                saveRemoteNote(remoteNote, fileInfo)
                                downloadedNotes.add(remoteNote)
                            }
                            is Resolution.KeepBoth -> {
                                // Create a duplicate note with new ID
                                val duplicateNote = createDuplicateNote(remoteNote)
                                downloadedNotes.add(duplicateNote)
                            }
                        }
                    } else if (remoteNote.updatedAt > localNote.updatedAt) {
                        // Remote is newer, use it
                        saveRemoteNote(remoteNote, fileInfo)
                        downloadedNotes.add(remoteNote)
                    }
                    // If local is newer, we'll upload it in the upload phase
                } else {
                    // No local copy, just save it
                    saveRemoteNote(remoteNote, fileInfo)
                    downloadedNotes.add(remoteNote)
                }
            } catch (e: Exception) {
                // Log error but continue with next file
                println("Error downloading file ${fileInfo.name}: ${e.message}")
            }
        }

        return@withContext downloadedNotes
    }

    /**
     * Uploads local changes to Google Drive
     */
    private suspend fun uploadChanges(): Boolean = withContext(Dispatchers.IO) {
        val lastSync = _lastSyncTime.value ?: Date(0)

        // Get notes that changed locally since last sync
        val changedNotes = changeTracker.getChangedNotesSince(lastSync)

        for ((index, note) in changedNotes.withIndex()) {
            // Update progress
            _syncProgress.value = 0.5f + (0.4f * index / changedNotes.size.toFloat())

            try {
                uploadNote(note)
            } catch (e: Exception) {
                // Log error but continue with next note
                println("Error uploading note ${note.title}: ${e.message}")
            }
        }

        return@withContext true
    }

    /**
     * Uploads a single note to Google Drive
     */
    private suspend fun uploadNote(note: NoteEntity) {
        // Get strokes for this note
        val strokes = noteRepository.getStrokesForNote(note.id)

        // Serialize note and strokes
        val noteData = NoteSerializer.serialize(note, strokes)

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
        // Save note to database
        noteRepository.updateNote(remoteNote)

        // Check if we need to download strokes
        val noteContent = driveServiceWrapper.downloadFile(fileInfo.id)
        val strokes = NoteSerializer.deserializeStrokes(noteContent, remoteNote.id)

        // Delete existing strokes and replace with remote ones
        noteRepository.deleteStrokesForNote(remoteNote.id)
        noteRepository.saveStrokes(remoteNote.id, strokes)
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