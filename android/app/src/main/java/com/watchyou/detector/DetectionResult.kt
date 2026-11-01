package com.watchyou.detector

data class DetectionResult(
    val className: String,
    val confidence: Float,
    val x1: Int,
    val y1: Int,
    val x2: Int,
    val y2: Int
) {
    val width: Int get() = x2 - x1
    val height: Int get() = y2 - y1
    val centerX: Int get() = (x1 + x2) / 2
    val centerY: Int get() = (y1 + y2) / 2
}
