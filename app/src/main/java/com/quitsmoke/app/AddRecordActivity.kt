package com.quitsmoke.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.quitsmoke.app.data.SmokeRecord
import com.quitsmoke.app.data.SmokeRepository
import com.quitsmoke.app.databinding.ActivityAddRecordBinding
import com.quitsmoke.app.widget.SmokeWidgetProvider
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AddRecordActivity : BaseActivity() {

    private lateinit var binding: ActivityAddRecordBinding
    private lateinit var repo: SmokeRepository

    private val calendar = Calendar.getInstance()
    private var selectedReason: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddRecordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = SmokeRepository.getInstance(this)

        binding.toolbar.setNavigationOnClickListener { finish() }

        updateDateDisplay()
        updateTimeDisplay()

        binding.btnPickDate.setOnClickListener {
            showDatePicker()
        }

        binding.btnPickTime.setOnClickListener {
            showTimePicker()
        }

        binding.btnPickReason.setOnClickListener {
            showReasonPicker()
        }

        binding.btnConfirmAdd.setOnClickListener {
            saveRecord()
        }
    }

    override fun onResume() {
        super.onResume()
        applyThemeColor()
    }

    private fun applyThemeColor() {
        val color = AppPreferences.getCachedThemeColor(this)
        val colorInt = android.graphics.Color.parseColor(color)

        // Toolbar
        binding.toolbar.setBackgroundColor(colorInt)
        binding.toolbar.setTitleTextColor(android.graphics.Color.WHITE)
        binding.toolbar.navigationIcon?.setTint(android.graphics.Color.WHITE)

        // Filled button
        binding.btnConfirmAdd.backgroundTintList = android.content.res.ColorStateList.valueOf(colorInt)
        binding.btnConfirmAdd.setTextColor(android.graphics.Color.WHITE)

        // Outlined buttons
        binding.btnPickDate.setTextColor(colorInt)
        binding.btnPickDate.strokeColor = android.content.res.ColorStateList.valueOf(colorInt)
        binding.btnPickTime.setTextColor(colorInt)
        binding.btnPickTime.strokeColor = android.content.res.ColorStateList.valueOf(colorInt)
        binding.btnPickReason.setTextColor(colorInt)
        binding.btnPickReason.strokeColor = android.content.res.ColorStateList.valueOf(colorInt)

        // Status bar
        window.statusBarColor = colorInt
    }

    private fun updateDateDisplay() {
        val sdf = SimpleDateFormat("yyyy-MM-dd (EEE)", Locale.CHINESE)
        binding.tvSelectedDate.text = sdf.format(calendar.time)
    }

    private fun updateTimeDisplay() {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        binding.tvSelectedTime.text = sdf.format(calendar.time)
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
            true
        ).show()
    }

    private fun showReasonPicker() {
        val reasons = resources.getStringArray(R.array.trigger_reasons)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_trigger_reason))
            .setItems(reasons) { _, which ->
                selectedReason = reasons[which]
                binding.tvSelectedReason.text = selectedReason
            }
            .setNegativeButton(getString(R.string.btn_clear)) { _, _ ->
                selectedReason = ""
                binding.tvSelectedReason.text = getString(R.string.reason_not_selected)
            }
            .show()
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
            note = selectedReason
        )

        lifecycleScope.launch {
            repo.insertRecord(record)
            SmokeWidgetProvider.notifyWidgetUpdate(this@AddRecordActivity)
            Toast.makeText(
                this@AddRecordActivity,
                getString(R.string.toast_added, dateStr, binding.tvSelectedTime.text),
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }
}
