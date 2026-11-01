package com.watchyou

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.watchyou.service.DetectionService

class MainActivity : AppCompatActivity() {
    private val PERMISSIONS_BASIC = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )
    private val PERMISSIONS_STORAGE = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    private val PERMISSIONS_FOREGROUND = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        arrayOf(
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.FOREGROUND_SERVICE_CAMERA,
            Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
        )
    } else {
        arrayOf(Manifest.permission.FOREGROUND_SERVICE)
    }
    private val PERMISSIONS_NOTIFICATION = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyArray<String>()
    }

    private val PERMISSION_REQUEST_CODE = 1001
    private val SETTINGS_REQUEST_CODE = 1002

    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnSettings: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvInfo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnSettings = findViewById(R.id.btnSettings)
        tvStatus = findViewById(R.id.tvStatus)
        tvInfo = findViewById(R.id.tvInfo)

        btnStart.setOnClickListener { requestAllPermissions() }
        btnStop.setOnClickListener { stopService() }
        btnSettings.setOnClickListener { openAppSettings() }

        updateUI(false)
        checkPermissionsAndUpdateUI()
    }

    private fun requestAllPermissions() {
        val allPerms = mutableListOf<String>()
        allPerms.addAll(PERMISSIONS_BASIC.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED })
        allPerms.addAll(PERMISSIONS_STORAGE.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED })
        allPerms.addAll(PERMISSIONS_FOREGROUND.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED })
        allPerms.addAll(PERMISSIONS_NOTIFICATION.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED })

        if (allPerms.isEmpty()) {
            startDetectionService()
            return
        }

        val permanentlyDenied = allPerms.filter { !ActivityCompat.shouldShowRequestPermissionRationale(this, it) }
        if (permanentlyDenied.isNotEmpty()) {
            showPermissionRationaleDialog(permanentlyDenied)
            return
        }

        ActivityCompat.requestPermissions(this, allPerms.toTypedArray(), PERMISSION_REQUEST_CODE)
    }

    private fun showPermissionRationaleDialog(perms: List<String>) {
        val message = "应用需要以下权限才能工作：\n" + perms.joinToString("\n") + "\n\n请在设置中手动开启。"
        AlertDialog.Builder(this)
            .setTitle("需要权限")
            .setMessage(message)
            .setPositiveButton("去设置") { _, _ -> openAppSettings() }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val denied = mutableListOf<String>()
            for (i in permissions.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    denied.add(permissions[i])
                }
            }
            if (denied.isEmpty()) {
                startDetectionService()
            } else {
                val permanentlyDenied = denied.filter { !ActivityCompat.shouldShowRequestPermissionRationale(this, it) }
                if (permanentlyDenied.isNotEmpty()) {
                    showPermissionRationaleDialog(permanentlyDenied)
                } else {
                    tvStatus.text = "权限被拒绝：" + denied.joinToString(", ")
                }
            }
        }
    }

    private fun checkPermissionsAndUpdateUI() {
        val allPerms = PERMISSIONS_BASIC + PERMISSIONS_STORAGE + PERMISSIONS_FOREGROUND + PERMISSIONS_NOTIFICATION
        val allGranted = allPerms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun startDetectionService() {
        val intent = Intent(this, DetectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateUI(true)
    }

    private fun stopService() {
        val intent = Intent(this, DetectionService::class.java)
        stopService(intent)
        updateUI(false)
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        startActivityForResult(intent, SETTINGS_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SETTINGS_REQUEST_CODE) {
            checkPermissionsAndUpdateUI()
        }
    }

    private fun updateUI(running: Boolean) {
        btnStart.isEnabled = !running
        btnStop.isEnabled = running
        btnSettings.isEnabled = !running
        tvStatus.text = if (running) "监控运行中..." else "监控已停止"
    }
}
