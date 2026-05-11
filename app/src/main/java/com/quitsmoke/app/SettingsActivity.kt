package com.quitsmoke.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.quitsmoke.app.data.SmokeRecord
import com.quitsmoke.app.data.SmokeRepository
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

/**
 * 设置页面
 * - 主题切换（浅色/深色/跟随系统）
 * - 数据导出/导入
 */
class SettingsActivity : BaseActivity() {

    private lateinit var repo: SmokeRepository

    // 视图
    private lateinit var tvThemeMode: TextView
    private lateinit var tvExportResult: TextView
    private lateinit var tvAppVersion: TextView

    // 主题按钮
    private lateinit var btnThemeSystem: TextView
    private lateinit var btnThemeLight: TextView
    private lateinit var btnThemeDark: TextView

    // 导出导入按钮
    private lateinit var btnExport: TextView
    private lateinit var btnImport: TextView

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
        ThemeHelper.init(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        repo = SmokeRepository.getInstance(this)
        initViews()
        setupListeners()
        updateThemeUI()
    }

    private fun initViews() {
        tvThemeMode = findViewById(R.id.tv_theme_mode)
        tvExportResult = findViewById(R.id.tv_export_result)
        tvAppVersion = findViewById(R.id.tv_app_version)
        btnThemeSystem = findViewById(R.id.btn_theme_system)
        btnThemeLight = findViewById(R.id.btn_theme_light)
        btnThemeDark = findViewById(R.id.btn_theme_dark)
        btnExport = findViewById(R.id.btn_export)
        btnImport = findViewById(R.id.btn_import)

        tvAppVersion.text = "抽了吗 v${BuildConfig.VERSION_NAME}"
    }

    private fun setupListeners() {
        // 返回按钮
        findViewById<TextView>(R.id.btn_back_settings).setOnClickListener {
            finish()
        }

        // 历史记录跳转
        findViewById<LinearLayout>(R.id.item_history).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        // 主题切换
        btnThemeSystem.setOnClickListener { switchTheme(ThemeHelper.MODE_SYSTEM) }
        btnThemeLight.setOnClickListener { switchTheme(ThemeHelper.MODE_LIGHT) }
        btnThemeDark.setOnClickListener { switchTheme(ThemeHelper.MODE_DARK) }

        // 数据导出
        btnExport.setOnClickListener { exportData() }

        // 数据导入
        btnImport.setOnClickListener { importData() }
    }

    // ========== 主题切换 ==========

    private fun switchTheme(mode: Int) {
        ThemeHelper.setThemeMode(this, mode)
        updateThemeUI()
        // 主题切换会自动重建Activity
    }

    private fun updateThemeUI() {
        val currentMode = ThemeHelper.getThemeMode(this)
        tvThemeMode.text = "当前：${ThemeHelper.getModeName(currentMode)}"

        // 高亮当前选中的主题按钮
        val activeBg = R.drawable.btn_smoke_bg
        val inactiveBg = R.drawable.btn_undo_bg

        btnThemeSystem.setBackgroundResource(if (currentMode == ThemeHelper.MODE_SYSTEM) activeBg else inactiveBg)
        btnThemeLight.setBackgroundResource(if (currentMode == ThemeHelper.MODE_LIGHT) activeBg else inactiveBg)
        btnThemeDark.setBackgroundResource(if (currentMode == ThemeHelper.MODE_DARK) activeBg else inactiveBg)

        // 激活时文字用白色
        val activeTextColor = 0xFFFFFFFF.toInt()
        val inactiveTextColor = resources.getColor(R.color.text_primary, theme)

        btnThemeSystem.setTextColor(if (currentMode == ThemeHelper.MODE_SYSTEM) activeTextColor else inactiveTextColor)
        btnThemeLight.setTextColor(if (currentMode == ThemeHelper.MODE_LIGHT) activeTextColor else inactiveTextColor)
        btnThemeDark.setTextColor(if (currentMode == ThemeHelper.MODE_DARK) activeTextColor else inactiveTextColor)
    }

    // ========== 数据导出 ==========

    private fun exportData() {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "choulema_backup_${dateFormat.format(Date())}.csv"
        exportDocumentLauncher.launch(fileName)
    }

    // ========== 数据导入 ==========

    private fun importData() {
        importDocumentLauncher.launch(
            arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/*")
        )
    }

    /**
     * 执行导出操作
     * CSV格式：id,timestamp,dateStr,hourOfDay,note
     */
    private fun performExport(uri: Uri) {
        lifecycleScope.launch {
            try {
                val records = withContext(Dispatchers.IO) {
                    repo.getAllRecordsForExport()
                }

                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        val writer = OutputStreamWriter(outputStream, Charsets.UTF_8)
                        // 写入BOM头，让Excel正确识别UTF-8
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

                showDataResult("导出成功！共 ${records.size} 条记录")
            } catch (e: Exception) {
                showDataResult("导出失败：${e.message}")
            }
        }
    }

    /**
     * 执行导入操作
     */
    private fun performImport(uri: Uri) {
        lifecycleScope.launch {
            try {
                val summary = withContext(Dispatchers.IO) {
                    val readResult = readImportedRecords(uri)
                    val insertedCount = repo.insertRecords(readResult.records)
                    ImportSummary(
                        insertedCount = insertedCount,
                        duplicateCount = readResult.records.size - insertedCount,
                        invalidCount = readResult.invalidCount
                    )
                }

                if (summary.insertedCount > 0) {
                    SmokeWidgetProvider().notifyWidgetUpdate(this@SettingsActivity)
                }
                showDataResult(
                    "导入完成：新增 ${summary.insertedCount} 条，跳过重复 ${summary.duplicateCount} 条，无效 ${summary.invalidCount} 条"
                )
            } catch (e: Exception) {
                showDataResult("导入失败：${e.message}")
            }
        }
    }

    /**
     * 读取CSV记录，支持引号、逗号以及引号内换行。
     */
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

    /**
     * 解析CSV行（处理引号内的逗号和转义双引号）
     */
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
        tvExportResult.text = message
        tvExportResult.visibility = TextView.VISIBLE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private data class ImportReadResult(
        val records: List<SmokeRecord>,
        val invalidCount: Int
    )

    private data class ImportSummary(
        val insertedCount: Int,
        val duplicateCount: Int,
        val invalidCount: Int
    )
}
