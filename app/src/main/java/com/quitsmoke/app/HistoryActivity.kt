package com.quitsmoke.app

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.quitsmoke.app.data.SmokeRepository
import com.quitsmoke.app.databinding.ActivityHistoryBinding
import kotlinx.coroutines.launch

class HistoryActivity : BaseActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var repo: SmokeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = SmokeRepository.getInstance(this)
        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)

        binding.toolbar.setNavigationOnClickListener { finish() }

        loadHistory()
    }

    override fun onResume() {
        super.onResume()
        applyThemeColor()
    }

    private fun applyThemeColor() {
        val color = AppPreferences.getCachedThemeColor(this)
        val colorInt = Color.parseColor(color)

        // Toolbar
        binding.toolbar.setBackgroundColor(colorInt)
        binding.toolbar.setTitleTextColor(Color.WHITE)
        binding.toolbar.navigationIcon?.setTint(Color.WHITE)

        // Status bar
        window.statusBarColor = colorInt
        
        // Reload history with new theme color
        loadHistory()
    }

    private fun loadHistory() {
        val themeColor = Color.parseColor(AppPreferences.getCachedThemeColor(this))
        lifecycleScope.launch {
            val stats = repo.getWeeklyStats()
            if (stats.isEmpty()) {
                binding.tvHistoryEmpty.text = getString(R.string.history_empty)
                binding.tvHistoryEmpty.visibility = View.VISIBLE
                binding.recyclerHistory.visibility = View.GONE
            } else {
                binding.tvHistoryEmpty.visibility = View.GONE
                binding.recyclerHistory.visibility = View.VISIBLE
                binding.recyclerHistory.adapter = HistoryAdapter(stats.reversed(), themeColor)
            }
        }
    }
}

