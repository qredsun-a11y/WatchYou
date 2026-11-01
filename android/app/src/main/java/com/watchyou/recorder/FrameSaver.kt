package com.watchyou.recorder

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import com.watchyou.util.StorageUtil
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FrameSaver {
    private const val TAG = "FrameSaver"
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    fun save(context: Context, bitmap: Bitmap): String {
        val timestamp = dateFormat.format(Date())
        
        // Try MediaStore first (Android 10+) for gallery visibility
        val mediaStoreUri = StorageUtil.saveFrameToMediaStore(context, bitmap, timestamp)
        if (mediaStoreUri != null) {
            Log.d(TAG, "Frame saved to MediaStore: $mediaStoreUri")
            return mediaStoreUri.toString()
        }
        
        // Fallback to file system (pre-Android 10)
        val file = StorageUtil.getFrameFile(context, timestamp)
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            scanFile(context, file)
            Log.d(TAG, "Frame saved to file: ${file.absolutePath}")
            return file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save frame", e)
            return ""
        }
    }

    private fun scanFile(context: Context, file: java.io.File) {
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null) { path, uri ->
            Log.d(TAG, "Scanned: $path -> $uri")
        }
    }
}
