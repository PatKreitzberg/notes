package com.wyldsoft.notes.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.wyldsoft.notes.drawing.DrawCanvas
import com.wyldsoft.notes.views.PageView
import com.wyldsoft.notes.utils.EditorState
import com.wyldsoft.notes.settings.SettingsRepository
import com.wyldsoft.notes.templates.TemplateRenderer
import com.wyldsoft.notes.search.SearchManager

@Composable
@ExperimentalComposeUiApi
fun EditorSurface(
    state: EditorState,
    page: PageView,
    settingsRepository: SettingsRepository,
    templateRenderer: TemplateRenderer,
    searchManager: SearchManager? = null
) {
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        AndroidView(factory = { ctx ->
            DrawCanvas(
                ctx,
                coroutineScope,
                state,
                page,
                settingsRepository,
                templateRenderer,
                searchManager // Pass the search manager
            ).apply {
                init()
                registerObservers()
            }
        })
    }
}