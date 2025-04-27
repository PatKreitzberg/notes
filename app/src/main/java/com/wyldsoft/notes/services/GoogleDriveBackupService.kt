package com.wyldsoft.notes.services

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.wyldsoft.notes.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Service to handle Google Drive backup operations.
 *
 * This service manages authentication with Google Drive and provides
 * functionality to backup the app's database to Google Drive.
 */
class GoogleDriveBackupService(private val context: Context) {

    companion object {
        private const val TAG = "GoogleDriveBackupService"
        private const val APP_NAME = "Notes Backup"
        private const val BACKUP_FOLDER_NAME = "NotesBackups"
    }

    // States for the backup process
    enum class BackupState {
        IDLE,
        AUTHENTICATING,
        PREPARING,
        UPLOADING,
        SUCCESS,
        ERROR
    }

    // Progress and state information
    private val _backupState = MutableStateFlow(BackupState.IDLE)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Google Sign-In client
    private lateinit var googleSignInClient: GoogleSignInClient

    // Initialize with default options in constructor
    init {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()

        googleSignInClient = GoogleSignIn.getClient(context, signInOptions)
    }

    // Setup Google Sign-In with more specific options
    fun setupGoogleSignIn(activity: Activity) {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .requestIdToken(activity.getString(R.string.google_oauth_client_id))
            .build()

        googleSignInClient = GoogleSignIn.getClient(activity, signInOptions)
    }

    // Check if user is already signed in
    fun isSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_FILE))
    }

    // Sign in to Google Drive
    fun signIn(activity: Activity, launcher: ActivityResultLauncher<Intent>) {
        _backupState.value = BackupState.AUTHENTICATING
        val signInIntent = googleSignInClient.signInIntent
        launcher.launch(signInIntent)
    }

    // Handle sign-in result
    fun handleSignInResult(data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(IOException::class.java)
            if (account != null) {
                Log.d(TAG, "Sign-in successful: ${account.email}")
                _backupState.value = BackupState.IDLE
            } else {
                _backupState.value = BackupState.ERROR
                _errorMessage.value = "Sign-in failed"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in failed", e)
            _backupState.value = BackupState.ERROR
            _errorMessage.value = "Sign-in failed: ${e.message}"
        }
    }

    // Sign out from Google Drive
    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            try {
                googleSignInClient.signOut().await()
                Log.d(TAG, "Sign-out successful")
            } catch (e: Exception) {
                Log.e(TAG, "Sign-out failed", e)
            }
        }
    }

    // Backup database to Google Drive
    suspend fun backupDatabase() {
        if (_backupState.value == BackupState.UPLOADING) {
            Log.d(TAG, "Backup already in progress")
            return
        }

        try {
            _backupState.value = BackupState.PREPARING

            // Get Google account
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account == null) {
                _backupState.value = BackupState.ERROR
                _errorMessage.value = "Not signed in to Google"
                return
            }

            // Create Drive service
            val driveService = getDriveService(account)

            // Create backup folder if needed
            val folderId = getOrCreateBackupFolder(driveService)

            // Upload database file
            val databaseFile = getDatabaseFile()
            if (databaseFile == null) {
                _backupState.value = BackupState.ERROR
                _errorMessage.value = "Could not locate database file"
                return
            }

            _backupState.value = BackupState.UPLOADING

            // Upload the file
            uploadFile(driveService, databaseFile, folderId)

            _backupState.value = BackupState.SUCCESS
            Log.d(TAG, "Backup completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            _backupState.value = BackupState.ERROR
            _errorMessage.value = "Backup failed: ${e.message}"
        }
    }

    // Get Drive service
    private fun getDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account

        return Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName(APP_NAME)
            .build()
    }

    // Create or get backup folder
    private suspend fun getOrCreateBackupFolder(driveService: Drive): String {
        return withContext(Dispatchers.IO) {
            // Check if folder already exists
            val folderList = driveService.files().list()
                .setQ("name = '$BACKUP_FOLDER_NAME' and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
                .setSpaces("drive")
                .execute()

            if (folderList.files.isNotEmpty()) {
                // Return existing folder ID
                return@withContext folderList.files[0].id
            }

            // Create new folder
            val folderMetadata = File()
                .setName(BACKUP_FOLDER_NAME)
                .setMimeType("application/vnd.google-apps.folder")

            val folder = driveService.files().create(folderMetadata)
                .setFields("id")
                .execute()

            return@withContext folder.id
        }
    }

    // Get database file
    private fun getDatabaseFile(): java.io.File? {
        val databasePath = context.getDatabasePath("notes_database")
        return if (databasePath.exists()) databasePath else null
    }

    // Upload file to Drive
    private suspend fun uploadFile(driveService: Drive, file: java.io.File, folderId: String) {
        return withContext(Dispatchers.IO) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val timestamp = dateFormat.format(Date())
            val fileName = "notes_backup_$timestamp.db"

            val fileMetadata = File()
                .setName(fileName)
                .setParents(listOf(folderId))

            FileInputStream(file).use { inputStream ->
                driveService.files().create(fileMetadata,
                    com.google.api.client.http.InputStreamContent("application/octet-stream", inputStream))
                    .setFields("id, name")
                    .execute()
            }
        }
    }

    // Reset state
    fun resetState() {
        _backupState.value = BackupState.IDLE
        _errorMessage.value = null
    }
}