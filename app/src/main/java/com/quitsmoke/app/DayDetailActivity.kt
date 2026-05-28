package com.quitsmoke.app

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.quitsmoke.app.data.SmokeRepository
import com.quitsmoke.app.databinding.ActivityDayDetailBinding
import kotlinx.coroutines.launch

class DayDetailActivity : BaseActivity() {

    companion object {
        const val EXTRA_DATE = "extra_date"
    }

    private lateinit var binding: ActivityDayDetailBinding
    private lateinit var repo: SmokeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDayDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = SmokeRepository.getInstance(this)

        binding.recyclerDayDetail.layoutManager = LinearLayoutManager(this)

        val dateStr = intent.getStringExtra(EXTRA_DATE) ?: repo.getTodayStr()
        binding.tvDayDetailTitle.text = dateStr

        binding.btnBackDayDetail.setOnClickListener {
            finish()
        }

        loadDetail(dateStr)
    }

    private fun loadDetail(dateStr: String) {
        lifecycleScope.launch {
            val records = repo.getRecordsByDate(dateStr)
            if (records.isEmpty()) {
                binding.tvDayDetailEmpty.text = getString(R.string.day_detail_empty)
                binding.tvDayDetailEmpty.visibility = View.VISIBLE
                binding.recyclerDayDetail.visibility = View.GONE
            } else {
                binding.tvDayDetailEmpty.visibility = View.GONE
                binding.recyclerDayDetail.visibility = View.VISIBLE
                binding.recyclerDayDetail.adapter = DayDetailAdapter(records)
            }
        }
    }
}
