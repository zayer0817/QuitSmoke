package com.quitsmoke.app

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.quitsmoke.app.ai.DeepSeekClient
import com.quitsmoke.app.data.SmokeRepository
import com.quitsmoke.app.databinding.ActivityMonthlyReportBinding
import io.noties.markwon.Markwon
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MonthlyReportActivity : BaseActivity() {

    private lateinit var binding: ActivityMonthlyReportBinding
    private lateinit var repo: SmokeRepository
    private lateinit var markwon: Markwon

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // 当前选中的区间
    private var currentStart = ""
    private var currentEnd = ""
    private var currentLabel = ""

    // 当前展示的报告内容（用于分享）
    private var currentReportMarkdown = ""

    private lateinit var rangeButtons: List<MaterialButton>
    private var selectedButton: MaterialButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMonthlyReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repo = SmokeRepository.getInstance(this)
        markwon = Markwon.create(this)

        rangeButtons = listOf(
            binding.btnRange7,
            binding.btnRange14,
            binding.btnRange30,
            binding.btnRangeThisMonth,
            binding.btnRangeLastMonth,
            binding.btnRangeAll
        )

        setupToolbar()
        setupRangeButtons()
        setupActionButtons()
        applyThemeColor()

        // 默认选中「近30天」，进入待确认状态（不自动调 API）
        selectRange(binding.btnRange30)

        // 如果有缓存的报告，显示「查看上次报告」入口
        updateCachedEntry()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRangeButtons() {
        for (btn in rangeButtons) {
            btn.setOnClickListener { selectRange(btn) }
        }
    }

    private fun setupActionButtons() {
        binding.btnStartAnalysis.setOnClickListener { loadReport() }
        binding.btnReanalyze.setOnClickListener { loadReport() }
        binding.btnShare.setOnClickListener { shareReport() }
        binding.btnViewCached.setOnClickListener { showCachedReport() }
        binding.btnEmptyBack.setOnClickListener { showReady() }
        binding.btnRetry.setOnClickListener {
            val apiKey = AppPreferences.getAiApiKey(this)
            if (apiKey.isBlank()) {
                startActivity(Intent(this, SettingsActivity::class.java))
                finish()
            } else {
                loadReport()
            }
        }
    }

    /** 检查是否有缓存报告，控制「查看上次报告」入口的显隐 */
    private fun updateCachedEntry() {
        val cached = AppPreferences.getCachedReportMeta(this)
        if (cached != null) {
            binding.layoutCachedEntry.visibility = View.VISIBLE
        } else {
            binding.layoutCachedEntry.visibility = View.GONE
        }
    }

    private fun selectRange(selected: MaterialButton) {
        val today = Calendar.getInstance()
        var end = sdf.format(today.time)
        val start: String
        val label: String

        when (selected.id) {
            R.id.btn_range_all -> {
                // 「全部」需要异步查最早记录日期
                updateRangeButtonStyles(selected)
                showLoading()
                lifecycleScope.launch {
                    val first = repo.getFirstRecordDate()
                    if (first == null) {
                        showEmpty()
                        return@launch
                    }
                    currentStart = first
                    currentEnd = end
                    currentLabel = "全部数据"
                    updateReadyInfo()
                    showReady()
                }
                return
            }
            R.id.btn_range_7 -> { start = daysAgo(6); label = "近7天" }
            R.id.btn_range_14 -> { start = daysAgo(13); label = "近14天" }
            R.id.btn_range_30 -> { start = daysAgo(29); label = "近30天" }
            R.id.btn_range_this_month -> {
                val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
                start = sdf.format(cal.time)
                label = "本月"
            }
            R.id.btn_range_last_month -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    add(Calendar.MONTH, -1)
                }
                start = sdf.format(cal.time)
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                end = sdf.format(cal.time)
                label = "上月"
            }
            else -> return
        }

        updateRangeButtonStyles(selected)
        currentStart = start
        currentEnd = end
        currentLabel = label
        updateReadyInfo()
        showReady()
    }

    private fun updateReadyInfo() {
        binding.tvReadyRange.text = currentLabel
        binding.tvReadyDates.text = "$currentStart  至  $currentEnd"
    }

    private fun daysAgo(n: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, -n)
        return sdf.format(cal.time)
    }

    private fun updateRangeButtonStyles(selected: MaterialButton) {
        selectedButton = selected
        val colorInt = Color.parseColor(AppPreferences.getCachedThemeColor(this))
        val strokePx = (1 * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        for (btn in rangeButtons) {
            if (btn == selected) {
                btn.backgroundTintList = ColorStateList.valueOf(colorInt)
                btn.setTextColor(Color.WHITE)
                btn.strokeWidth = 0
            } else {
                btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                btn.setTextColor(colorInt)
                btn.strokeColor = ColorStateList.valueOf(colorInt)
                btn.strokeWidth = strokePx
            }
        }
    }

    private fun applyThemeColor() {
        val colorStr = AppPreferences.getCachedThemeColor(this)
        val colorInt = Color.parseColor(colorStr)

        // Toolbar 主题色
        binding.toolbar.setBackgroundColor(colorInt)

        // 沉浸式状态栏：状态栏背景跟随主题色，图标颜色根据明暗自适应
        window.statusBarColor = colorInt
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isColorDark(colorInt)

        // 「开始分析」按钮主题色填充
        binding.btnStartAnalysis.backgroundTintList = ColorStateList.valueOf(colorInt)

        // 重绘区间按钮选中态（主题色可能变了）
        selectedButton?.let { updateRangeButtonStyles(it) }
    }

    private fun loadReport() {
        val apiKey = AppPreferences.getAiApiKey(this)
        if (apiKey.isBlank()) {
            showNoApiKey()
            return
        }

        showLoading()

        lifecycleScope.launch {
            try {
                // rangeLabel 需包含日期范围，供 AI prompt 使用
                val rangeLabelWithDates = "$currentLabel（$currentStart 至 $currentEnd）"
                val prompt = repo.getAnalysisPrompt(currentStart, currentEnd, rangeLabelWithDates)

                if (prompt.contains("总根数：0 根")) {
                    showEmpty()
                    return@launch
                }

                val client = DeepSeekClient(apiKey)
                val result = client.chat(prompt)

                result.onSuccess { report ->
                    // 缓存报告
                    AppPreferences.saveCachedReport(
                        this@MonthlyReportActivity,
                        report,
                        currentLabel,
                        currentStart,
                        currentEnd
                    )
                    currentReportMarkdown = report
                    showReport(report, isCached = false)
                    updateCachedEntry()
                }.onFailure { e ->
                    showError(e.message ?: "分析失败，请检查网络后重试")
                }
            } catch (e: Exception) {
                showError(e.message ?: "发生未知错误")
            }
        }
    }

    /** 展示缓存的上次报告 */
    private fun showCachedReport() {
        val md = AppPreferences.getCachedReport(this)
        val meta = AppPreferences.getCachedReportMeta(this)
        if (md == null || meta == null) {
            return
        }
        currentReportMarkdown = md
        currentLabel = meta.label
        currentStart = meta.start
        currentEnd = meta.end
        showReport(md, isCached = true, cachedTime = meta.time)
    }

    /** 分享当前报告 */
    private fun shareReport() {
        if (currentReportMarkdown.isBlank()) return

        val meta = AppPreferences.getCachedReportMeta(this)
        val timeStr = meta?.time ?: SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            .format(java.util.Date())

        val shareText = buildString {
            appendLine("📊 QuitSmoke AI 数据分析报告")
            appendLine("区间：$currentLabel（$currentStart 至 $currentEnd）")
            appendLine("生成时间：$timeStr")
            appendLine()
            appendLine(currentReportMarkdown)
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "QuitSmoke AI 分析报告")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, "分享报告"))
    }

    // ===== 状态切换 =====

    private fun showReady() {
        binding.cardReady.visibility = View.VISIBLE
        binding.layoutLoading.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.cardReport.visibility = View.GONE
        binding.layoutReportActions.visibility = View.GONE
        // 保持缓存入口的显隐状态
        updateCachedEntry()
    }

    private fun showLoading() {
        binding.cardReady.visibility = View.GONE
        binding.layoutLoading.visibility = View.VISIBLE
        binding.layoutError.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.cardReport.visibility = View.GONE
        binding.layoutReportActions.visibility = View.GONE
        binding.layoutCachedEntry.visibility = View.GONE
    }

    private fun showReport(markdown: String, isCached: Boolean, cachedTime: String? = null) {
        binding.cardReady.visibility = View.GONE
        binding.layoutLoading.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.cardReport.visibility = View.VISIBLE
        binding.layoutReportActions.visibility = View.VISIBLE

        binding.tvRangeLabel.text = "$currentLabel · $currentStart 至 $currentEnd"

        // 缓存报告显示时间提示
        if (isCached && cachedTime != null) {
            binding.tvCachedTime.visibility = View.VISIBLE
            binding.tvCachedTime.text = "📋 上次生成于 $cachedTime"
        } else {
            binding.tvCachedTime.visibility = View.GONE
        }

        markwon.setMarkdown(binding.tvReport, markdown)
    }

    private fun showError(msg: String) {
        binding.cardReady.visibility = View.GONE
        binding.layoutLoading.visibility = View.GONE
        binding.layoutError.visibility = View.VISIBLE
        binding.layoutEmpty.visibility = View.GONE
        binding.cardReport.visibility = View.GONE
        binding.layoutReportActions.visibility = View.GONE
        binding.tvErrorMsg.text = msg
        binding.btnRetry.text = "重试"
    }

    private fun showEmpty() {
        binding.cardReady.visibility = View.GONE
        binding.layoutLoading.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.cardReport.visibility = View.GONE
        binding.layoutReportActions.visibility = View.GONE
    }

    private fun showNoApiKey() {
        binding.cardReady.visibility = View.GONE
        binding.layoutLoading.visibility = View.GONE
        binding.layoutError.visibility = View.VISIBLE
        binding.layoutEmpty.visibility = View.GONE
        binding.cardReport.visibility = View.GONE
        binding.layoutReportActions.visibility = View.GONE
        binding.tvErrorMsg.text = "请先在设置中配置 DeepSeek API Key"
        binding.btnRetry.text = "去设置"
    }

    override fun onResume() {
        super.onResume()
        applyThemeColor()
    }
}
