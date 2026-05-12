package com.quitsmoke.app

import android.app.Application

/**
 * 应用全局 Application 类
 *
 * 统一执行全局初始化，避免每个 Activity 重复调用：
 * - 主题初始化 (ThemeHelper)
 */
class QuitSmokeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 统一初始化主题设置
        ThemeHelper.init(this)
    }
}
