package com.quitsmoke.app

import android.content.Intent
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

    override val useTransparentStatusBar: Boolean = true

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        binding.btnAddManual.setOnClickListener {
            startActivity(Intent(this, AddRecordActivity::class.java))
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
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

        val (tip, color) = when {
            count == 0 -> getString(R.string.today_tip_no_smoke) to getColor(R.color.green_good)
            count <= dailyTarget -> getString(R.string.today_tip_on_target) to getColor(R.color.green_good)
            count <= 5 -> getString(R.string.today_tip_mild) to getColor(R.color.yellow_warn)
            count <= 10 -> getString(R.string.today_tip_moderate) to getColor(R.color.orange_alert)
            count <= 20 -> getString(R.string.today_tip_heavy) to getColor(R.color.red_danger)
            else -> getString(R.string.today_tip_severe) to getColor(R.color.red_dark)
        }
        binding.tvTodayTip.text = tip
        binding.tvTodayTip.setTextColor(color)
        binding.tvTodayTarget.setTextColor(
            if (count <= dailyTarget) getColor(R.color.green_good) else getColor(R.color.red_danger)
        )
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
                -1 -> getColor(R.color.green_good)
                1 -> getColor(R.color.red_danger)
                else -> getColor(R.color.yellow_warn)
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
                stat.count == 0 -> getColor(R.color.green_good)
                stat.count <= 5 -> getColor(R.color.green_light)
                stat.count <= 10 -> getColor(R.color.yellow_warn)
                stat.count <= 20 -> getColor(R.color.orange_alert)
                else -> getColor(R.color.red_danger)
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
            getString(R.string.reminder_peak_hour, timeRange)
        }

        binding.tvAdvice.text = peakHint ?: when {
            todayCount == 0 -> getString(R.string.reminder_no_smoke_today)
            todayCount > dailyTarget -> getString(R.string.reminder_over_target)
            weekReport.trend == -1 -> getString(R.string.reminder_trend_down)
            weekReport.trend == 1 -> getString(R.string.reminder_trend_up)
            weekReport.avgDaily.toFloatOrNull()?.let { it >= 10f } == true -> getString(R.string.reminder_heavy)
            else -> getString(R.string.reminder_normal)
        }
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
                    resources.getColor(R.color.bar_highlight_stroke, theme)
                )
            }
        }
    }
}
