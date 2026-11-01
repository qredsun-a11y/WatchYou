package com.watchyou.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StorageUtil {
    private const val TAG = "StorageUtil"
    private const val APP_DIR_NAME = "WatchYou"
    private const val FRAMES_DIR = "frames"
    private const val VIDEOS_DIR = "videos"
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    fun getBaseDir(context: Context): File {
        val publicPicturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        return File(publicPicturesDir, APP_DIR_NAME).apply { mkdirs() }
    }

    fun getFramesDir(context: Context): File {
        return File(getBaseDir(context), FRAMES_DIR).apply { mkdirs() }
    }

    fun getVideosDir(context: Context): File {
        val publicMoviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        return File(publicMoviesDir, APP_DIR_NAME + "/" + VIDEOS_DIR).apply { mkdirs() }
    }

    fun getFrameFile(context: Context, timestamp: String): File {
        return File(getFramesDir(context), "${timestamp}.jpg")
    }

    fun getVideoFile(context: Context, timestamp: String): File {
        return File(getVideosDir(context), "${timestamp}.mp4")
    }

    fun saveFrameToMediaStore(context: Context, bitmap: android.graphics.Bitmap, timestamp: String): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "${timestamp}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + APP_DIR_NAME + "/" + FRAMES_DIR)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                try {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(it, contentValues, null, null)
                    Log.d(TAG, "Frame saved to MediaStore: $it")
                    return it
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save frame to MediaStore", e)
                    resolver.delete(it, null, null)
                }
            }
        }
        return null
    }

    fun saveVideoToMediaStore(context: Context, videoFile: File, timestamp: String): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && videoFile.exists()) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "${timestamp}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/" + APP_DIR_NAME + "/" + VIDEOS_DIR)
                put(MediaStore.Video.Media.IS_PENDING, 1)
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Video.Media.DURATION, 0)
                put(MediaStore.Video.Media.SIZE, videoFile.length())
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                try {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        FileInputStream(videoFile).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(it, contentValues, null, null)
                    Log.d(TAG, "Video saved to MediaStore: $it")
                    return it
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save video to MediaStore", e)
                    resolver.delete(it, null, null)
                }
            }
        }
        return null
    }

    fun cleanOldFiles(context: Context, maxAgeDays: Int = 7) {
        val baseDir = getBaseDir(context)
        val cutoff = System.currentTimeMillis() - maxAgeDays * 24L * 60 * 60 * 1000
        baseDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.lastModified() < cutoff) {
                        file.delete()
                    }
                }
            }
        }
    }
}
