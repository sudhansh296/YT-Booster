package com.ytsubexchange

import android.app.Application
import android.util.Log

class YTBoosterApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("YTBoosterApp", "Application started")
        // NewPipeExtractor initializes itself lazily inside the innertube module
        // No manual init needed here - it caused conflicts
    }
}
