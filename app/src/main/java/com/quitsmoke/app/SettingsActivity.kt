package com.quitsmoke.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.quitsmoke.app.data.SmokeRecord
import com.quitsmoke.app.data.SmokeRepository
import com.quitsmoke.app.databinding.ActivitySettingsBinding
import com.quitsmoke.app.reminder.ReminderReceiver
import com.quitsmoke.app.widget.SmokeWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : BaseActivity() {

    private lateinit var repo: SmokeRepository
    private lateinit var binding: ActivitySettingsBinding

    private val exportDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { performExport(it) }
    }

    private val importDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { performImport(it) }
    }

    companion object {
        private val DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = SmokeRepository.getInstance(this)
        binding.tvAppVersion.text = getString(R.string.app_version_format, BuildConfig.VERSION_NAME)
        setupToolbar()
        setupListeners()
        lifecycleScope.launch {
            updateThemeUI()
            updateGoalUI()
            loadDataSummary()
            loadReminderConfig()
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

        // All filled buttons - set background tint and white text
        val filledButtons = listOf(
            binding.btnTargetPlus,
            binding.btnImport
        )
        for (btn in filledButtons) {
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(colorInt)
            btn.setTextColor(android.graphics.Color.WHITE)
        }

        // All outlined buttons - set stroke color and theme text color
        val outlinedButtons = listOf(
            binding.btnTargetMinus,
            binding.btnExport,
            binding.btnCustomColor,
            binding.btnThemeSystem,
            binding.btnThemeLight,
            binding.btnThemeDark
        )
        for (btn in outlinedButtons) {
            btn.setTextColor(colorInt)
            btn.strokeColor = android.content.res.ColorStateList.valueOf(colorInt)
            btn.iconTint = android.content.res.ColorStateList.valueOf(colorInt)
        }

        // 补录提醒 ImageButton 图标着色 + 边框
        val reminderIconButtons = listOf(
            binding.btnMorningMinus, binding.btnMorningPlus,
            binding.btnNoonMinus, binding.btnNoonPlus,
            binding.btnEveningMinus, binding.btnEveningPlus
        )
        for (ib in reminderIconButtons) {
            ib.imageTintList = android.content.res.ColorStateList.valueOf(colorInt)
            (ib.background as? android.graphics.drawable.GradientDrawable)?.setStroke(
                2, colorInt
            )
        }

        // Switch 跟随主题色
        binding.switchReminder.thumbTintList = android.content.res.ColorStateList.valueOf(colorInt)
        binding.switchReminder.trackTintList = android.content.res.ColorStateList.valueOf(colorInt)

        // 检查时间文字跟随主题色
        binding.tvMorningCheck.setTextColor(colorInt)
        binding.tvNoonCheck.setTextColor(colorInt)
        binding.tvEveningCheck.setTextColor(colorInt)

        // Update color circle selection highlight
        updateColorCircleSelection(color)

        // Status bar
        window.statusBarColor = colorInt
    }

    private fun updateColorCircleSelection(selectedColor: String) {
        val colorViews = mapOf(
            binding.colorGreen to "#2E6B2A",
            binding.colorBlue to "#1565C0",
            binding.colorPurple to "#6750A4",
            binding.colorOrange to "#E65100",
            binding.colorPink to "#AD1457",
            binding.colorTeal to "#00695C"
        )

        for ((view, presetColor) in colorViews) {
            val drawable = android.graphics.drawable.GradientDrawable()
            drawable.shape = android.graphics.drawable.GradientDrawable.OVAL
            drawable.setColor(android.graphics.Color.parseColor(presetColor))
            if (presetColor.equals(selectedColor, ignoreCase = true)) {
                drawable.setStroke(4, android.graphics.Color.WHITE)
            }
            view.background = drawable
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupListeners() {
        binding.itemHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        binding.btnThemeSystem.setOnClickListener { switchTheme(AppPreferences.MODE_SYSTEM) }
        binding.btnThemeLight.setOnClickListener { switchTheme(AppPreferences.MODE_LIGHT) }
        binding.btnThemeDark.setOnClickListener { switchTheme(AppPreferences.MODE_DARK) }

        binding.colorGreen.setOnClickListener { switchThemeColor("#2E6B2A") }
        binding.colorBlue.setOnClickListener { switchThemeColor("#1565C0") }
        binding.colorPurple.setOnClickListener { switchThemeColor("#6750A4") }
        binding.colorOrange.setOnClickListener { switchThemeColor("#E65100") }
        binding.colorPink.setOnClickListener { switchThemeColor("#AD1457") }
        binding.colorTeal.setOnClickListener { switchThemeColor("#00695C") }

        binding.btnCustomColor.setOnClickListener { showColorPickerDialog() }

        binding.btnTargetMinus.setOnClickListener { adjustDailyTarget(-1) }
        binding.btnTargetPlus.setOnClickListener { adjustDailyTarget(1) }

        binding.btnExport.setOnClickListener { exportData() }
        binding.btnImport.setOnClickListener { importData() }

        // 自动补录提醒
        binding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            AppPreferences.setReminderEnabled(this, isChecked)
            if (isChecked) {
                ReminderReceiver.scheduleAllReminders(this)
            } else {
                ReminderReceiver.cancelAllReminders(this)
            }
        }

        setupExpectedCountButtons("morning", binding.btnMorningMinus, binding.btnMorningPlus, binding.tvMorningExpected)
        setupExpectedCountButtons("noon", binding.btnNoonMinus, binding.btnNoonPlus, binding.tvNoonExpected)
        setupExpectedCountButtons("evening", binding.btnEveningMinus, binding.btnEveningPlus, binding.tvEveningExpected)

        binding.tvMorningCheck.setOnClickListener { pickCheckTime("morning", binding.tvMorningCheck) }
        binding.tvNoonCheck.setOnClickListener { pickCheckTime("noon", binding.tvNoonCheck) }
        binding.tvEveningCheck.setOnClickListener { pickCheckTime("evening", binding.tvEveningCheck) }
    }

    private fun setupExpectedCountButtons(
        period: String,
        btnMinus: android.widget.ImageButton,
        btnPlus: android.widget.ImageButton,
        tvCount: TextView
    ) {
        btnMinus.setOnClickListener {
            val current = AppPreferences.getExpectedCount(this, period)
            val newVal = (current - 1).coerceAtLeast(0)
            AppPreferences.setExpectedCount(this, period, newVal)
            tvCount.text = newVal.toString()
        }
        btnPlus.setOnClickListener {
            val current = AppPreferences.getExpectedCount(this, period)
            val newVal = (current + 1).coerceAtMost(20)
            AppPreferences.setExpectedCount(this, period, newVal)
            tvCount.text = newVal.toString()
        }
    }

    private fun pickCheckTime(period: String, tvDisplay: TextView) {
        val (currentHour, currentMinute) = AppPreferences.getCheckTime(this, period)
        val view = layoutInflater.inflate(R.layout.dialog_time_picker, null)
        val timePicker = view.findViewById<TimePicker>(R.id.time_picker)
        timePicker.setIs24HourView(true)
        timePicker.hour = currentHour
        timePicker.minute = currentMinute

        AlertDialog.Builder(this)
            .setTitle("设置${AppPreferences.getPeriodLabel(period)}检查时间")
            .setView(view)
            .setPositiveButton("确定") { _, _ ->
                val hour = timePicker.hour
                val minute = timePicker.minute
                AppPreferences.setCheckTime(this, period, hour, minute)
                tvDisplay.text = String.format("%02d:%02d", hour, minute)
                // 重新调度该时段闹钟
                ReminderReceiver.scheduleReminder(this, period)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadReminderConfig() {
        binding.switchReminder.isChecked = AppPreferences.isReminderEnabled(this)
        binding.tvMorningExpected.text = AppPreferences.getExpectedCount(this, "morning").toString()
        binding.tvNoonExpected.text = AppPreferences.getExpectedCount(this, "noon").toString()
        binding.tvEveningExpected.text = AppPreferences.getExpectedCount(this, "evening").toString()

        val (mh, mm) = AppPreferences.getCheckTime(this, "morning")
        binding.tvMorningCheck.text = String.format("%02d:%02d", mh, mm)
        val (nh, nm) = AppPreferences.getCheckTime(this, "noon")
        binding.tvNoonCheck.text = String.format("%02d:%02d", nh, nm)
        val (eh, em) = AppPreferences.getCheckTime(this, "evening")
        binding.tvEveningCheck.text = String.format("%02d:%02d", eh, em)
    }

    private fun switchThemeColor(color: String) {
        // Apply immediately for fast UI response
        applyThemeColorDirect(color)

        // 立即写入 SharedPreferences——必须在协程之前。
        // setThemeColor() 是 suspend 函数，里面的 DataStore I/O 可能延迟，
        // 导致 SP 写入滞后。如果用户在协程完成前返回 MainActivity，
        // getCachedThemeColor() 会读到旧颜色，柱状图跳回默认色。
        getSharedPreferences("quitsmoke_prefs", MODE_PRIVATE)
            .edit()
            .putString("theme_color", color)
            .commit()  // commit() 同步写磁盘，apply() 异步可能被杀进程导致丢失

        lifecycleScope.launch {
            AppPreferences.setThemeColor(this@SettingsActivity, color)
            updateThemeColorUI()
            SmokeWidgetProvider.notifyWidgetUpdate(this@SettingsActivity)
        }
    }

    private fun applyThemeColorDirect(color: String) {
        val colorInt = android.graphics.Color.parseColor(color)

        // Toolbar
        binding.toolbar.setBackgroundColor(colorInt)
        binding.toolbar.setTitleTextColor(android.graphics.Color.WHITE)
        binding.toolbar.navigationIcon?.setTint(android.graphics.Color.WHITE)

        // All filled buttons
        val filledButtons = listOf(binding.btnTargetPlus, binding.btnImport)
        for (btn in filledButtons) {
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(colorInt)
            btn.setTextColor(android.graphics.Color.WHITE)
        }

        // All outlined buttons
        val outlinedButtons = listOf(
            binding.btnTargetMinus, binding.btnExport, binding.btnCustomColor,
            binding.btnThemeSystem, binding.btnThemeLight, binding.btnThemeDark
        )
        for (btn in outlinedButtons) {
            btn.setTextColor(colorInt)
            btn.strokeColor = android.content.res.ColorStateList.valueOf(colorInt)
            btn.iconTint = android.content.res.ColorStateList.valueOf(colorInt)
        }

        // 补录提醒 ImageButton 图标着色 + 边框
        val reminderIconButtons = listOf(
            binding.btnMorningMinus, binding.btnMorningPlus,
            binding.btnNoonMinus, binding.btnNoonPlus,
            binding.btnEveningMinus, binding.btnEveningPlus
        )
        for (ib in reminderIconButtons) {
            ib.imageTintList = android.content.res.ColorStateList.valueOf(colorInt)
            (ib.background as? android.graphics.drawable.GradientDrawable)?.setStroke(
                2, colorInt
            )
        }

        // Switch 跟随主题色
        binding.switchReminder.thumbTintList = android.content.res.ColorStateList.valueOf(colorInt)
        binding.switchReminder.trackTintList = android.content.res.ColorStateList.valueOf(colorInt)

        // 检查时间文字跟随主题色
        binding.tvMorningCheck.setTextColor(colorInt)
        binding.tvNoonCheck.setTextColor(colorInt)
        binding.tvEveningCheck.setTextColor(colorInt)

        // Update color circle selection highlight
        updateColorCircleSelection(color)

        // Status bar
        window.statusBarColor = colorInt
    }

    private fun updateThemeColorUI() {
        val currentColor = AppPreferences.getCachedThemeColor(this)
        binding.tvThemeColor.text = currentColor
        
        // Highlight the selected color circle
        val colorViews = listOf(
            binding.colorGreen to "#2E6B2A",
            binding.colorBlue to "#1565C0",
            binding.colorPurple to "#6750A4",
            binding.colorOrange to "#E65100",
            binding.colorPink to "#AD1457",
            binding.colorTeal to "#00695C"
        )
        
        for ((view, presetColor) in colorViews) {
            if (presetColor.equals(currentColor, ignoreCase = true)) {
                // Add a border to indicate selection
                val drawable = android.graphics.drawable.GradientDrawable()
                drawable.shape = android.graphics.drawable.GradientDrawable.OVAL
                drawable.setColor(android.graphics.Color.parseColor(presetColor))
                drawable.setStroke(4, android.graphics.Color.WHITE)
                view.background = drawable
            } else {
                // Reset to default circle
                val drawable = android.graphics.drawable.GradientDrawable()
                drawable.shape = android.graphics.drawable.GradientDrawable.OVAL
                drawable.setColor(android.graphics.Color.parseColor(presetColor))
                view.background = drawable
            }
        }
    }

    private fun showColorPickerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val previewView = dialogView.findViewById<View>(R.id.color_preview)
        val seekRed = dialogView.findViewById<android.widget.SeekBar>(R.id.seek_red)
        val seekGreen = dialogView.findViewById<android.widget.SeekBar>(R.id.seek_green)
        val seekBlue = dialogView.findViewById<android.widget.SeekBar>(R.id.seek_blue)
        val tvRed = dialogView.findViewById<TextView>(R.id.tv_red_value)
        val tvGreen = dialogView.findViewById<TextView>(R.id.tv_green_value)
        val tvBlue = dialogView.findViewById<TextView>(R.id.tv_blue_value)

        // Initialize with current color
        val currentColor = AppPreferences.getCachedThemeColor(this)
        val colorInt = android.graphics.Color.parseColor(currentColor)
        seekRed.progress = android.graphics.Color.red(colorInt)
        seekGreen.progress = android.graphics.Color.green(colorInt)
        seekBlue.progress = android.graphics.Color.blue(colorInt)
        tvRed.text = android.graphics.Color.red(colorInt).toString()
        tvGreen.text = android.graphics.Color.green(colorInt).toString()
        tvBlue.text = android.graphics.Color.blue(colorInt).toString()
        previewView.setBackgroundColor(colorInt)

        val updatePreview = {
            val r = seekRed.progress
            val g = seekGreen.progress
            val b = seekBlue.progress
            val color = android.graphics.Color.rgb(r, g, b)
            previewView.setBackgroundColor(color)
            tvRed.text = r.toString()
            tvGreen.text = g.toString()
            tvBlue.text = b.toString()
        }

        seekRed.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) = updatePreview()
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
        seekGreen.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) = updatePreview()
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
        seekBlue.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) = updatePreview()
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_pick_color)
            .setView(dialogView)
            .setPositiveButton(R.string.btn_confirm) { _, _ ->
                val r = seekRed.progress
                val g = seekGreen.progress
                val b = seekBlue.progress
                val hexColor = String.format("#%02X%02X%02X", r, g, b)
                switchThemeColor(hexColor)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun switchTheme(mode: Int) {
        lifecycleScope.launch {
            AppPreferences.setThemeMode(this@SettingsActivity, mode)
            updateThemeUI()
        }
    }

    private suspend fun updateThemeUI() {
        val currentMode = AppPreferences.getThemeMode(this@SettingsActivity)
        binding.tvThemeMode.text = getString(R.string.current_theme_format, AppPreferences.getModeName(currentMode))

        val buttonId = when (currentMode) {
            AppPreferences.MODE_SYSTEM -> R.id.btn_theme_system
            AppPreferences.MODE_LIGHT -> R.id.btn_theme_light
            AppPreferences.MODE_DARK -> R.id.btn_theme_dark
            else -> R.id.btn_theme_system
        }
        binding.themeToggleGroup.check(buttonId)
        updateThemeColorUI()
    }

    private fun adjustDailyTarget(delta: Int) {
        lifecycleScope.launch {
            val current = AppPreferences.getDailyTarget(this@SettingsActivity)
            AppPreferences.setDailyTarget(this@SettingsActivity, current + delta)
            updateGoalUI()
            SmokeWidgetProvider.notifyWidgetUpdate(this@SettingsActivity)
        }
    }

    private suspend fun updateGoalUI() {
        val target = AppPreferences.getDailyTarget(this@SettingsActivity)
        binding.tvDailyTarget.text = getString(R.string.target_format, target)
    }

    private suspend fun loadDataSummary() {
        val total = withContext(Dispatchers.IO) {
            repo.getTotalCount()
        }
        binding.tvTotalRecords.text = getString(R.string.total_records_format, total)
    }

    private fun exportData() {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "choulema_backup_${dateFormat.format(Date())}.csv"
        exportDocumentLauncher.launch(fileName)
    }

    private fun importData() {
        importDocumentLauncher.launch(
            arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/*")
        )
    }

    private fun performExport(uri: Uri) {
        lifecycleScope.launch {
            try {
                val records = withContext(Dispatchers.IO) {
                    repo.getAllRecordsForExport()
                }

                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        val writer = OutputStreamWriter(outputStream, Charsets.UTF_8)
                        writer.write("\uFEFF")
                        writer.write(buildCsvLine(listOf("id", "timestamp", "dateStr", "hourOfDay", "note")))
                        for (record in records) {
                            writer.write(
                                buildCsvLine(
                                    listOf(
                                        record.id.toString(),
                                        record.timestamp.toString(),
                                        record.dateStr,
                                        record.hourOfDay.toString(),
                                        record.note
                                    )
                                )
                            )
                        }
                        writer.flush()
                    }
                }

                showDataResult(getString(R.string.export_success, records.size))
            } catch (e: Exception) {
                showDataResult(getString(R.string.export_failed, e.message))
            }
        }
    }

    private fun performImport(uri: Uri) {
        lifecycleScope.launch {
            try {
                val preview = withContext(Dispatchers.IO) {
                    val readResult = readImportedRecords(uri)
                    val insertableCount = repo.previewInsertRecords(readResult.records)
                    ImportPreview(
                        records = readResult.records,
                        insertableCount = insertableCount,
                        duplicateCount = readResult.records.size - insertableCount,
                        invalidCount = readResult.invalidCount
                    )
                }
                showImportConfirm(preview)
            } catch (e: Exception) {
                showDataResult(getString(R.string.import_failed, e.message))
            }
        }
    }

    private fun showImportConfirm(preview: ImportPreview) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_import))
            .setMessage(getString(R.string.import_preview_format, preview.insertableCount, preview.duplicateCount, preview.invalidCount))
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .setPositiveButton(getString(R.string.btn_import_confirm)) { _, _ ->
                confirmImport(preview.records)
            }
            .show()
    }

    private fun confirmImport(records: List<SmokeRecord>) {
        lifecycleScope.launch {
            try {
                val insertedCount = withContext(Dispatchers.IO) {
                    repo.insertRecords(records)
                }
                if (insertedCount > 0) {
                    SmokeWidgetProvider.notifyWidgetUpdate(this@SettingsActivity)
                }
                loadDataSummary()
                showDataResult(getString(R.string.import_complete, insertedCount))
            } catch (e: Exception) {
                showDataResult(getString(R.string.import_failed, e.message))
            }
        }
    }

    private fun readImportedRecords(uri: Uri): ImportReadResult {
        val records = mutableListOf<SmokeRecord>()
        var invalidCount = 0

        contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val rows = readCsvRows(reader)
            val dataRows = if (rows.firstOrNull()?.firstOrNull()?.equals("id", ignoreCase = true) == true) {
                rows.drop(1)
            } else {
                rows
            }

            dataRows.forEach { fields ->
                val record = parseImportedRecord(fields)
                if (record != null) {
                    records.add(record)
                } else {
                    invalidCount++
                }
            }
        }

        return ImportReadResult(records, invalidCount)
    }

    private fun readCsvRows(reader: BufferedReader): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val pending = StringBuilder()
        var isFirstLine = true
        var line = reader.readLine()

        while (line != null) {
            val cleanLine = if (isFirstLine) line.removePrefix("\uFEFF") else line
            isFirstLine = false

            if (pending.isNotEmpty()) {
                pending.append('\n')
            }
            pending.append(cleanLine)

            if (isCsvRecordComplete(pending.toString())) {
                rows.add(parseCsvLine(pending.toString()))
                pending.clear()
            }

            line = reader.readLine()
        }

        if (pending.isNotEmpty()) {
            rows.add(parseCsvLine(pending.toString()))
        }

        return rows
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
            index++
        }
        result.add(current.toString())
        return result
    }

    private fun isCsvRecordComplete(value: String): Boolean {
        var inQuotes = false
        var index = 0

        while (index < value.length) {
            if (value[index] == '"') {
                if (inQuotes && index + 1 < value.length && value[index + 1] == '"') {
                    index++
                } else {
                    inQuotes = !inQuotes
                }
            }
            index++
        }

        return !inQuotes
    }

    private fun parseImportedRecord(fields: List<String>): SmokeRecord? {
        if (fields.size < 4) return null

        val timestamp = fields[1].trim().toLongOrNull() ?: return null
        val dateStr = fields[2].trim()
        val hourOfDay = fields[3].trim().toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val note = fields.getOrNull(4).orEmpty()

        if (!isValidDate(dateStr)) return null

        return SmokeRecord(
            id = 0,
            timestamp = timestamp,
            dateStr = dateStr,
            hourOfDay = hourOfDay,
            note = note
        )
    }

    private fun isValidDate(dateStr: String): Boolean {
        if (!DATE_REGEX.matches(dateStr)) return false

        return runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
                isLenient = false
            }.parse(dateStr)
        }.getOrNull() != null
    }

    private fun buildCsvLine(values: List<String>): String {
        return values.joinToString(",") { escapeCsvField(it) } + "\n"
    }

    private fun escapeCsvField(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    private fun showDataResult(message: String) {
        binding.tvExportResult.text = message
        binding.tvExportResult.visibility = TextView.VISIBLE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private data class ImportReadResult(
        val records: List<SmokeRecord>,
        val invalidCount: Int
    )

    private data class ImportPreview(
        val records: List<SmokeRecord>,
        val insertableCount: Int,
        val duplicateCount: Int,
        val invalidCount: Int
    )
}
