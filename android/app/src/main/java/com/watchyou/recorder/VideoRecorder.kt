package com.watchyou.recorder

import android.content.ContentValues
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.watchyou.util.Logger
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoRecorder(private val context: Context) {
    companion object {
        private const val TAG = "VideoRecorder"
        private const val RECORD_FPS = 10
        private const val VIDEO_BITRATE = 2_000_000
    }

    private var mediaRecorder: MediaRecorder? = null
    private var currentVideoUri: Uri? = null
    private var isRecording = false
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    fun startRecording(width: Int, height: Int): Boolean {
        if (isRecording) return false

        val timestamp = dateFormat.format(Date())
        
        // Use MediaStore for Android 10+ to get a valid file descriptor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "${timestamp}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/WatchYou/videos")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            currentVideoUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            
            if (currentVideoUri == null) {
                Logger.e(TAG, "Failed to create MediaStore entry")
                return false
            }
        } else {
            // Legacy: use file path (pre-Android 10)
            val publicMoviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val file = java.io.File(publicMoviesDir, "WatchYou/videos/" + timestamp + ".mp4")
            file.parentFile?.mkdirs()
            currentVideoUri = Uri.fromFile(file)
        }

        mediaRecorder = MediaRecorder().apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use file descriptor from MediaStore URI
                val fd = context.contentResolver.openFileDescriptor(currentVideoUri!!, "rw")?.fileDescriptor
                if (fd != null) {
                    setOutputFile(fd)
                } else {
                    Logger.e(TAG, "Failed to open file descriptor")
                }
            } else {
                setOutputFile(currentVideoUri!!.path)
            }
            setVideoEncodingBitRate(VIDEO_BITRATE)
            setVideoFrameRate(RECORD_FPS)
            setVideoSize(width, height)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        }

        return try {
            mediaRecorder!!.prepare()
            mediaRecorder!!.start()
            isRecording = true
            Logger.d(TAG, "Recording started: $currentVideoUri")
            true
        } catch (e: IOException) {
            Logger.e(TAG, "Failed to start recording", e)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && currentVideoUri != null) {
                context.contentResolver.delete(currentVideoUri!!, null, null)
            }
            release()
            false
        }
    }

    fun stopRecording(): String? {
        if (!isRecording || mediaRecorder == null) return null

        try {
            mediaRecorder!!.stop()
        } catch (e: Exception) {
            Logger.e(TAG, "Error stopping recorder", e)
        } finally {
            release()
        }
        
        // Finalize MediaStore entry (Android 10+)
        val resultUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && currentVideoUri != null) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            }
            context.contentResolver.update(currentVideoUri!!, contentValues, null, null)
            Logger.d(TAG, "Video saved to MediaStore: $currentVideoUri")
            currentVideoUri.toString()
        } else {
            // Legacy fallback
            val path = currentVideoUri?.path ?: ""
            scanFile(path)
            Logger.d(TAG, "Recording stopped: $path")
            path
        }
        
        return resultUri
    }

    fun getIsRecording(): Boolean = isRecording

    private fun release() {
        mediaRecorder?.reset()
        mediaRecorder?.release()
        mediaRecorder = null
        currentVideoUri = null
        isRecording = false
    }

    private fun scanFile(path: String) {
        android.media.MediaScannerConnection.scanFile(context, arrayOf(path), null) { p, uri ->
            Logger.d(TAG, "Scanned video: $p -> $uri")
        }
    }
}
