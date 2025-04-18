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

@Composable
@ExperimentalComposeUiApi
fun EditorSurface(
    state: EditorState, page: PageView
) {
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        AndroidView(factory = { ctx ->
            val canvas = DrawCanvas(ctx, coroutineScope, state, page)
            canvas.init()
            // Now we can call initGestureDetector without parameters
            canvas.initGestureDetector()
            canvas.registerObservers()
            canvas
        })
    }
}