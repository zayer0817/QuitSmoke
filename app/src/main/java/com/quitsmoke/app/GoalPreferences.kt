package com.quitsmoke.app

import android.content.Context

object GoalPreferences {
    private const val PREFS_NAME = "quitsmoke_prefs"
    private const val KEY_DAILY_TARGET = "daily_target"

    const val DEFAULT_DAILY_TARGET = 10
    const val MIN_DAILY_TARGET = 1
    const val MAX_DAILY_TARGET = 99

    fun getDailyTarget(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_DAILY_TARGET, DEFAULT_DAILY_TARGET)
            .coerceIn(MIN_DAILY_TARGET, MAX_DAILY_TARGET)
    }

    fun setDailyTarget(context: Context, target: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_DAILY_TARGET, target.coerceIn(MIN_DAILY_TARGET, MAX_DAILY_TARGET))
            .apply()
    }
}
