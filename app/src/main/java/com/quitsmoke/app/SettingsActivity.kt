package com.quitsmoke.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.quitsmoke.app.data.SmokeRecord
import com.quitsmoke.app.data.SmokeRepository
import com.quitsmoke.app.databinding.ActivitySettingsBinding
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
        setupListeners()
        lifecycleScope.launch {
            updateThemeUI()
            updateGoalUI()
            loadDataSummary()
        }
    }

    private fun setupListeners() {
        binding.btnBackSettings.setOnClickListener {
            finish()
        }

        binding.itemHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        binding.btnThemeSystem.setOnClickListener { switchTheme(AppPreferences.MODE_SYSTEM) }
        binding.btnThemeLight.setOnClickListener { switchTheme(AppPreferences.MODE_LIGHT) }
        binding.btnThemeDark.setOnClickListener { switchTheme(AppPreferences.MODE_DARK) }

        binding.btnTargetMinus.setOnClickListener { adjustDailyTarget(-1) }
        binding.btnTargetPlus.setOnClickListener { adjustDailyTarget(1) }

        binding.btnExport.setOnClickListener { exportData() }
        binding.btnImport.setOnClickListener { importData() }
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

        val activeBg = R.drawable.btn_smoke_bg
        val inactiveBg = R.drawable.btn_undo_bg

        binding.btnThemeSystem.setBackgroundResource(if (currentMode == AppPreferences.MODE_SYSTEM) activeBg else inactiveBg)
        binding.btnThemeLight.setBackgroundResource(if (currentMode == AppPreferences.MODE_LIGHT) activeBg else inactiveBg)
        binding.btnThemeDark.setBackgroundResource(if (currentMode == AppPreferences.MODE_DARK) activeBg else inactiveBg)

        val activeTextColor = getColor(R.color.white)
        val inactiveTextColor = resources.getColor(R.color.text_primary, theme)

        binding.btnThemeSystem.setTextColor(if (currentMode == AppPreferences.MODE_SYSTEM) activeTextColor else inactiveTextColor)
        binding.btnThemeLight.setTextColor(if (currentMode == AppPreferences.MODE_LIGHT) activeTextColor else inactiveTextColor)
        binding.btnThemeDark.setTextColor(if (currentMode == AppPreferences.MODE_DARK) activeTextColor else inactiveTextColor)
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
