package com.quitsmoke.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.quitsmoke.app.data.SmokeRepository
import com.quitsmoke.app.data.DailyStat
import com.quitsmoke.app.widget.SmokeWidgetProvider
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 主Activity - 统计分析页面
 *
 * 展示：
 * 1. 今日计数和快捷操作
 * 2. 最近7天趋势图
 * 3. 周报摘要
 * 4. 时段分析
 * 5. 戒烟建议
 */
class MainActivity : BaseActivity() {

    private lateinit var repo: SmokeRepository

    // 视图引用
    private lateinit var tvTodayCount: TextView
    private lateinit var tvTodayTip: TextView
    private lateinit var btnSmoke: View
    private lateinit var btnUndo: View
    private lateinit var btnAddManual: View
    private lateinit var btnSettings: View
    private lateinit var tvWeekTotal: TextView
    private lateinit var tvWeekAvg: TextView
    private lateinit var tvTrend: TextView
    private lateinit var layoutBars: LinearLayout
    private lateinit var tvAdvice: TextView
    private lateinit var tvMotivation: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        // 在设置内容视图之前初始化主题
        ThemeHelper.init(this)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        repo = SmokeRepository.getInstance(this)
        initViews()
        setupListeners()
        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun initViews() {
        tvTodayCount = findViewById(R.id.tv_today_count)
        tvTodayTip = findViewById(R.id.tv_today_tip)
        btnSmoke = findViewById(R.id.btn_smoke_main)
        btnUndo = findViewById(R.id.btn_undo)
        btnAddManual = findViewById(R.id.btn_add_manual)
        btnSettings = findViewById(R.id.btn_settings)
        tvWeekTotal = findViewById(R.id.tv_week_total)
        tvWeekAvg = findViewById(R.id.tv_week_avg)
        tvTrend = findViewById(R.id.tv_trend)
        layoutBars = findViewById(R.id.layout_bars)
        tvAdvice = findViewById(R.id.tv_advice)
        tvMotivation = findViewById(R.id.tv_motivation)
    }

