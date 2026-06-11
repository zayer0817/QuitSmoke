package com.quitsmoke.app

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.quitsmoke.app.data.SmokeRecord
import com.quitsmoke.app.data.SmokeRepository
import com.quitsmoke.app.databinding.ActivityDayDetailBinding
import com.quitsmoke.app.widget.SmokeWidgetProvider
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DayDetailActivity : BaseActivity() {

    companion object {
        const val EXTRA_DATE = "extra_date"
    }

    private lateinit var binding: ActivityDayDetailBinding
    private lateinit var repo: SmokeRepository
    private var currentDateStr: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDayDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = SmokeRepository.getInstance(this)

        binding.recyclerDayDetail.layoutManager = LinearLayoutManager(this)

        currentDateStr = intent.getStringExtra(EXTRA_DATE) ?: repo.getTodayStr()
        binding.tvDayDetailTitle.text = currentDateStr

        binding.btnBackDayDetail.setOnClickListener {
            finish()
        }

        loadDetail(currentDateStr)
    }

    private fun loadDetail(dateStr: String) {
        lifecycleScope.launch {
            val records = repo.getRecordsByDate(dateStr).toMutableList()
            if (records.isEmpty()) {
                binding.tvDayDetailEmpty.text = getString(R.string.day_detail_empty)
                binding.tvDayDetailEmpty.visibility = View.VISIBLE
                binding.recyclerDayDetail.visibility = View.GONE
            } else {
                binding.tvDayDetailEmpty.visibility = View.GONE
                binding.recyclerDayDetail.visibility = View.VISIBLE
                binding.recyclerDayDetail.adapter = DayDetailAdapter(records) { record, _ ->
                    showDeleteConfirm(record, records)
                }
            }
        }
    }

    private fun showDeleteConfirm(record: SmokeRecord, records: MutableList<SmokeRecord>) {
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_delete_title))
            .setMessage(getString(R.string.confirm_delete_message, timeStr))
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                deleteRecord(record, records)
            }
            .show()
    }

    private fun deleteRecord(record: SmokeRecord, records: MutableList<SmokeRecord>) {
        lifecycleScope.launch {
            repo.deleteRecord(record.id)
            val index = records.indexOfFirst { it.id == record.id }
            if (index >= 0) {
                records.removeAt(index)
                binding.recyclerDayDetail.adapter?.notifyItemRemoved(index)
                binding.recyclerDayDetail.adapter?.notifyItemRangeChanged(index, records.size)
            }
            if (records.isEmpty()) {
                binding.tvDayDetailEmpty.text = getString(R.string.day_detail_empty)
                binding.tvDayDetailEmpty.visibility = View.VISIBLE
                binding.recyclerDayDetail.visibility = View.GONE
            }
            Toast.makeText(this@DayDetailActivity, getString(R.string.toast_record_deleted), Toast.LENGTH_SHORT).show()
            SmokeWidgetProvider.notifyWidgetUpdate(this@DayDetailActivity)
        }
    }
}
