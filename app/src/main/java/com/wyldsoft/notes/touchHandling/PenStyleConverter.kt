package com.wyldsoft.notes.touchHandling

import com.wyldsoft.notes.utils.Pen

/**
 * Converts pen types to Onyx SDK stroke styles.
 * Handles mapping between internal pen types and platform-specific implementations.
 */
class PenStyleConverter {
    companion object {
        // Map each pen type to the corresponding Onyx SDK stroke style
        private val penStyleMap = mapOf(
            Pen.BALLPEN to com.onyx.android.sdk.pen.style.StrokeStyle.PENCIL,
            Pen.MARKER to com.onyx.android.sdk.pen.style.StrokeStyle.MARKER,
            Pen.FOUNTAIN to com.onyx.android.sdk.pen.style.StrokeStyle.FOUNTAIN
        )

        /**
         * Converts internal pen type to Onyx SDK stroke style
         */
        fun convertPenToStrokeStyle(pen: Pen): Int {
            return penStyleMap[pen] ?: com.onyx.android.sdk.pen.style.StrokeStyle.PENCIL
        }
    }
}