    private fun setupListeners() {
        // 抽一根
        btnSmoke.setOnClickListener {
            lifecycleScope.launch {
                repo.recordSmoke()
                notifyWidgetUpdate()
                loadData()
            }
        }

        // 撤销
        btnUndo.setOnClickListener {
            lifecycleScope.launch {
                val success = repo.undoLastSmoke()
                if (success) {
                    notifyWidgetUpdate()
                    loadData()
                }
            }
        }

        // 手动添加
        btnAddManual.setOnClickListener {
            startActivity(Intent(this, AddRecordActivity::class.java))
        }

        // 设置
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            val todayCount = repo.getTodayCount()
            updateTodaySection(todayCount)

            val weekReport = repo.getWeekReport()
            updateWeekSection(weekReport)

            updateBars(weekReport.dailyStats)

            val hourly = repo.getHourlyDistribution()
            updateAdvice(todayCount, weekReport, hourly)

            updateMotivation(weekReport)
        }
    }

    /**
     * 更新今日区域
     */
    private fun updateTodaySection(count: Int) {
        tvTodayCount.text = count.toString()

        val (tip, color) = when {
            count == 0 -> "今天还没有抽烟，坚持住！" to 0xFF4CAF50.toInt()
            count <= 5 -> "还行，控制住自己" to 0xFFFFC107.toInt()
            count <= 10 -> "有点多了，注意控制" to 0xFFFF9800.toInt()
            count <= 20 -> "太多了，要克制！" to 0xFFF44336.toInt()
            else -> "严重超标！请立即停止！" to 0xFFB71C1C.toInt()
        }
        tvTodayTip.text = tip
        tvTodayTip.setTextColor(color)
    }

    /**
     * 更新周报区域
     */
    private fun updateWeekSection(report: com.quitsmoke.app.data.WeekReport) {
        tvWeekTotal.text = "${report.totalWeek} 根"
        tvWeekAvg.text = "${report.avgDaily} 根/天"

        val trendText = when (report.trend) {
            -1 -> "↓ 比上周减少了 ${report.prevWeekTotal - report.totalWeek} 根"
            1 -> "↑ 比上周增加了 ${report.totalWeek - report.prevWeekTotal} 根"
            else -> "→ 与上周持平"
        }
        tvTrend.text = trendText
        tvTrend.setTextColor(
            when (report.trend) {
                -1 -> 0xFF4CAF50.toInt()  // 下降是好事，绿色
                1 -> 0xFFF44336.toInt()   // 上升是坏事，红色
                else -> 0xFFFFC107.toInt()
            }
        )
    }

    /**
     * 更新7天柱状图（纯原生View实现，不依赖第三方库）
     */
    private fun updateBars(dailyStats: List<DailyStat>) {
        layoutBars.removeAllViews()

        val maxCount = dailyStats.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
        val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
        val todayStr = repo.getTodayStr()

        dailyStats.forEach { stat ->
            val barLayout = layoutInflater.inflate(
                R.layout.item_bar, layoutBars, false
            )

            val barView = barLayout.findViewById<View>(R.id.view_bar)
            val tvDay = barLayout.findViewById<TextView>(R.id.tv_day)
            val tvCount = barLayout.findViewById<TextView>(R.id.tv_bar_count)

            // 柱子高度按比例
            val heightRatio = if (maxCount > 0) stat.count.toFloat() / maxCount else 0f
            val maxHeight = 120 // dp
            val barHeightDp = (maxHeight * heightRatio).coerceAtLeast(4f)
            val barHeightPx = (barHeightDp * resources.displayMetrics.density).toInt()

            val layoutParams = barView.layoutParams
            layoutParams.height = barHeightPx
            barView.layoutParams = layoutParams

            // 柱子颜色
            val barColor = when {
                stat.count == 0 -> 0xFF4CAF50.toInt()
                stat.count <= 5 -> 0xFF66BB6A.toInt()
                stat.count <= 10 -> 0xFFFFC107.toInt()
                stat.count <= 20 -> 0xFFFF9800.toInt()
                else -> 0xFFF44336.toInt()
            }
            barView.setBackgroundColor(barColor)

            // 如果是今天，加高亮边框
            if (stat.dateStr == todayStr) {
                barView.setBackgroundResource(R.drawable.bar_today_bg)
            }

            // 日期标签
            try {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(stat.dateStr)
                tvDay.text = sdf.format(date!!)
            } catch (e: Exception) {
                tvDay.text = stat.dateStr.takeLast(5)
            }

            // 数量标签
            tvCount.text = if (stat.count > 0) stat.count.toString() else ""

            // 点击柱子跳转到当日详情
            val clickDate = stat.dateStr
            barLayout.setOnClickListener {
                val intent = Intent(this, DayDetailActivity::class.java).apply {
                    putExtra(DayDetailActivity.EXTRA_DATE, clickDate)
                }
                startActivity(intent)
            }
            barLayout.isClickable = true
            barLayout.isFocusable = true

            layoutBars.addView(barLayout)
        }
    }

    /**
     * 更新戒烟建议
     */
    private fun updateAdvice(
        @Suppress("UNUSED_PARAMETER") todayCount: Int,
        @Suppress("UNUSED_PARAMETER") weekReport: com.quitsmoke.app.data.WeekReport,
        hourly: List<com.quitsmoke.app.data.HourlyStat>
    ) {
        val sb = StringBuilder()

        // 时段分析
        if (hourly.isNotEmpty()) {
            val peakHour = hourly.maxByOrNull { it.count }
            if (peakHour != null && peakHour.count > 0) {
                val timeRange = when (peakHour.hourOfDay) {
                    in 0..5 -> "凌晨 ${peakHour.hourOfDay}:00"
                    in 6..8 -> "早上 ${peakHour.hourOfDay}:00"
                    in 9..11 -> "上午 ${peakHour.hourOfDay}:00"
                    in 12..13 -> "中午 ${peakHour.hourOfDay}:00"
                    in 14..17 -> "下午 ${peakHour.hourOfDay}:00"
                    in 18..20 -> "傍晚 ${peakHour.hourOfDay}:00"
                    else -> "晚上 ${peakHour.hourOfDay}:00"
                }
                sb.append("📊 你的高峰时段是 $timeRange，这个时段烟瘾最强。\n")
                sb.append("   建议：在此时段准备替代品（口香糖、喝水等）\n\n")
            }
        }

        // 周报建议
        when {
            weekReport.totalWeek == 0 -> {
                sb.append("🎉 整整一周没有抽烟！你太厉害了！")
            }
            weekReport.trend == -1 -> {
                val reducePct = if (weekReport.prevWeekTotal > 0) {
                    val pct = (weekReport.prevWeekTotal - weekReport.totalWeek) * 100 / weekReport.prevWeekTotal
                    "$pct%"
                } else "一些"
                sb.append("📉 本周比上周减少了 $reducePct，继续保持！\n\n")
                sb.append("💡 建议：设定阶段性目标，逐步减少每日上限。")
            }
            weekReport.trend == 1 -> {
                sb.append("⚠️ 本周比上周有所增加，需要警惕！\n\n")
                sb.append("💡 建议：\n")
                sb.append("  • 找出触发抽烟的场景，尽量避免\n")
                sb.append("  • 每次想抽烟时先等5分钟\n")
                sb.append("  • 增加运动量，转移注意力")
            }
            weekReport.avgDaily.toFloat() > 10 -> {
                sb.append("🚨 平均每天超过10根，属于重度吸烟。\n\n")
                sb.append("💡 强烈建议：\n")
                sb.append("  • 咨询医生，考虑使用尼古丁替代疗法\n")
                sb.append("  • 设定每日上限并严格遵守\n")
                sb.append("  • 寻找家人朋友的支持")
            }
            else -> {
                sb.append("💪 当前状态尚可，继续控制每日数量。\n\n")
                sb.append("💡 建议：尝试每天比前一天少抽1根。")
            }
        }

        tvAdvice.text = sb.toString()
    }

    /**
     * 更新激励语
     */
    private fun updateMotivation(@Suppress("UNUSED_PARAMETER") weekReport: com.quitsmoke.app.data.WeekReport) {
        val motivations = listOf(
            "你每少抽一根烟，肺部的纤毛就开始恢复工作。",
            "戒烟20分钟：心率和血压开始恢复正常。",
            "戒烟12小时：血液中一氧化碳水平恢复正常。",
            "戒烟2-12周：血液循环改善，肺功能增强。",
            "戒烟1-9个月：咳嗽和气短减轻。",
            "戒烟1年：心脏病风险降低一半。",
            "戒烟5年：中风风险降至非吸烟者水平。",
            "每少抽一根烟，平均延长11分钟寿命。",
            "你的家人因为你的坚持而更安心。",
            "坚持就是胜利，每一根不抽都是进步！"
        )
        tvMotivation.text = motivations.random()
    }

    private fun notifyWidgetUpdate() {
        SmokeWidgetProvider.notifyWidgetUpdate(this)
    }
}
