// app/src/main/java/com/wyldsoft/notes/views/Router.kt
package com.wyldsoft.notes.views

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@ExperimentalFoundationApi
@ExperimentalComposeUiApi
@Composable
fun Router() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(route = "home") {
            HomeView(navController)
        }

        // Editor without a specific note ID (creates a new page)
        composable(route = "editor") {
            EditorView()
        }

        // Editor with a specific page ID (opens existing page)
        composable(
            route = "editor/{pageId}",
            arguments = listOf(navArgument("pageId") { type = NavType.StringType })
        ) { backStackEntry ->
            val pageId = backStackEntry.arguments?.getString("pageId")
            EditorView(noteId = pageId)
        }

        // Folder view
        composable(
            route = "folder/{folderId}",
            arguments = listOf(navArgument("folderId") { type = NavType.StringType })
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getString("folderId")
            HomeView(navController, initialFolderId = folderId)
        }

        // Notebook view
        composable(
            route = "notebook/{notebookId}",
            arguments = listOf(navArgument("notebookId") { type = NavType.StringType })
        ) { backStackEntry ->
            val notebookId = backStackEntry.arguments?.getString("notebookId")
            HomeView(navController, initialNotebookId = notebookId)
        }
    }
}