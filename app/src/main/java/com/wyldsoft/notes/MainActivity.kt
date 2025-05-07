package com.wyldsoft.notes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.wyldsoft.notes.ui.theme.NotesTheme
import com.wyldsoft.notes.classes.drawing.DrawingManager
import com.wyldsoft.notes.classes.LocalSnackContext
import com.wyldsoft.notes.classes.SnackBar
import com.wyldsoft.notes.classes.SnackState
import com.wyldsoft.notes.components.ConflictResolutionDialog
import com.wyldsoft.notes.sync.Resolution
import com.wyldsoft.notes.views.Router
import kotlinx.coroutines.launch
import com.onyx.android.sdk.api.device.epd.EpdController


var SCREEN_WIDTH = EpdController.getEpdHeight().toInt()
var SCREEN_HEIGHT = EpdController.getEpdWidth().toInt()

@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@ExperimentalFoundationApi
class MainActivity : ComponentActivity() {
    private lateinit var app: NotesApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableFullScreen()
        requestPermissions()

        app = application as NotesApp

        // Initialize screen dimensions if needed
        if (SCREEN_WIDTH == 0) {
            SCREEN_WIDTH = resources.displayMetrics.widthPixels
            SCREEN_HEIGHT = resources.displayMetrics.heightPixels
        }

        val snackState = SnackState()
        snackState.registerGlobalSnackObserver()

        setContent {
            NotesTheme {
                CompositionLocalProvider(LocalSnackContext provides snackState) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colors.background
                    ) {
                        Box(
                            Modifier
                                .background(Color.White)
                                .fillMaxSize()
                        ) {
                            Router()
                            SnackBar(state = snackState)

                            // Google Sign-in launcher
                            val signInLauncher = rememberLauncherForActivityResult(
                                contract = ActivityResultContracts.StartActivityForResult()
                            ) { result ->
                                lifecycleScope.launch {
                                    app.driveServiceWrapper.handleSignInResult(result.data)
                                }
                            }

                            // Observe conflicts
                            val conflictResolver = app.syncManager.conflictResolver
                            val conflict by conflictResolver.conflictToResolve.collectAsState()

                            // Show conflict resolution dialog if needed
                            if (conflict != null) {
                                ConflictResolutionDialog(
                                    conflict = conflict!!,
                                    onResolution = { resolution ->
                                        conflictResolver.provideResolution(conflict!!.localNote.id, resolution)
                                    },
                                    onDismiss = {
                                        // Default to using local version if dismissed
                                        conflictResolver.provideResolution(conflict!!.localNote.id, Resolution.UseLocal)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onRestart() {
        super.onRestart()
        // Redraw after device sleep
        this.lifecycleScope.launch {
            DrawingManager.restartAfterConfChange.emit(Unit)
        }
    }

    override fun onPause() {
        super.onPause()
        this.lifecycleScope.launch {
            DrawingManager.refreshUi.emit(Unit)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableFullScreen()
        }
        this.lifecycleScope.launch {
            DrawingManager.refreshUi.emit(Unit)
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    1001
                )
            }
        } else if (!Environment.isExternalStorageManager()) {
            val intent = android.content.Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = android.net.Uri.fromParts("package", packageName, null)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
    }

    private fun enableFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)

            // Safely access window.insetsController, which might be null
            window.decorView.post {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.insetsController?.let {
                        it.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                        it.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                }
            }
        } else {
            // For devices running Android 10 (Q) or below
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }
}