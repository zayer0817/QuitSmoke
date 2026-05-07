package com.quitsmoke.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.quitsmoke.app.data.SmokeRecord
import com.quitsmoke.app.data.SmokeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

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

    // 主题按钮
    private lateinit var btnThemeSystem: TextView
    private lateinit var btnThemeLight: TextView
    private lateinit var btnThemeDark: TextView

    // 导出导入按钮
    private lateinit var btnExport: TextView
    private lateinit var btnImport: TextView

    companion object {
        private const val REQUEST_CODE_EXPORT = 1001
        private const val REQUEST_CODE_IMPORT = 1002
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
        btnThemeSystem = findViewById(R.id.btn_theme_system)
        btnThemeLight = findViewById(R.id.btn_theme_light)
        btnThemeDark = findViewById(R.id.btn_theme_dark)
        btnExport = findViewById(R.id.btn_export)
        btnImport = findViewById(R.id.btn_import)
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
        // 使用 SAF (Storage Access Framework) 让用户选择保存位置
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "choulema_backup_${dateFormat.format(Date())}.csv"

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_CODE_EXPORT)
    }

    // ========== 数据导入 ==========

    private fun importData() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/comma-separated-values", "application/csv"))
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_CODE_IMPORT)
    }

    @Deprecated("使用 Activity Result API 替代")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK || data == null) return

        when (requestCode) {
            REQUEST_CODE_EXPORT -> {
                data.data?.let { uri ->
                    performExport(uri)
                }
            }
            REQUEST_CODE_IMPORT -> {
                data.data?.let { uri ->
                    performImport(uri)
                }
            }
        }
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
                        val writer = OutputStreamWriter(outputStream, "UTF-8")
                        // 写入BOM头，让Excel正确识别UTF-8
                        writer.write("\uFEFF")
                        // CSV头部
                        writer.write("id,timestamp,dateStr,hourOfDay,note\n")
                        // 数据行
                        for (record in records) {
                            writer.write("${record.id},${record.timestamp},${record.dateStr},${record.hourOfDay},\"${record.note.replace("\"", "\"\"")}\"\n")
                        }
                        writer.flush()
                    }
                }

                showExportResult("导出成功！共 ${records.size} 条记录")
            } catch (e: Exception) {
                showExportResult("导出失败：${e.message}")
            }
        }
    }

    /**
     * 执行导入操作
     */
    private fun performImport(uri: Uri) {
        lifecycleScope.launch {
            try {
                val importedCount = withContext(Dispatchers.IO) {
                    val records = mutableListOf<SmokeRecord>()
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                        var line = reader.readLine()
                        // 跳过BOM头
                        if (line != null && line.startsWith("\uFEFF")) {
                            line = line.substring(1)
                        }
                        // 跳过头部行
                        if (line != null && line.startsWith("id,")) {
                            line = reader.readLine()
                        }
                        while (line != null) {
                            val parts = parseCsvLine(line)
                            if (parts.size >= 4) {
                                try {
                                    val record = SmokeRecord(
                                        id = 0, // 让Room自动生成ID
                                        timestamp = parts[1].toLong(),
                                        dateStr = parts[2],
                                        hourOfDay = parts[3].toInt(),
                                        note = if (parts.size > 4) parts[4] else ""
                                    )
                                    records.add(record)
                                } catch (e: Exception) {
                                    // 跳过格式错误的行
                                }
                            }
                            line = reader.readLine()
                        }
                    }
                    // 批量插入
                    if (records.isNotEmpty()) {
                        repo.insertRecords(records)
                    }
                    records.size
                }

                Toast.makeText(this@SettingsActivity, "导入成功！共 $importedCount 条记录", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "导入失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 解析CSV行（处理引号内的逗号）
     */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' && !inQuotes -> inQuotes = true
                char == '"' && inQuotes -> inQuotes = false
                char == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString().trim())
        return result
    }

    private fun showExportResult(message: String) {
        tvExportResult.text = message
        tvExportResult.visibility = TextView.VISIBLE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
