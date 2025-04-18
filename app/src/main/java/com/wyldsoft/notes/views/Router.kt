package com.wyldsoft.notes.views

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wyldsoft.notes.services.GoogleDriveBackupService

@ExperimentalFoundationApi
@ExperimentalComposeUiApi
@Composable
fun Router() {
    val navController = rememberNavController()
    val backupService = GoogleDriveBackupService(LocalContext.current)

    NavHost(
        navController = navController,
        startDestination = "notebooks",
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        // List of notebooks
        composable(route = "notebooks") {
            NotebookListView(navController)
        }

        // Notebook detail with pages
        composable(
            route = "notebook/{notebookId}",
            arguments = listOf(navArgument("notebookId") { type = NavType.StringType })
        ) { backStackEntry ->
            val notebookId = backStackEntry.arguments?.getString("notebookId") ?: return@composable
            NotebookDetailView(navController, notebookId)
        }

        // Editor for a specific page
        composable(
            route = "editor/{pageId}",
            arguments = listOf(navArgument("pageId") { type = NavType.StringType })
        ) { backStackEntry ->
            val pageId = backStackEntry.arguments?.getString("pageId") ?: return@composable
            EditorView(navController, pageId)
        }

        // Google Drive backup screen
        composable(route = "backup") {
            BackupScreen(navController = navController, backupService = backupService)
        }

        // Settings screen
        composable(route = "settings") {
            SettingsView(navController)
        }

        // Gesture Settings screen - new!
        composable(route = "gesture_settings") {
            GestureSettingsView(navController)
        }
    }
}