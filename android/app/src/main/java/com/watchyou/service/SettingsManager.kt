package com.watchyou.service

import android.content.Context
import android.preference.PreferenceManager

/**
 * 持久化设置管理器 - 通过 SharedPreferences 读写用户配置。
 * 由 SettingsActivity 保存，DetectionService 读取。
 */
object SettingsManager {

    private const val KEY_CONFIDENCE = "cfg_confidence"
    private const val KEY_IOU = "cfg_iou"
    private const val KEY_MIN_AREA = "cfg_min_area"
    private const val KEY_RECORD_DUR = "cfg_record_duration"
    private const val KEY_NO_TARGET = "cfg_no_target_timeout"
    private const val KEY_DETECT_INTERVAL = "cfg_detection_interval"
    private const val KEY_TARGET_CLASSES = "cfg_target_classes"
    private const val KEY_SHOW_WATERMARK = "cfg_show_watermark"
    private const val KEY_WATERMARK_TEXT = "cfg_watermark_text"
    private const val KEY_SHOW_TIMESTAMP = "cfg_show_timestamp"
    private const val KEY_NUM_THREADS = "cfg_num_threads"
    private const val KEY_USE_NNAPI = "cfg_use_nnapi"

    // Defaults
    const val DEFAULT_CONFIDENCE = 0.5f
    const val DEFAULT_IOU = 0.45f
    const val DEFAULT_MIN_AREA = 0.02f
    const val DEFAULT_RECORD_DURATION_MS = 10_000L
    const val DEFAULT_NO_TARGET_TIMEOUT_MS = 1_500L
    const val DEFAULT_DETECT_INTERVAL_MS = 300L
    const val DEFAULT_NUM_THREADS = 2
    val DEFAULT_TARGET_CLASSES = setOf(0, 14, 15, 16)
    const val DEFAULT_WATERMARK_TEXT = "WatchYou"

    fun load(context: Context): Map<String, Any> {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)

        // Resolve target classes explicitly to avoid type ambiguity in mapOf
        val savedClasses = sp.getStringSet(KEY_TARGET_CLASSES, null)
        val activeClasses: Set<Int> = if (savedClasses != null && savedClasses.isNotEmpty()) {
            savedClasses.map { it.toInt() }.toSet()
        } else {
            DEFAULT_TARGET_CLASSES
        }

        return mapOf(
            KEY_CONFIDENCE   to sp.getFloat(KEY_CONFIDENCE, DEFAULT_CONFIDENCE),
            KEY_IOU          to sp.getFloat(KEY_IOU, DEFAULT_IOU),
            KEY_MIN_AREA     to sp.getFloat(KEY_MIN_AREA, DEFAULT_MIN_AREA),
            KEY_RECORD_DUR   to sp.getLong(KEY_RECORD_DUR, DEFAULT_RECORD_DURATION_MS),
            KEY_NO_TARGET    to sp.getLong(KEY_NO_TARGET, DEFAULT_NO_TARGET_TIMEOUT_MS),
            KEY_DETECT_INTERVAL to sp.getLong(KEY_DETECT_INTERVAL, DEFAULT_DETECT_INTERVAL_MS),
            KEY_TARGET_CLASSES to activeClasses,
            KEY_SHOW_WATERMARK to sp.getBoolean(KEY_SHOW_WATERMARK, false),
            KEY_WATERMARK_TEXT to (sp.getString(KEY_WATERMARK_TEXT, DEFAULT_WATERMARK_TEXT) ?: DEFAULT_WATERMARK_TEXT),
            KEY_SHOW_TIMESTAMP to sp.getBoolean(KEY_SHOW_TIMESTAMP, true),
            KEY_NUM_THREADS  to sp.getInt(KEY_NUM_THREADS, DEFAULT_NUM_THREADS),
            KEY_USE_NNAPI    to sp.getBoolean(KEY_USE_NNAPI, false)
        )
    }

    fun save(context: Context, values: Map<String, Any>) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.edit().apply {
            putFloat(KEY_CONFIDENCE,   values[KEY_CONFIDENCE] as Float)
            putFloat(KEY_IOU,          values[KEY_IOU] as Float)
            putFloat(KEY_MIN_AREA,     values[KEY_MIN_AREA] as Float)
            putLong(KEY_RECORD_DUR,    values[KEY_RECORD_DUR] as Long)
            putLong(KEY_NO_TARGET,     values[KEY_NO_TARGET] as Long)
            putLong(KEY_DETECT_INTERVAL, values[KEY_DETECT_INTERVAL] as Long)
            // 兼容 Set<String> 与 Set<Int> 两种写法，避免 ClassCastException
            val clsStr = when (val raw = values[KEY_TARGET_CLASSES]) {
                is Set<*> -> raw.map { it.toString() }.toSet()
                else -> emptySet()
            }
            putStringSet(KEY_TARGET_CLASSES, clsStr)
            putBoolean(KEY_SHOW_WATERMARK, values[KEY_SHOW_WATERMARK] as Boolean)
            putString(KEY_WATERMARK_TEXT, values[KEY_WATERMARK_TEXT] as String)
            putBoolean(KEY_SHOW_TIMESTAMP, values[KEY_SHOW_TIMESTAMP] as Boolean)
            putInt(KEY_NUM_THREADS,      values[KEY_NUM_THREADS] as Int)
            putBoolean(KEY_USE_NNAPI,    values[KEY_USE_NNAPI] as Boolean)
            apply()
        }
    }

    fun resetDefaults(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().apply()
    }
}
