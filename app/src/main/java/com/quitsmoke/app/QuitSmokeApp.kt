package com.quitsmoke.app

import android.app.Application
import com.quitsmoke.app.reminder.ReminderReceiver
import com.quitsmoke.app.widget.SmokeWidgetProvider

class QuitSmokeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppPreferences.applyTheme(AppPreferences.getCachedThemeMode(this))
        // 每次启动都确保凌晨闹钟已调度（防重启/更新后丢失）
        SmokeWidgetProvider.ensureMidnightUpdateScheduled(this)
        // 每次启动都调度自动补录提醒
        ReminderReceiver.scheduleAllReminders(this)
    }
}
