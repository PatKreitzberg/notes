// app/src/main/java/com/wyldsoft/notes/sync/DriveServiceWrapper.kt
package com.wyldsoft.notes.sync

import android.content.Context
import android.content.Intent
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
import com.google.api.services.drive.model.FileList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Date

/**
 * Information about a file stored in Google Drive
 */
data class DriveFileInfo(
    val id: String,
    val name: String,
    val modifiedTime: Date,
    val mimeType: String
)

/**
 * Wrapper for Google Drive API operations
 */
class DriveServiceWrapper(private val context: Context) {
    companion object {
        private const val APP_NAME = "Notes Sync"
        private const val NOTES_FOLDER_NAME = "Notes Sync"
        private const val METADATA_FILENAME = "notes_sync_metadata.json"
    }

    private lateinit var googleSignInClient: GoogleSignInClient
    private var driveService: Drive? = null
    private var driveRootFolderId: String? = null

    init {
        setupGoogleSignIn()

        // If already signed in, initialize Drive service
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null) {
            initializeDriveService(account)
        }
    }

    /**
     * Sets up Google Sign-In client
     */
    private fun setupGoogleSignIn() {
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()

        googleSignInClient = GoogleSignIn.getClient(context, signInOptions)
    }

    /**
     * Initializes Drive service with an account
     */
    private fun initializeDriveService(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account

        driveService = Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName(APP_NAME)
            .build()
    }

    /**
     * Checks if the user is signed in
     */
    fun isSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null && GoogleSignIn.hasPermissions(
            account, Scope(DriveScopes.DRIVE_FILE)
        )
    }

    /**
     * Initiates the sign-in process
     */
    fun signIn(launcher: ActivityResultLauncher<Intent>) {
        val signInIntent = googleSignInClient.signInIntent
        launcher.launch(signInIntent)
    }

    /**
     * Handles sign-in result
     */
    suspend fun handleSignInResult(data: Intent?): Boolean {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(IOException::class.java)
            if (account != null) {
                initializeDriveService(account)
                ensureNotesFolder() // Make sure we have our root folder
                true
            } else {
                false
            }
        } catch (e: Exception) {
            println("GoogleSignIn error: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Signs out from Google Drive
     */
    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            try {
                googleSignInClient.signOut().await()
                driveService = null
                driveRootFolderId = null
            } catch (e: Exception) {
                // Handle sign out error
            }
        }
    }

    /**
     * Ensures that the Notes folder exists in Drive
     */
    private suspend fun ensureNotesFolder(): String {
        return withContext(Dispatchers.IO) {
            if (driveRootFolderId != null) {
                return@withContext driveRootFolderId!!
            }

            val service = driveService ?: throw IllegalStateException("Drive service not initialized. Please sign in to Google Drive first.")

            // Check if folder already exists
            val query = "name = '$NOTES_FOLDER_NAME' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
            val result = service.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            if (result.files.isNotEmpty()) {
                driveRootFolderId = result.files[0].id
                return@withContext driveRootFolderId!!
            }

            // Create folder if it doesn't exist
            val folderMetadata = File()
                .setName(NOTES_FOLDER_NAME)
                .setMimeType("application/vnd.google-apps.folder")

            val folder = service.files().create(folderMetadata)
                .setFields("id")
                .execute()

            driveRootFolderId = folder.id
            return@withContext driveRootFolderId!!
        }
    }

    /**
     * Gets the metadata file (or null if it doesn't exist)
     */
    suspend fun getMetadataFile(): DriveFileInfo? {
        return withContext(Dispatchers.IO) {
            val service = driveService ?: throw IllegalStateException("Drive service not initialized")
            val folderId = ensureNotesFolder()

            // Search for metadata file
            val query = "name = '$METADATA_FILENAME' and '$folderId' in parents and trashed = false"
            val result = service.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name, modifiedTime, mimeType)")
                .execute()

            if (result.files.isEmpty()) {
                return@withContext null
            }

            val file = result.files[0]
            return@withContext DriveFileInfo(
                id = file.id,
                name = file.name,
                modifiedTime = Date(file.modifiedTime.value),
                mimeType = file.mimeType
            )
        }
    }

    /**
     * Creates a new metadata file
     */
    suspend fun createMetadataFile(content: String): DriveFileInfo {
        return withContext(Dispatchers.IO) {
            val service = driveService ?: throw IllegalStateException("Drive service not initialized")
            val folderId = ensureNotesFolder()

            val fileMetadata = File()
                .setName(METADATA_FILENAME)
                .setParents(listOf(folderId))
                .setMimeType("application/json")

            val contentStream = ByteArrayInputStream(content.toByteArray())

            val file = service.files().create(fileMetadata,
                com.google.api.client.http.InputStreamContent("application/json", contentStream))
                .setFields("id, name, modifiedTime, mimeType")
                .execute()

            return@withContext DriveFileInfo(
                id = file.id,
                name = file.name,
                modifiedTime = Date(file.modifiedTime.value),
                mimeType = file.mimeType
            )
        }
    }

    /**
     * Finds a note file by its ID
     */
    suspend fun findNoteFile(noteId: String): DriveFileInfo? {
        return withContext(Dispatchers.IO) {
            val service = driveService ?: throw IllegalStateException("Drive service not initialized")
            val folderId = ensureNotesFolder()

            // Search for note file using ID in filename
            val query = "name contains '$noteId' and '$folderId' in parents and trashed = false"
            val result = service.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name, modifiedTime, mimeType)")
                .execute()

            if (result.files.isEmpty()) {
                return@withContext null
            }

            val file = result.files[0]
            return@withContext DriveFileInfo(
                id = file.id,
                name = file.name,
                modifiedTime = Date(file.modifiedTime.value),
                mimeType = file.mimeType
            )
        }
    }

    /**
     * Gets files that changed since a specific time
     */
    suspend fun getChangedFiles(since: Date): List<DriveFileInfo> {
        return withContext(Dispatchers.IO) {
            val service = driveService ?: throw IllegalStateException("Drive service not initialized")
            val folderId = ensureNotesFolder()

            // Skip metadata file
            val query = "'$folderId' in parents and name != '$METADATA_FILENAME' and " +
                    "modifiedTime > '${formatDateForDriveQuery(since)}' and trashed = false"

            val result = service.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name, modifiedTime, mimeType)")
                .execute()

            return@withContext result.files.map { file ->
                DriveFileInfo(
                    id = file.id,
                    name = file.name,
                    modifiedTime = Date(file.modifiedTime.value),
                    mimeType = file.mimeType
                )
            }
        }
    }

    /**
     * Creates a new note file
     */
    suspend fun createNoteFile(noteId: String, content: String, title: String): DriveFileInfo {
        return withContext(Dispatchers.IO) {
            val service = driveService ?: throw IllegalStateException("Drive service not initialized")
            val folderId = ensureNotesFolder()

            // Include note ID in filename for easier searching
            val filename = "${noteId}_${sanitizeFileName(title)}.json"

            val fileMetadata = File()
                .setName(filename)
                .setParents(listOf(folderId))
                .setMimeType("application/json")

            val contentStream = ByteArrayInputStream(content.toByteArray())

            val file = service.files().create(fileMetadata,
                com.google.api.client.http.InputStreamContent("application/json", contentStream))
                .setFields("id, name, modifiedTime, mimeType")
                .execute()

            return@withContext DriveFileInfo(
                id = file.id,
                name = file.name,
                modifiedTime = Date(file.modifiedTime.value),
                mimeType = file.mimeType
            )
        }
    }

    /**
     * Updates an existing file
     */
    suspend fun updateFile(fileId: String, content: String, title: String? = null): DriveFileInfo {
        return withContext(Dispatchers.IO) {
            val service = driveService ?: throw IllegalStateException("Drive service not initialized")

            // Create file metadata with optional name change
            val fileMetadata = File()
            if (title != null) {
                fileMetadata.name = title
            }

            val contentStream = ByteArrayInputStream(content.toByteArray())

            val file = service.files().update(fileId, fileMetadata,
                com.google.api.client.http.InputStreamContent("application/json", contentStream))
                .setFields("id, name, modifiedTime, mimeType")
                .execute()

            return@withContext DriveFileInfo(
                id = file.id,
                name = file.name,
                modifiedTime = Date(file.modifiedTime.value),
                mimeType = file.mimeType
            )
        }
    }

    /**
     * Downloads a file's content
     */
    suspend fun downloadFile(fileId: String): String {
        return withContext(Dispatchers.IO) {
            val service = driveService ?: throw IllegalStateException("Drive service not initialized")

            val outputStream = ByteArrayOutputStream()
            service.files().get(fileId)
                .executeMediaAndDownloadTo(outputStream)

            return@withContext String(outputStream.toByteArray())
        }
    }

    /**
     * Deletes a file
     */
    suspend fun deleteFile(fileId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val service = driveService ?: throw IllegalStateException("Drive service not initialized")

            try {
                service.files().delete(fileId).execute()
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Formats a date for Drive query
     */
    private fun formatDateForDriveQuery(date: Date): String {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return format.format(date)
    }

    /**
     * Sanitizes filename for storage
     */
    private fun sanitizeFileName(name: String): String {
        return name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
    }
}