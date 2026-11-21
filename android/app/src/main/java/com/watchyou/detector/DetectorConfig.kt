package com.watchyou.detector

/**
 * UI 可配置的参数集合（来自 SharedPreferences）。
 * 由 DetectionService.loadConfig() 构建，传给 ObjectDetector 构造函数。
 */
data class DetectorConfig(
    val confidenceThreshold: Float = 0.5f,
    val iouThreshold: Float = 0.45f,
    val minBoxAreaRatio: Float = 0.02f,
    val recordDurationMs: Long = 10000L,
    val noTargetTimeoutMs: Long = 1500L,
    val detectionIntervalMs: Long = 1000L,
    val targetClasses: Set<Int> = setOf(0, 14, 15, 16),
    val showWatermark: Boolean = true,
    val watermarkText: String = "WatchYou",
    val showTimestamp: Boolean = true,
    val numThreads: Int = 2,
    val useNnapi: Boolean = false
)
