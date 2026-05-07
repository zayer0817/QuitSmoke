package com.quitsmoke.app

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * 主题管理工具
 * 支持三种模式：跟随系统、浅色、深色
 */
object ThemeHelper {

    private const val PREFS_NAME = "quitsmoke_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    // 主题模式常量
    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2

    /** 获取当前保存的主题模式 */
    fun getThemeMode(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_THEME_MODE, MODE_SYSTEM)
    }

    /** 保存并应用主题模式 */
    fun setThemeMode(context: Context, mode: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply()
        applyTheme(mode)
    }

    /** 应用主题（不保存） */
    fun applyTheme(mode: Int) {
        when (mode) {
            MODE_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            MODE_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    /** 初始化主题（在Application或Activity的onCreate中调用） */
    fun init(context: Context) {
        applyTheme(getThemeMode(context))
    }

    /** 获取主题模式名称 */
    fun getModeName(mode: Int): String {
        return when (mode) {
            MODE_LIGHT -> "浅色"
            MODE_DARK -> "深色"
            else -> "跟随系统"
        }
    }
}
