package com.watchyou.service

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.view.MotionEvent
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.watchyou.R
import com.watchyou.detector.ObjectDetector
import java.util.LinkedHashMap

class SettingsActivity : AppCompatActivity() {

    private lateinit var sbConfidence: SeekBar
    private lateinit var tvConfidenceVal: TextView
    private lateinit var sbIou: SeekBar
    private lateinit var tvIouVal: TextView
    private lateinit var sbMinArea: SeekBar
    private lateinit var tvMinAreaVal: TextView
    private lateinit var spinnerTargetClasses: Spinner
    private lateinit var tvTargetSummary: TextView
    private lateinit var spinnerDetectInterval: Spinner
    private lateinit var spinnerRecordDuration: Spinner
    private lateinit var spinnerNoTargetTimeout: Spinner
    private lateinit var switchWatermark: SwitchMaterial
    private lateinit var switchTimestamp: SwitchMaterial
    private lateinit var spinnerThreads: Spinner
    private lateinit var switchNnapi: SwitchMaterial
    private lateinit var btnReset: Button
    private lateinit var onSave: Button
    private lateinit var etWatermarkText: EditText

    private val classOptions = ObjectDetector.ALL_CLASS_NAMES.keys.toList()
    private val detectIntervalOptions = intArrayOf(300, 500, 800, 1000, 1500, 2000, 3000)
    private val recordDurationSecs = intArrayOf(5, 10, 15, 20, 30, 60)
    private val noTargetOptions     = intArrayOf(0, 500, 1000, 1500, 2000, 3000, 5000)
    private val threadOptions       = intArrayOf(1, 2, 3, 4)

