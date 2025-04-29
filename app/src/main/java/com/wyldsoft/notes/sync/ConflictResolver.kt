// app/src/main/java/com/wyldsoft/notes/sync/ConflictResolver.kt (fixed)
package com.wyldsoft.notes.sync

import android.content.Context
import androidx.compose.runtime.Composable
import com.wyldsoft.notes.database.entity.NoteEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Resolution type for conflicts
 */
sealed class Resolution {
    /**
     * Use the local version
     */
    object UseLocal : Resolution()

    /**
     * Use the remote version
     */
    object UseRemote : Resolution()

    /**
     * Keep both versions (create a copy)
     */
    object KeepBoth : Resolution()
}

/**
 * Information about a conflict
 */
data class NoteConflict(
    val localNote: NoteEntity,
    val remoteNote: NoteEntity
)

/**
 * Handles conflict resolution for sync conflicts
 */
class ConflictResolver(private val context: Context) {

    // Track active conflicts
    private val activeConflicts = mutableMapOf<String, CompletableDeferred<Resolution>>()

    // For UI to observe - switched to StateFlow
    private val _conflictToResolve = MutableStateFlow<NoteConflict?>(null)
    val conflictToResolve: StateFlow<NoteConflict?> = _conflictToResolve.asStateFlow()

    /**
     * Resolves a conflict between local and remote versions
     */
    suspend fun resolveConflict(
        localNote: NoteEntity,
        remoteNote: NoteEntity
    ): Resolution {
        // Check timestamps first for automatic resolution
        val localUpdateTime = localNote.updatedAt.time
        val remoteUpdateTime = remoteNote.updatedAt.time

        // If timestamps differ by more than 5 minutes, use the newer one
        if (Math.abs(localUpdateTime - remoteUpdateTime) > 5 * 60 * 1000) {
            return if (localUpdateTime > remoteUpdateTime) {
                Resolution.UseLocal
            } else {
                Resolution.UseRemote
            }
        }

        // If timestamps are close, ask the user
        return requestUserResolution(NoteConflict(localNote, remoteNote))
    }

    /**
     * Requests user input for conflict resolution
     */
    private suspend fun requestUserResolution(conflict: NoteConflict): Resolution {
        return suspendCancellableCoroutine { continuation ->
            // Store the deferred for use from the UI
            val deferred = CompletableDeferred<Resolution>()
            activeConflicts[conflict.localNote.id] = deferred

            // Set conflict for UI
            _conflictToResolve.value = conflict

            // Wait for result
            deferred.invokeOnCompletion { throwable ->
                if (throwable != null) {
                    // Default to using local if there's an error
                    continuation.resume(Resolution.UseLocal)
                } else {
                    // Continue with the resolution
                    continuation.resume(deferred.getCompleted())
                }

                // Clear conflict state
                if (_conflictToResolve.value?.localNote?.id == conflict.localNote.id) {
                    _conflictToResolve.value = null
                }

                // Remove deferred
                activeConflicts.remove(conflict.localNote.id)
            }

            // Handle cancellation
            continuation.invokeOnCancellation {
                deferred.cancel()
                _conflictToResolve.value = null
                activeConflicts.remove(conflict.localNote.id)
            }
        }
    }

    /**
     * Provides a resolution for a conflict (called from UI)
     */
    fun provideResolution(noteId: String, resolution: Resolution) {
        activeConflicts[noteId]?.complete(resolution)
    }
}