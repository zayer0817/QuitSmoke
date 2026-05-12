package com.quitsmoke.app

import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

/**
 * 所有 Activity 的基类
 *
 * 提供统一的沉浸式状态栏支持：
 * - 状态栏透明，内容延伸到状态栏下方
 * - 根据当前主题自动切换状态栏图标颜色
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
     * 根据当前主题自动切换状态栏图标颜色
     */
    private fun setupImmersiveStatusBar() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val isLightTheme = (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        // 浅色主题 → 深色图标，深色主题 → 浅色图标
        controller.isAppearanceLightStatusBars = isLightTheme
        controller.isAppearanceLightNavigationBars = isLightTheme
    }
}
