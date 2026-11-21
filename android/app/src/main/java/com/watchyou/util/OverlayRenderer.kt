package com.watchyou.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.watchyou.detector.DetectionResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object OverlayRenderer {
    private val boxPaint = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 6f; isAntiAlias = true }
    private val textPaint = Paint().apply { color = Color.WHITE; textSize = 48f; isAntiAlias = true }
    private val timePaint = Paint().apply { color = Color.RED; textSize = 48f; isAntiAlias = true }
    private val bgPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
    private val classColors = mapOf(
        "person" to Color.RED,
        "bird" to Color.CYAN,
        "cat" to Color.MAGENTA,
        "dog" to Color.YELLOW,
    )
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun drawOverlay(canvas: Canvas, detections: List<DetectionResult>, bitmapWidth: Int) {
        for (d in detections) {
            boxPaint.color = classColors[d.className] ?: Color.WHITE
            val left = d.x1.toFloat()
            val top = d.y1.toFloat()
            val right = d.x2.toFloat()
            val bottom = d.y2.toFloat()
            canvas.drawRect(left, top, right, bottom, boxPaint)
            val label = d.className + " " + String.format(Locale.ROOT, "%.0f%%", d.confidence * 100)
            val textBounds = Rect()
            textPaint.getTextBounds(label, 0, label.length, textBounds)
            val textWidth = textBounds.width().toFloat()
            val textHeight = textBounds.height().toFloat()
            val x = left
            val y = (top - 10).coerceAtLeast(textHeight + 10f)
            canvas.drawRect(x, y - textHeight, x + textWidth + 10, y + 5, bgPaint)
            canvas.drawText(label, x + 5, y, textPaint)
        }
        val timeStr = timeFormat.format(Date())
        val textBounds = Rect()
        timePaint.getTextBounds(timeStr, 0, timeStr.length, textBounds)
        val textWidth = textBounds.width().toFloat()
        val textHeight = textBounds.height().toFloat()
        val x = (bitmapWidth - textWidth - 20).toFloat()
        val y = textHeight + 20f
        canvas.drawRect(x - 10, y - textHeight - 5, x + textWidth + 10, y + 5, bgPaint)
        canvas.drawText(timeStr, x, y, timePaint)
    }
}
