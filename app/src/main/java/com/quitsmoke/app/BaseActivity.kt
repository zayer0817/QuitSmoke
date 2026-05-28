package com.quitsmoke.app

import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

open class BaseActivity : AppCompatActivity() {

    protected open val useTransparentStatusBar: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        setupImmersiveStatusBar()
    }

    override fun setContentView(view: View) {
        super.setContentView(view)
        setupImmersiveStatusBar()
    }

    private fun setupImmersiveStatusBar() {
        window.statusBarColor = if (useTransparentStatusBar) {
            Color.TRANSPARENT
        } else {
            ContextCompat.getColor(this, R.color.status_bar)
        }
        window.navigationBarColor = ContextCompat.getColor(this, R.color.nav_bar)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val isLightTheme = (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        controller.isAppearanceLightStatusBars = isLightTheme
        controller.isAppearanceLightNavigationBars = isLightTheme
    }
}
