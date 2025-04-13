package com.wyldsoft.notes.utils

import java.util.Date
import java.util.UUID

data class StrokePoint(
    val x: Float,
    var y: Float,
    val pressure: Float,
    val size: Float,
    val tiltX: Int,
    val tiltY: Int,
    val timestamp: Long,
)

data class Stroke(
    val id: String = UUID.randomUUID().toString(),
    val size: Float,
    val pen: Pen,
    val color: Int = 0xFF000000.toInt(),
    var top: Float,
    var bottom: Float,
    var left: Float,
    var right: Float,
    val points: List<StrokePoint>,
    val pageId: String,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)