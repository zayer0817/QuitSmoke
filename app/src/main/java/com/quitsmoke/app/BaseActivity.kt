package com.quitsmoke.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

/**
 * 所有 Activity 的基类
 *
 * 提供统一的沉浸式状态栏支持：
 * - 状态栏透明，内容延伸到状态栏下方
 * - 浅色背景使用深色状态栏图标（避免白色混淆）
 * - 子类自行决定如何处理状态栏区域的间距
 */
open class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 沉浸式状态栏：必须在 super.onCreate 之前设置
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        setupImmersiveStatusBar()
    }

    /**
     * 配置沉浸式状态栏（在 setContentView 之后调用）
     */
    private fun setupImmersiveStatusBar() {
        // 浅色背景 → 深色状态栏图标，确保可见
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
    }
}
