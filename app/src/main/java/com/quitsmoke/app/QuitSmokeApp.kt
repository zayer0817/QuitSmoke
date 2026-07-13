package com.quitsmoke.app

import android.app.Application
import android.graphics.Color

class QuitSmokeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppPreferences.applyTheme(AppPreferences.getCachedThemeMode(this))
    }
}
