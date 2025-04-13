package com.wyldsoft.notes.utils

enum class Pen(val penName: String) {
    BALLPEN("BALLPEN"),
    MARKER("MARKER"),
    FOUNTAIN("FOUNTAIN");

    companion object {
        fun fromString(name: String?): Pen {
            return entries.find { it.penName.equals(name, ignoreCase = true) } ?: BALLPEN
        }
    }
}

@kotlinx.serialization.Serializable
data class PenSetting(
    var strokeSize: Float,
    var color: Int
)

typealias NamedSettings = Map<String, PenSetting>