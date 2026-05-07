package com.quitsmoke.app

import android.os.Bundle
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.quitsmoke.app.data.SmokeRepository
import kotlinx.coroutines.launch

/**
 * 历史记录页面
 */
class HistoryActivity : BaseActivity() {

    private lateinit var repo: SmokeRepository
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.init(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        repo = SmokeRepository.getInstance(this)
        recyclerView = findViewById(R.id.recycler_history)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 返回按钮
        findViewById<TextView>(R.id.btn_back_history).setOnClickListener {
            finish()
        }

        loadHistory()
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val stats = repo.getWeeklyStats()
            // 最近的在前
            val adapter = HistoryAdapter(stats.reversed())
            recyclerView.adapter = adapter
        }
    }
}
