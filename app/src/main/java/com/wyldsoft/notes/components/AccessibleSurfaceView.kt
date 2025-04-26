// app/src/main/java/com/wyldsoft/notes/components/AccessibleSurfaceView.kt
package com.wyldsoft.notes.components

import android.content.Context
import android.view.SurfaceView
import android.view.View

/**
 * A SurfaceView that properly implements performClick for accessibility support.
 */
class AccessibleSurfaceView(context: Context) : SurfaceView(context) {

    override fun performClick(): Boolean {
        // Call the super implementation to handle the click
        val handled = super.performClick()

        // For debugging
        println("DEBUG: performClick called, result: $handled")

        return handled
    }
}