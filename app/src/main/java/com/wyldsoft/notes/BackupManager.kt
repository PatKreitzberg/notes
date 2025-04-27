// app/src/main/java/com/wyldsoft/notes/backup/BackupManager.kt
package com.wyldsoft.notes.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.wyldsoft.notes.services.GoogleDriveBackupService
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * BackupManager handles backup operations and coordinates with the UI.
 * It wraps the GoogleDriveBackupService to provide a simplified interface.
 */
class BackupManager(private val context: Context) {
    private val googleDriveService = GoogleDriveBackupService(context)

    // Expose the backup state and error message for the UI
    val backupState: StateFlow<GoogleDriveBackupService.BackupState> = googleDriveService.backupState
    val errorMessage: StateFlow<String?> = googleDriveService.errorMessage

    // Check if user is signed in to Google Drive
    fun isSignedIn(): Boolean {
        return googleDriveService.isSignedIn()
    }

    // Initiate sign in process
    fun signIn(activity: Activity, launcher: ActivityResultLauncher<Intent>) {
        googleDriveService.setupGoogleSignIn(activity)
        googleDriveService.signIn(activity, launcher)
    }

    // Handle sign in result
    fun handleSignInResult(data: Intent?) {
        googleDriveService.handleSignInResult(data)
    }

    // Sign out from Google account
    suspend fun signOut() {
        googleDriveService.signOut()
    }

    // Perform backup operation
    suspend fun performBackup() {
        googleDriveService.backupDatabase()
    }

    // Reset state after operations
    fun resetState() {
        googleDriveService.resetState()
    }
}

/**
 * Composable function to remember and provide the BackupManager
 */
@Composable
fun rememberBackupManager(context: Context): BackupManager {
    return remember { BackupManager(context) }
}