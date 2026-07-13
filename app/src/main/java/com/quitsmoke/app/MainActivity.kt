package com.quitsmoke.app

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.quitsmoke.app.data.DailyStat
import com.quitsmoke.app.data.WeekReport
import com.quitsmoke.app.databinding.ActivityMainBinding
import com.quitsmoke.app.databinding.ItemBarBinding
import com.quitsmoke.app.widget.SmokeWidgetProvider
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    
    private var themeColor: Int = Color.parseColor("#2E6B2A")
    private var themeColorLight: Int = Color.parseColor("#4E8B32")
    private var themeColorDark: Int = Color.parseColor("#1B5E20")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupListeners()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    state.goalReport?.let { updateTodaySection(state.todayCount, state.dailyTarget) }
                    state.goalReport?.let { updateGoalSection(it) }
                    state.weekReport?.let { updateWeekSection(it) }
                    state.weekReport?.let { updateBars(it.dailyStats) }
                    if (state.weekReport != null) {
                        updateAdvice(state.todayCount, state.dailyTarget, state.weekReport, state.hourlyStats)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadData()
        applyThemeColor()
    }

    private fun applyThemeColor() {
        val color = AppPreferences.getCachedThemeColor(this)
        themeColor = Color.parseColor(color)
        themeColorLight = lightenColor(themeColor, 0.3f)
        themeColorDark = darkenColor(themeColor, 0.2f)

        // Toolbar
        binding.toolbar.setBackgroundColor(themeColor)
        binding.toolbar.setTitleTextColor(Color.WHITE)
        binding.toolbar.overflowIcon?.setTint(Color.WHITE)
        for (i in 0 until binding.toolbar.menu.size()) {
            binding.toolbar.menu.getItem(i).icon?.setTint(Color.WHITE)
        }

        // Filled button
        binding.btnSmokeMain.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
        binding.btnSmokeMain.setTextColor(Color.WHITE)

        // Outlined button
        binding.btnUndo.setTextColor(themeColor)
        binding.btnUndo.strokeColor = android.content.res.ColorStateList.valueOf(themeColor)

        // Goal streak text
        binding.tvGoalStreak.setTextColor(themeColor)
        binding.tvNoSmokeStreak.setTextColor(themeColorLight)

        // Status bar
        window.statusBarColor = themeColor

        // 强制重绘柱状图——因为 StateFlow 在数据不变时不会 emit，
        // 所以即使主题色变了，collect 也不会触发 updateBars()
        viewModel.uiState.value.weekReport?.let { updateBars(it.dailyStats) }
        viewModel.uiState.value.weekReport?.let { updateWeekSection(it) }
        viewModel.uiState.value.goalReport?.let { updateGoalSection(it) }
        viewModel.uiState.value.let { updateTodaySection(it.todayCount, it.dailyTarget) }
        if (viewModel.uiState.value.weekReport != null) {
            updateAdvice(
                viewModel.uiState.value.todayCount,
                viewModel.uiState.value.dailyTarget,
                viewModel.uiState.value.weekReport!!,
                viewModel.uiState.value.hourlyStats
            )
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_add -> {
                    startActivity(Intent(this, AddRecordActivity::class.java))
                    true
                }
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun setupListeners() {
        binding.btnSmokeMain.setOnClickListener {
            viewModel.recordSmoke { notifyWidgetUpdate() }
        }

        binding.btnUndo.setOnClickListener {
            viewModel.undoLastSmoke { success ->
                if (success) notifyWidgetUpdate()
            }
        }
    }

    private fun updateTodaySection(count: Int, dailyTarget: Int) {
        binding.tvTodayCount.text = count.toString()
        val remaining = (dailyTarget - count).coerceAtLeast(0)
        binding.tvTodayTarget.text = if (count <= dailyTarget) {
            getString(R.string.target_on_track, count, dailyTarget, remaining)
        } else {
            getString(R.string.target_exceeded, count, dailyTarget, count - dailyTarget)
        }

        val (tipArrayRes, color) = when {
            count == 0 -> R.array.today_tips_no_smoke to themeColor
            count <= dailyTarget -> R.array.today_tips_on_target to themeColor
            count <= 5 -> R.array.today_tips_mild to themeColorLight
            count <= 10 -> R.array.today_tips_moderate to themeColorDark
            count <= 20 -> R.array.today_tips_heavy to themeColor
            else -> R.array.today_tips_severe to themeColorLight
        }
        binding.tvTodayTip.text = getRandomStringFromArray(tipArrayRes)
        binding.tvTodayTip.setTextColor(color)
        binding.tvTodayTarget.setTextColor(
            if (count <= dailyTarget) themeColor else themeColorLight
        )
    }

    private fun getRandomStringFromArray(arrayResId: Int): String {
        val array = resources.getStringArray(arrayResId)
        return array.random()
    }

    private fun updateGoalSection(report: com.quitsmoke.app.data.GoalReport) {
        binding.tvGoalStreak.text = getString(R.string.days_unit, report.targetStreak)
        binding.tvNoSmokeStreak.text = getString(R.string.days_unit, report.noSmokeStreak)
        binding.tvMonthSummary.text = getString(
            R.string.month_summary_format,
            report.monthTotal,
            report.monthAvgDaily,
            report.monthDaysElapsed,
            report.monthTargetDays,
            report.monthSmokeFreeDays
        )
    }

    private fun updateWeekSection(report: WeekReport) {
        binding.tvWeekTotal.text = getString(R.string.count_cigarettes, report.totalWeek)
        binding.tvWeekAvg.text = getString(R.string.count_per_day, report.avgDaily)

        val trendText = when (report.trend) {
            -1 -> getString(R.string.trend_down, report.prevWeekTotal - report.totalWeek)
            1 -> getString(R.string.trend_up, report.totalWeek - report.prevWeekTotal)
            else -> getString(R.string.trend_flat)
        }
        binding.tvTrend.text = trendText
        binding.tvTrend.setTextColor(
            when (report.trend) {
                -1 -> themeColor
                1 -> themeColorLight
                else -> themeColorDark
            }
        )
    }

    private fun updateBars(dailyStats: List<DailyStat>) {
        binding.layoutBars.removeAllViews()

        val maxCount = dailyStats.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
        val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
        val repo = com.quitsmoke.app.data.SmokeRepository.getInstance(this)
        val todayStr = repo.getTodayStr()

        dailyStats.forEach { stat ->
            val barBinding = ItemBarBinding.inflate(layoutInflater, binding.layoutBars, false)

            val heightRatio = if (maxCount > 0) stat.count.toFloat() / maxCount else 0f
            val maxHeight = 120
            val barHeightDp = (maxHeight * heightRatio).coerceAtLeast(4f)
            val barHeightPx = (barHeightDp * resources.displayMetrics.density).toInt()

            val layoutParams = barBinding.viewBar.layoutParams
            layoutParams.height = barHeightPx
            barBinding.viewBar.layoutParams = layoutParams

            val barColor = when {
                stat.count == 0 -> themeColor
                stat.count <= 3 -> themeColorLight
                stat.count <= 6 -> themeColor
                stat.count <= 10 -> themeColorDark
                else -> darkenColor(themeColorDark, 0.15f)
            }
            barBinding.viewBar.background = createBarBackground(barColor, stat.dateStr == todayStr)

            try {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(stat.dateStr)
                barBinding.tvDay.text = sdf.format(date!!)
            } catch (e: Exception) {
                barBinding.tvDay.text = stat.dateStr.takeLast(5)
            }

            barBinding.tvBarCount.text = if (stat.count > 0) stat.count.toString() else ""

            val clickDate = stat.dateStr
            barBinding.root.setOnClickListener {
                val intent = Intent(this, DayDetailActivity::class.java).apply {
                    putExtra(DayDetailActivity.EXTRA_DATE, clickDate)
                }
                startActivity(intent)
            }
            barBinding.root.isClickable = true
            barBinding.root.isFocusable = true

            binding.layoutBars.addView(barBinding.root)
        }
    }

    private fun updateAdvice(
        todayCount: Int,
        dailyTarget: Int,
        weekReport: WeekReport,
        hourly: List<com.quitsmoke.app.data.HourlyStat>
    ) {
        val peakHour = hourly.maxByOrNull { it.count }?.takeIf { it.count > 0 }
        val peakHint = peakHour?.let {
            val timeRange = when (it.hourOfDay) {
                in 0..5 -> getString(R.string.hour_dawn, it.hourOfDay)
                in 6..8 -> getString(R.string.hour_morning, it.hourOfDay)
                in 9..11 -> getString(R.string.hour_forenoon, it.hourOfDay)
                in 12..13 -> getString(R.string.hour_noon, it.hourOfDay)
                in 14..17 -> getString(R.string.hour_afternoon, it.hourOfDay)
                in 18..20 -> getString(R.string.hour_evening, it.hourOfDay)
                else -> getString(R.string.hour_night, it.hourOfDay)
            }
            val templates = resources.getStringArray(R.array.reminders_peak_hour)
            templates.random().format(timeRange)
        }

        val adviceText = peakHint ?: when {
            todayCount == 0 -> getRandomStringFromArray(R.array.reminders_no_smoke_today)
            todayCount > dailyTarget -> getRandomStringFromArray(R.array.reminders_over_target)
            weekReport.trend == -1 -> getRandomStringFromArray(R.array.reminders_trend_down)
            weekReport.trend == 1 -> getRandomStringFromArray(R.array.reminders_trend_up)
            weekReport.avgDaily.toFloatOrNull()?.let { it >= 10f } == true -> getRandomStringFromArray(R.array.reminders_heavy)
            else -> getRandomStringFromArray(R.array.reminders_normal)
        }
        binding.tvAdvice.text = adviceText
    }

    private fun notifyWidgetUpdate() {
        SmokeWidgetProvider.notifyWidgetUpdate(this)
    }

    private fun createBarBackground(color: Int, isToday: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 4f * resources.displayMetrics.density
            setColor(color)
            if (isToday) {
                setStroke(
                    (2f * resources.displayMetrics.density).toInt().coerceAtLeast(1),
                    Color.WHITE
                )
            }
        }
    }
}
