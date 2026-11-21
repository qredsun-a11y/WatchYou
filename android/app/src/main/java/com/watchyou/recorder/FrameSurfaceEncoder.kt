package com.watchyou.recorder

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import android.view.Surface
import com.watchyou.util.Logger
import com.watchyou.util.StorageUtil
import java.io.File
import java.nio.ByteBuffer

class FrameSurfaceEncoder(
    private val output: Any,
    private val width: Int,
    private val height: Int,
    private val frameRate: Int = 10,
    private val context: Context? = null,
    private val mediaUri: Uri? = null,
    private val timestamp: String? = null
) {
    companion object {
        private const val TAG = "FrameSurfaceEncoder"
        private const val MIME_TYPE = "video/avc"
        private const val BIT_RATE = 2_000_000
        private const val I_FRAME_INTERVAL = 2
        private const val TIMEOUT_US = 10_000L
    }

    private var mediaCodec: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    private var inputSurface: Surface? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private var started = false
    private var frameCount = 0
    private var presentationTimeUs = 0L

    /** File mode: writes to a file path directly */
    constructor(file: File, width: Int, height: Int, frameRate: Int = 10) : this(
        output = file,
        width = width,
        height = height,
        frameRate = frameRate
    )

    /** MediaStore mode: writes to a Uri obtained from StorageUtil.createVideoInMediaStore */
    constructor(context: Context, uri: Uri, width: Int, height: Int, frameRate: Int = 10, timestamp: String) : this(
        output = uri,
        width = width,
        height = height,
        frameRate = frameRate,
        context = context,
        mediaUri = uri,
        timestamp = timestamp
    )

    fun start() {
        if (started) return
        try {
            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            }
            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = createInputSurface()
                start()
            }

            // Choose muxer output target: File or FileDescriptor (MediaStore)
            when (output) {
                is File -> {
                    val file = output
                    if (file.parentFile?.exists() != true) {
                        val created = file.parentFile?.mkdirs() ?: false
                        Log.i(TAG, "Created parent dir: $created, path=${file.parentFile?.absolutePath}")
                    }
                    mediaMuxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    Logger.i(TAG, "MediaMuxer started (File mode): " + file.absolutePath)
                }
                is Uri -> {
                    val ctx = context ?: throw IllegalStateException("Context required for MediaStore mode")
                    val uri = output
                    val pfd = ctx.contentResolver.openFileDescriptor(uri, "rw")
                        ?: throw IllegalStateException("Cannot open FileDescriptor for $uri")
                    mediaMuxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    pfd.close()
                    Logger.i(TAG, "MediaMuxer started (MediaStore mode): $uri")
                }
                else -> throw IllegalStateException("Unsupported output type: ${output::class}")
            }

            started = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start encoder", e)
            stop()
            throw e
        }
    }

    /** Feed a Bitmap frame to the encoder via input surface */
    fun encodeFrame(bitmap: Bitmap) {
        val surface = inputSurface ?: return
        if (!started) return

        // Scale bitmap to encoder resolution if needed
        val scaledBitmap = if (bitmap.width != width || bitmap.height != height) {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } else bitmap

        // Draw to input surface - handle surface released race condition
        val canvas = try {
            surface.lockCanvas(null)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Surface already released, skipping frame", e)
            if (scaledBitmap !== bitmap) scaledBitmap.recycle()
            return
        } catch (e: Exception) {
            Log.w(TAG, "Failed to lock canvas", e)
            if (scaledBitmap !== bitmap) scaledBitmap.recycle()
            return
        }
        
        if (canvas == null) {
            Log.w(TAG, "Canvas is null, skipping frame")
            if (scaledBitmap !== bitmap) scaledBitmap.recycle()
            return
        }
        
        try {
            canvas.drawBitmap(scaledBitmap, 0f, 0f, null)
        } finally {
            try {
                surface.unlockCanvasAndPost(canvas)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unlock canvas", e)
            }
        }

        if (scaledBitmap !== bitmap) {
            scaledBitmap.recycle()
        }

        // Drain encoded data
        drainEncoder()
        
        presentationTimeUs += 1000000L / frameRate
        frameCount++
    }

    private fun drainEncoder() {
        val codec = mediaCodec ?: return
        val info = android.media.MediaCodec.BufferInfo()
        while (true) {
            val outputBufferIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            when {
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val format = codec.outputFormat
                    trackIndex = mediaMuxer?.addTrack(format) ?: -1
                    if (trackIndex >= 0) {
                        mediaMuxer?.start()
                        muxerStarted = true
                    }
                }
                outputBufferIndex >= 0 -> {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && muxerStarted) {
                        outputBuffer.rewind()
                        outputBuffer.limit(info.offset + info.size)
                        mediaMuxer?.writeSampleData(trackIndex, outputBuffer, info)
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                }
                else -> return
            }
        }
    }

    fun stop() {
        if (!started && mediaCodec == null) return
        try {
            // Stop MediaCodec safely
            mediaCodec?.let { codec ->
                try {
                    codec.signalEndOfInputStream()
                    drainEncoder()
                } catch (e: Exception) {
                    Log.w(TAG, "signalEndOfInputStream/drain failed", e)
                }
                try {
                    codec.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "codec.stop() failed", e)
                }
                try {
                    codec.release()
                } catch (e: Exception) {
                    Log.w(TAG, "codec.release() failed", e)
                }
            }

            // Stop MediaMuxer safely
            mediaMuxer?.let { muxer ->
                try {
                    if (muxerStarted) muxer.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "muxer.stop() failed", e)
                }
                try {
                    muxer.release()
                } catch (e: Exception) {
                    Log.w(TAG, "muxer.release() failed", e)
                }
            }

            inputSurface?.release()
            inputSurface = null

            // Finalize MediaStore record if in MediaStore mode
            if (mediaUri != null && context != null && timestamp != null) {
                if (muxerStarted && trackIndex >= 0) {
                    try {
                        StorageUtil.finalizeVideoInMediaStore(context, mediaUri)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to finalize MediaStore video", e)
                    }
                } else {
                    // No frames recorded - delete the pending MediaStore entry
                    try {
                        context.contentResolver.delete(mediaUri, null, null)
                        Logger.w(TAG, "Deleted empty MediaStore entry: $mediaUri")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to delete pending MediaStore entry", e)
                    }
                }
            }
        } finally {
            mediaCodec = null
            mediaMuxer = null
            muxerStarted = false
            started = false
            frameCount = 0
            presentationTimeUs = 0L
            Logger.d(TAG, "Encoder stopped")
        }
    }

    fun isStarted(): Boolean = started
}