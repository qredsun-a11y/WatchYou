package com.watchyou.service

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * 独立控制的 LifecycleOwner，用于 CameraX 绑定。
 * Service 的默认 lifecycle 可能不进入 RESUMED，导致相机不产出帧。
 */
class CameraLifecycleOwner : LifecycleOwner {
    private val _lifecycle = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = _lifecycle

    fun onStart() = _lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
    fun onResume() = _lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onPause() = _lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun onStop() = _lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onDestroy() = _lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
}