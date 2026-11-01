package com.watchyou.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.watchyou.MainActivity
import com.watchyou.R
import com.watchyou.detector.DetectionResult
import com.watchyou.detector.ObjectDetector
import com.watchyou.media.SoundPlayer
import com.watchyou.recorder.FrameSaver
import com.watchyou.recorder.VideoRecorder
import com.watchyou.util.Logger
import com.watchyou.util.StorageUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DetectionService : LifecycleService() {
    companion object {
        private const val TAG = "DetectionService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "watchyou_channel"
        private const val CONFIDENCE_THRESHOLD = 0.5f
        private const val COOLDOWN_MS = 3000L
        private const val RECORD_DURATION_MS = 5000L
        private const val PREVIEW_WIDTH = 640
        private const val PREVIEW_HEIGHT = 480
        private const val DETECTION_WIDTH = 320
        private const val DETECTION_HEIGHT = 240
        private const val DETECTION_INTERVAL_MS = 300
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var objectDetector: ObjectDetector? = null
    private var videoRecorder: VideoRecorder? = null
    private var soundPlayer: SoundPlayer? = null
    private var notificationManager: NotificationManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    private var lastDetectionTime = 0L
    private var isRecording = false
    private var recordingEndTime = 0L
    
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val initExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val detectionHandler: Handler = Handler(HandlerThread("DetectionThread").apply { start() }.looper)
    private var detectionRunnable: Runnable? = null
    private var previewBitmap: Bitmap? = null
    
    private val scope = CoroutineScope(Dispatchers.Default)
    private var detectionJob: Job? = null
    private var isServiceRunning = false
    private var cameraStarted = false
    private var cameraLifecycleRef: androidx.lifecycle.LifecycleOwner? = null

    override fun onCreate() {
        super.onCreate()
        Logger.i(TAG, "onCreate called - minimal setup only, lifecycle state: " + lifecycle.currentState)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                Logger.i(TAG, "LifecycleObserver onStart - starting camera")
                if (!cameraStarted) {
                    startCamera()
                }
            }
            override fun onStop(owner: LifecycleOwner) {
                Logger.i(TAG, "LifecycleObserver onStop - stopping camera")
                cameraProvider?.unbindAll()
                cameraStarted = false
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.i(TAG, "onStartCommand called - THIS IS THE ENTRY POINT, lifecycle state: " + lifecycle.currentState)
        
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        createNotificationChannel()
        
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WatchYouWakeLock")
        wakeLock?.acquire(10000.toLong())
        
        startForeground(NOTIFICATION_ID, buildNotification("正在启动..."))
        Logger.i(TAG, "startForeground called - service is now visible to system")
        
        objectDetector = ObjectDetector(this)
        videoRecorder = VideoRecorder(this)
        soundPlayer = SoundPlayer(this)
        
        val result = super.onStartCommand(intent, flags, startId)
        Logger.i(TAG, "onStartCommand completed - service should now persist")
        return result
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WatchYou Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "监控通知渠道"
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WatchYou 监控中")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        notificationManager?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun startCameraWithLifecycle(cameraLifecycle: androidx.lifecycle.LifecycleOwner) {
        Logger.i(TAG, "startCameraWithLifecycle called")
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> = 
            ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try { 
                cameraProvider = cameraProviderFuture.get(10, TimeUnit.SECONDS)
                Logger.i(TAG, "CameraProvider obtained, binding use cases...")
                Handler(Looper.getMainLooper()).post { bindCameraUseCases(cameraLifecycle) }
            }
            catch (e: Exception) { Log.e(TAG, "Camera start failed", e) }
        }, initExecutor)
    }
    
    private fun startCamera() {
        Logger.i(TAG, "startCamera called (legacy)")
        startCameraWithLifecycle(this)
    }

    private fun bindCameraUseCases(cameraLifecycle: androidx.lifecycle.LifecycleOwner) {
        Logger.i(TAG, "bindCameraUseCases called with custom lifecycle")
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(DETECTION_WIDTH, DETECTION_HEIGHT))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build().also { it.setAnalyzer(cameraExecutor, ::analyzeImage) }
        
        try { 
            Logger.i(TAG, "Attempting to bind to service lifecycle...")
            cameraProvider?.bindToLifecycle(cameraLifecycle, cameraSelector, imageAnalysis)
            Logger.i(TAG, "Camera bind successful")
        }
        catch (e: Exception) { Log.e(TAG, "Camera bind failed", e) }
    }
    
    private fun analyzeImage(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        Logger.d(TAG, "analyzeImage called, interval check: " + (now - lastDetectionTime))
        if (now - lastDetectionTime < DETECTION_INTERVAL_MS) { imageProxy.close(); return }
        
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        Logger.d(TAG, "Image available: width=" + imageProxy.width + ", height=" + imageProxy.height)
        
        val yBuffer = mediaImage.planes[0].buffer
        val yPixelStride = mediaImage.planes[0].pixelStride
        val yRowStride = mediaImage.planes[0].rowStride
        
        val bitmap = Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
        
        yBuffer.rewind()
        val yData = ByteArray(yBuffer.remaining())
        yBuffer.get(yData)
        Logger.d(TAG, "Y data loaded: " + yData.size + " bytes")
        
        val rgbArray = IntArray(imageProxy.width * imageProxy.height)
        
        for (y in 0 until imageProxy.height) {
            for (x in 0 until imageProxy.width) {
                val yValue = yData[y * imageProxy.width + x].toInt() and 0xFF
                rgbArray[y * imageProxy.width + x] = 0xFF000000.toInt() or (yValue shl 16) or (yValue shl 8) or yValue
            }
        }
        
        bitmap.setPixels(rgbArray, 0, imageProxy.width, 0, 0, imageProxy.width, imageProxy.height)
        
        previewBitmap = bitmap
        Logger.d(TAG, "Starting detection...")
        scope.launch(Dispatchers.Default) { detectAndProcess(bitmap) }
        imageProxy.close()
    }

    private fun detectAndProcess(bitmap: Bitmap) {
        val detector = objectDetector
        if (detector == null) {
            Logger.w(TAG, "ObjectDetector not initialized")
            return
        }
        val detections = detector.detect(bitmap)
        Logger.d(TAG, "Detections: ")
        for (d in detections) {
            if (d.confidence >= CONFIDENCE_THRESHOLD) {
                Logger.i(TAG, "Detected:  conf= bbox=(,,,)")
                handleDetection(d)
                break
            }
        }
    }

    private fun handleDetection(detection: DetectionResult) {
        val now = System.currentTimeMillis()
        if (now - lastDetectionTime < COOLDOWN_MS) {
            Logger.d(TAG, "Cooldown active, skipping")
            return
        }
        lastDetectionTime = now
        updateNotification("检测到: ")
        
        // Save screenshot (frame) to public Pictures/WatchYou/frames/
        previewBitmap?.let {
            FrameSaver.save(this@DetectionService, it)
            Logger.i(TAG, "Screenshot saved")
        }
        
        if (!isRecording) {
            startRecording()
        }
        recordingEndTime = now + RECORD_DURATION_MS
        detectionHandler.removeCallbacksAndMessages(null)
        detectionRunnable = object : Runnable {
            override fun run() {
                if (System.currentTimeMillis() >= recordingEndTime) {
                    stopRecording()
                } else {
                    detectionHandler.postDelayed(this, 500)
                }
            }
        }
        detectionHandler.post(detectionRunnable!!)
    }

    private fun startRecording() {
        if (isRecording || videoRecorder == null) return
        val dir = StorageUtil.getVideosDir(this)
        val fileName = "det_.mp4"
        val file = java.io.File(dir, fileName)
        val success = videoRecorder?.startRecording(PREVIEW_WIDTH, PREVIEW_HEIGHT) ?: false
        if (success) {
            isRecording = true
            soundPlayer?.playAlert()
            Logger.i(TAG, "Recording started: ")
        }
    }

    private fun stopRecording() {
        if (!isRecording || videoRecorder == null) return
        videoRecorder?.stopRecording()
        isRecording = false
        detectionHandler.removeCallbacksAndMessages(null)
        updateNotification("监控中...")
        Logger.i(TAG, "Recording stopped")
    }

    override fun onDestroy() {
        Logger.i(TAG, "onDestroy called")
        super.onDestroy()
        detectionHandler.removeCallbacksAndMessages(null)
        cameraProvider?.unbindAll()
        objectDetector?.close()
        videoRecorder?.stopRecording()
        wakeLock?.release()
        
        
    }
}
