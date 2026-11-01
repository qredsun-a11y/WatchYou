package com.watchyou

import android.app.Application
import timber.log.Timber

class WatchYouApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}
