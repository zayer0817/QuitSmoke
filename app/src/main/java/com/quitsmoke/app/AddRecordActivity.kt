package com.quitsmoke.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.quitsmoke.app.data.SmokeRecord
import com.quitsmoke.app.data.SmokeRepository
import com.quitsmoke.app.widget.SmokeWidgetProvider
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 手动添加抽烟记录页面
 * 用户可以自定义日期和具体时间
 */
class AddRecordActivity : BaseActivity() {

    private lateinit var repo: SmokeRepository
    private lateinit var tvSelectedDate: TextView
    private lateinit var tvSelectedTime: TextView
    private lateinit var tvConfirm: TextView

    private val calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.init(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_record)

        repo = SmokeRepository.getInstance(this)

        tvSelectedDate = findViewById(R.id.tv_selected_date)
        tvSelectedTime = findViewById(R.id.tv_selected_time)
        tvConfirm = findViewById(R.id.btn_confirm_add)

        // 默认显示当前日期时间
        updateDateDisplay()
        updateTimeDisplay()

        // 返回按钮
        findViewById<TextView>(R.id.btn_back_add).setOnClickListener {
            finish()
        }

        // 选择日期
        findViewById<TextView>(R.id.btn_pick_date).setOnClickListener {
            showDatePicker()
        }

        // 选择时间
        findViewById<TextView>(R.id.btn_pick_time).setOnClickListener {
            showTimePicker()
        }

        // 确认添加
        tvConfirm.setOnClickListener {
            saveRecord()
        }
    }

    private fun showDatePicker() {
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                updateDateDisplay()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker() {
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                updateTimeDisplay()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true  // 24小时制
        ).show()
    }

    private fun updateDateDisplay() {
        val sdf = SimpleDateFormat("yyyy-MM-dd (EEE)", Locale.CHINESE)
        tvSelectedDate.text = sdf.format(calendar.time)
    }

    private fun updateTimeDisplay() {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        tvSelectedTime.text = sdf.format(calendar.time)
    }

    private fun saveRecord() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateStr = sdf.format(calendar.time)
        val timestamp = calendar.timeInMillis
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)

        val record = SmokeRecord(
            timestamp = timestamp,
            dateStr = dateStr,
            hourOfDay = hourOfDay,
            note = ""
        )

        lifecycleScope.launch {
            repo.insertRecord(record)
            SmokeWidgetProvider().notifyWidgetUpdate(this@AddRecordActivity)
            Toast.makeText(
                this@AddRecordActivity,
                "已添加：$dateStr ${tvSelectedTime.text}",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }
}
