package com.quitsmoke.app

import android.os.Bundle
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.quitsmoke.app.data.SmokeRepository
import kotlinx.coroutines.launch

/**
 * 某天详情页面 - 展示当天每条记录的具体时间
 */
class DayDetailActivity : BaseActivity() {

    companion object {
        const val EXTRA_DATE = "extra_date"
    }

    private lateinit var repo: SmokeRepository
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvTitle: TextView
    private lateinit var tvEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.init(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_day_detail)

        repo = SmokeRepository.getInstance(this)

        tvTitle = findViewById(R.id.tv_day_detail_title)
        tvEmpty = findViewById(R.id.tv_day_detail_empty)
        recyclerView = findViewById(R.id.recycler_day_detail)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val dateStr = intent.getStringExtra(EXTRA_DATE) ?: repo.getTodayStr()
        tvTitle.text = dateStr

        // 返回按钮
        findViewById<TextView>(R.id.btn_back_day_detail).setOnClickListener {
            finish()
        }

        loadDetail(dateStr)
    }

    private fun loadDetail(dateStr: String) {
        lifecycleScope.launch {
            val records = repo.getRecordsByDate(dateStr)
            if (records.isEmpty()) {
                tvEmpty.visibility = TextView.VISIBLE
                recyclerView.visibility = RecyclerView.GONE
            } else {
                tvEmpty.visibility = TextView.GONE
                recyclerView.visibility = RecyclerView.VISIBLE
                recyclerView.adapter = DayDetailAdapter(records)
            }
        }
    }
}
