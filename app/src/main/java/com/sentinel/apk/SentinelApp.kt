package com.sentinel.apk

import android.app.Application

/**
 * SentinelApp — Custom Application class.
 *
 * Registered in AndroidManifest.xml via android:name=".SentinelApp".
 * Used for app-wide initialisation that must happen before any
 * Activity or Service is created.
 */
class SentinelApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Future: initialise analytics, crash reporting, DI, etc.
    }
}
