package com.wyldsoft.notes.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.wyldsoft.notes.classes.DrawCanvas
import com.wyldsoft.notes.classes.PageView
import com.wyldsoft.notes.utils.EditorState
import com.wyldsoft.notes.settings.SettingsRepository
import com.wyldsoft.notes.templates.TemplateRenderer

@Composable
@ExperimentalComposeUiApi
fun EditorSurface(
    state: EditorState,
    page: PageView,
    settingsRepository: SettingsRepository,
    templateRenderer: TemplateRenderer
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
                templateRenderer
            ).apply {
                init()
                registerObservers()
            }
        })
    }
}