    private var selectedClasses: Set<Int> = setOf()
    private var detectionService: DetectionService.LocalBinder? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: android.os.IBinder) {
            detectionService = service as DetectionService.LocalBinder
        }
        override fun onServiceDisconnected(name: ComponentName) {
            detectionService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        initViews()
        loadValues()
        bindListeners()
        bindDetectionService()
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(serviceConnection)
    }

    private fun bindDetectionService() {
        val intent = Intent(this, DetectionService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun initViews() {
        sbConfidence        = findViewById(R.id.sbConfidence)
        tvConfidenceVal     = findViewById(R.id.tvConfidenceVal)
        sbIou               = findViewById(R.id.sbIou)
        tvIouVal            = findViewById(R.id.tvIouVal)
        sbMinArea           = findViewById(R.id.sbMinArea)
        tvMinAreaVal        = findViewById(R.id.tvMinAreaVal)
        spinnerTargetClasses = findViewById(R.id.spinnerTargetClasses)
        tvTargetSummary     = findViewById(R.id.tvTargetSummary)
        spinnerDetectInterval  = findViewById(R.id.spinnerDetectInterval)
        spinnerRecordDuration = findViewById(R.id.spinnerRecordDuration)
        spinnerNoTargetTimeout = findViewById(R.id.spinnerNoTargetTimeout)
        switchWatermark       = findViewById(R.id.switchWatermark)
        switchTimestamp       = findViewById(R.id.switchTimestamp)
        spinnerThreads        = findViewById(R.id.spinnerThreads)
        switchNnapi           = findViewById(R.id.switchNnapi)
        btnReset              = findViewById(R.id.btnReset)
        onSave                = findViewById(R.id.btnSave)
        etWatermarkText      = findViewById(R.id.etWatermarkText)
    }

    private fun loadValues() {
        val s = SettingsManager.load(this)
        val conf = s["cfg_confidence"] as Float
        val iou  = s["cfg_iou"] as Float
        val minA = s["cfg_min_area"] as Float
        sbConfidence.progress = (conf * 100).toInt(); tvConfidenceVal.text = "${sbConfidence.progress}%"
        sbIou.progress        = (iou  * 100).toInt(); tvIouVal.text        = "%.2f".format(iou)
        sbMinArea.progress    = (minA * 100).toInt(); tvMinAreaVal.text    = "${sbMinArea.progress}%"

        try {
            selectedClasses = (s["cfg_target_classes"] as? Set<Int>) ?: SettingsManager.DEFAULT_TARGET_CLASSES
            if (selectedClasses.isEmpty()) {
                selectedClasses = SettingsManager.DEFAULT_TARGET_CLASSES
            }
        } catch (e: Exception) {
            selectedClasses = SettingsManager.DEFAULT_TARGET_CLASSES
        }
        updateTargetSummary()
        setupTargetClassesSpinner()

        setupSpinnerPos(spinnerDetectInterval, detectIntervalOptions.map { it.toLong() }, s["cfg_detection_interval"] as Long)
        val recDurSec = (s["cfg_record_duration"] as Long) / 1000L
        setupSpinnerPosSecs(spinnerRecordDuration, recordDurationSecs.map { it.toLong() }, recDurSec)
        setupSpinnerPos(spinnerNoTargetTimeout, noTargetOptions.map { it.toLong() }, s["cfg_no_target_timeout"] as Long)
        setupSpinnerThreads(spinnerThreads, threadOptions.map { it.toLong() }, (s["cfg_num_threads"] as Int).toLong())

        switchWatermark.isChecked = s["cfg_show_watermark"] as Boolean
        switchTimestamp.isChecked = s["cfg_show_timestamp"] as Boolean
        switchNnapi.isChecked     = s["cfg_use_nnapi"] as Boolean
        etWatermarkText.setText(s["cfg_watermark_text"] as? String ?: "WatchYou")
        applyWatermarkEnabled()
    }


    private fun setupTargetClassesSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            arrayOf(getString(R.string.settings_target_classes_dialog_title)))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTargetClasses.adapter = adapter
    }

    private fun setupSpinnerPos(spinner: Spinner, values: List<Long>, selectedValue: Long) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            values.map { v -> v.toString() })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val pos = values.indexOf(selectedValue)
        if (pos >= 0) spinner.setSelection(pos)
    }

    private fun setupSpinnerPosSecs(spinner: Spinner, values: List<Long>, selectedValue: Long) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            values.map { v -> "${v}s" })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val pos = values.indexOf(selectedValue)
        if (pos >= 0) spinner.setSelection(pos)
    }

    private fun setupSpinnerThreads(spinner: Spinner, values: List<Long>, selectedValue: Long) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            values.map { v -> v.toString() })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val pos = values.indexOf(selectedValue)
        if (pos >= 0) spinner.setSelection(pos)
    }

    private fun updateTargetSummary() {
        val labels = selectedClasses.map { ObjectDetector.ALL_CLASS_NAMES[it] ?: "" }.filter { it.isNotEmpty() }
        tvTargetSummary.text = if (labels.isEmpty()) getString(R.string.settings_target_classes_empty)
                               else labels.joinToString(", ")
    }

    private fun bindListeners() {
        sbConfidence.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { tvConfidenceVal.text = "$p%" }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        sbIou.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { tvIouVal.text = "%.2f".format(p / 100.0f) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        sbMinArea.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { tvMinAreaVal.text = "$p%" }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        spinnerTargetClasses.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                openClassSelectionDialog()
            }
            true
        }

        switchWatermark.setOnCheckedChangeListener { _, isChecked ->
            etWatermarkText.isEnabled = isChecked
        }

        btnReset.setOnClickListener { SettingsManager.resetDefaults(this); finish() }
        onSave.setOnClickListener { saveAllValues(); Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show(); finish() }
    }

    private fun applyWatermarkEnabled() {
        val enabled = switchWatermark.isChecked
        etWatermarkText.isEnabled = enabled
    }

    private fun openClassSelectionDialog() {
        val checked = BooleanArray(classOptions.size) { classOptions[it] in selectedClasses }
        val labels = classOptions.map { ObjectDetector.ALL_CLASS_NAMES[it] ?: "" }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.settings_target_classes_dialog_title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.settings_save) { _, _ ->
                selectedClasses = buildSet {
                    classOptions.forEachIndexed { i, cls -> if (checked[i]) add(cls) }
                }
                if (selectedClasses.isEmpty()) {
                    Toast.makeText(this, R.string.settings_target_classes_empty, Toast.LENGTH_SHORT).show()
                } else {
                    updateTargetSummary()
                }
            }
            .setNegativeButton(R.string.settings_reset) { _, _ -> /* dismiss, keep existing selection */ }
            .show()
    }

    private fun saveAllValues() {
        val vals: MutableMap<String, Any> = LinkedHashMap()
        vals["cfg_confidence"]      = (sbConfidence.progress / 100f).toFloat()
        vals["cfg_iou"]             = (sbIou.progress     / 100f).toFloat()
        vals["cfg_min_area"]        = (sbMinArea.progress / 100f).toFloat()
        vals["cfg_record_duration"] = (recordDurationSecs[spinnerRecordDuration.selectedItemPosition].toLong() * 1000L)
        vals["cfg_no_target_timeout"] = noTargetOptions[spinnerNoTargetTimeout.selectedItemPosition].toLong()
        vals["cfg_detection_interval"] = detectIntervalOptions[spinnerDetectInterval.selectedItemPosition].toLong()
        vals["cfg_target_classes"]  = selectedClasses.map { it.toString() }.toSet()
        vals["cfg_show_watermark"]  = switchWatermark.isChecked
        vals["cfg_show_timestamp"]  = switchTimestamp.isChecked
        vals["cfg_watermark_text"]  = etWatermarkText.text.toString()
        vals["cfg_num_threads"]     = threadOptions[spinnerThreads.selectedItemPosition]
        vals["cfg_use_nnapi"]       = switchNnapi.isChecked
        SettingsManager.save(this, vals)
        
        // 通知服务实时刷新配置（无需重启应用）
        detectionService?.refreshConfig()
    }
}
