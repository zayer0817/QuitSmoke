package com.quitsmoke.app

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val PREFS_NAME = "quitsmoke_prefs"
private const val KEY_THEME_MODE_NAME = "theme_mode"
private const val KEY_DAILY_TARGET_NAME = "daily_target"
private const val KEY_TRACKING_START_DATE_NAME = "tracking_start_date"
private const val KEY_THEME_COLOR_NAME = "theme_color"

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = PREFS_NAME
    // 不再使用 SharedPreferencesMigration——它会删除 SP 文件，
    // 导致所有 getCachedXxx() 读 SP 时返回默认值，主题色偶发回退
)

object AppPreferences {

    private val KEY_THEME_MODE = intPreferencesKey(KEY_THEME_MODE_NAME)
    private val KEY_DAILY_TARGET = intPreferencesKey(KEY_DAILY_TARGET_NAME)
    private val KEY_TRACKING_START_DATE = stringPreferencesKey(KEY_TRACKING_START_DATE_NAME)
    private val KEY_THEME_COLOR = stringPreferencesKey(KEY_THEME_COLOR_NAME)

    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2

    const val DEFAULT_DAILY_TARGET = 10
    const val MIN_DAILY_TARGET = 1
    const val MAX_DAILY_TARGET = 99

    const val DEFAULT_THEME_COLOR = "#2E6B2A"

    val PRESET_COLORS = listOf(
        "#2E6B2A", // 绿色
        "#1565C0", // 蓝色
        "#6750A4", // 紫色
        "#E65100", // 橙色
        "#AD1457", // 粉色
        "#00695C", // 青色
    )

    fun getThemeModeFlow(context: Context): Flow<Int> =
        context.dataStore.data.map { it[KEY_THEME_MODE] ?: MODE_SYSTEM }

    suspend fun getThemeMode(context: Context): Int =
        context.dataStore.data.map { it[KEY_THEME_MODE] ?: MODE_SYSTEM }.first()

    suspend fun setThemeMode(context: Context, mode: Int) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_THEME_MODE_NAME, mode)
            .apply()
        applyTheme(mode)
    }

    fun applyTheme(mode: Int) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    suspend fun initTheme(context: Context) {
        applyTheme(getThemeMode(context))
    }

    fun getCachedThemeMode(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_THEME_MODE_NAME, MODE_SYSTEM)
            .takeIf { it == MODE_SYSTEM || it == MODE_LIGHT || it == MODE_DARK }
            ?: MODE_SYSTEM
    }

    fun getModeName(mode: Int): String = when (mode) {
        MODE_LIGHT -> "浅色"
        MODE_DARK -> "深色"
        else -> "跟随系统"
    }

    fun getDailyTargetFlow(context: Context): Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_DAILY_TARGET] ?: DEFAULT_DAILY_TARGET)
                .coerceIn(MIN_DAILY_TARGET, MAX_DAILY_TARGET)
        }

    suspend fun getDailyTarget(context: Context): Int =
        getDailyTargetFlow(context).first()

    suspend fun setDailyTarget(context: Context, target: Int) {
        val safeTarget = target.coerceIn(MIN_DAILY_TARGET, MAX_DAILY_TARGET)
        context.dataStore.edit {
            it[KEY_DAILY_TARGET] = safeTarget
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DAILY_TARGET_NAME, safeTarget)
            .apply()
    }

    suspend fun getTrackingStartDate(context: Context): String? =
        context.dataStore.data.map { it[KEY_TRACKING_START_DATE] }.first()

    suspend fun setTrackingStartDate(context: Context, dateStr: String) {
        context.dataStore.edit {
            it[KEY_TRACKING_START_DATE] = dateStr
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TRACKING_START_DATE_NAME, dateStr)
            .apply()
    }

    fun getThemeColorFlow(context: Context): Flow<String> =
        context.dataStore.data.map { it[KEY_THEME_COLOR] ?: DEFAULT_THEME_COLOR }

    suspend fun getThemeColor(context: Context): String =
        getThemeColorFlow(context).first()

    fun getCachedThemeColor(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_COLOR_NAME, DEFAULT_THEME_COLOR) ?: DEFAULT_THEME_COLOR
    }

    suspend fun setThemeColor(context: Context, color: String) {
        context.dataStore.edit {
            it[KEY_THEME_COLOR] = color
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_COLOR_NAME, color)
            .apply()
    }

    // ==================== 自动补录提醒 ====================

    private const val KEY_REMINDER_ENABLED = "reminder_enabled"
    private const val KEY_MORNING_EXPECTED = "morning_expected"
    private const val KEY_NOON_EXPECTED = "noon_expected"
    private const val KEY_EVENING_EXPECTED = "evening_expected"
    private const val KEY_MORNING_CHECK_HOUR = "morning_check_hour"
    private const val KEY_MORNING_CHECK_MINUTE = "morning_check_minute"
    private const val KEY_NOON_CHECK_HOUR = "noon_check_hour"
    private const val KEY_NOON_CHECK_MINUTE = "noon_check_minute"
    private const val KEY_EVENING_CHECK_HOUR = "evening_check_hour"
    private const val KEY_EVENING_CHECK_MINUTE = "evening_check_minute"
    private const val KEY_END_OF_DAY_CHECK_HOUR = "eod_check_hour"
    private const val KEY_END_OF_DAY_CHECK_MINUTE = "eod_check_minute"
    private const val KEY_SKIP_DATE = "reminder_skip_date"
    private const val KEY_SKIP_PERIODS = "reminder_skip_periods"

    fun isReminderEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_REMINDER_ENABLED, true)
    }

    fun setReminderEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_REMINDER_ENABLED, enabled).apply()
    }

    fun getExpectedCount(context: Context, period: String): Int {
        val key = when (period) {
            "morning" -> KEY_MORNING_EXPECTED
            "noon" -> KEY_NOON_EXPECTED
            "evening" -> KEY_EVENING_EXPECTED
            else -> return 0
        }
        val default = when (period) {
            "morning" -> 2
            "noon" -> 3
            "evening" -> 3
            else -> 0
        }
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(key, default)
    }

    fun setExpectedCount(context: Context, period: String, count: Int) {
        val key = when (period) {
            "morning" -> KEY_MORNING_EXPECTED
            "noon" -> KEY_NOON_EXPECTED
            "evening" -> KEY_EVENING_EXPECTED
            else -> return
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(key, count).apply()
    }

    fun getCheckTime(context: Context, period: String): Pair<Int, Int> {
        val (hourKey, minuteKey) = when (period) {
            "morning" -> KEY_MORNING_CHECK_HOUR to KEY_MORNING_CHECK_MINUTE
            "noon" -> KEY_NOON_CHECK_HOUR to KEY_NOON_CHECK_MINUTE
            "evening" -> KEY_EVENING_CHECK_HOUR to KEY_EVENING_CHECK_MINUTE
            "end_of_day" -> KEY_END_OF_DAY_CHECK_HOUR to KEY_END_OF_DAY_CHECK_MINUTE
            else -> return 0 to 0
        }
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultHour = when (period) {
            "morning" -> 10
            "noon" -> 15
            "evening" -> 21
            "end_of_day" -> 23
            else -> 0
        }
        val defaultMinute = when (period) {
            "end_of_day" -> 30
            else -> 0
        }
        return sp.getInt(hourKey, defaultHour) to sp.getInt(minuteKey, defaultMinute)
    }

    fun setCheckTime(context: Context, period: String, hour: Int, minute: Int) {
        val (hourKey, minuteKey) = when (period) {
            "morning" -> KEY_MORNING_CHECK_HOUR to KEY_MORNING_CHECK_MINUTE
            "noon" -> KEY_NOON_CHECK_HOUR to KEY_NOON_CHECK_MINUTE
            "evening" -> KEY_EVENING_CHECK_HOUR to KEY_EVENING_CHECK_MINUTE
            "end_of_day" -> KEY_END_OF_DAY_CHECK_HOUR to KEY_END_OF_DAY_CHECK_MINUTE
            else -> return
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(hourKey, hour).putInt(minuteKey, minute).apply()
    }

    /**
     * 检查今天某个时段是否已被跳过
     */
    fun isPeriodSkippedToday(context: Context, period: String): Boolean {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        val skipDate = sp.getString(KEY_SKIP_DATE, "") ?: ""
        if (skipDate != todayStr) return false
        val skipped = sp.getString(KEY_SKIP_PERIODS, "") ?: ""
        return skipped.split(",").contains(period)
    }

    /**
     * 标记今天某个时段已跳过，今天内不再提醒
     */
    fun markPeriodSkippedToday(context: Context, period: String) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        val existingDate = sp.getString(KEY_SKIP_DATE, "") ?: ""
        val existingPeriods = if (existingDate == todayStr) {
            sp.getString(KEY_SKIP_PERIODS, "") ?: ""
        } else {
            ""
        }
        val newPeriods = if (existingPeriods.isEmpty()) period
        else if (existingPeriods.split(",").contains(period)) existingPeriods
        else "$existingPeriods,$period"
        sp.edit()
            .putString(KEY_SKIP_DATE, todayStr)
            .putString(KEY_SKIP_PERIODS, newPeriods)
            .apply()
    }

    /**
     * 获取时段的时间范围（起始小时, 结束小时）
     */
    fun getPeriodHourRange(period: String): Pair<Int, Int> {
        return when (period) {
            "morning" -> 6 to 11
            "noon" -> 11 to 17
            "evening" -> 17 to 23
            else -> 0 to 0
        }
    }

    fun getPeriodLabel(period: String): String {
        return when (period) {
            "morning" -> "早间"
            "noon" -> "午间"
            "evening" -> "晚间"
            else -> period
        }
    }

    // ==================== AI 分析 ====================

    private const val KEY_AI_API_KEY = "ai_api_key"
    private const val KEY_CACHED_REPORT = "cached_report_md"
    private const val KEY_CACHED_REPORT_LABEL = "cached_report_label"
    private const val KEY_CACHED_REPORT_START = "cached_report_start"
    private const val KEY_CACHED_REPORT_END = "cached_report_end"
    private const val KEY_CACHED_REPORT_TIME = "cached_report_time"

    /** 缓存上次分析报告，下次进入页面可直接查看 */
    fun saveCachedReport(context: Context, markdown: String, label: String, start: String, end: String) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date())
        sp.edit()
            .putString(KEY_CACHED_REPORT, markdown)
            .putString(KEY_CACHED_REPORT_LABEL, label)
            .putString(KEY_CACHED_REPORT_START, start)
            .putString(KEY_CACHED_REPORT_END, end)
            .putString(KEY_CACHED_REPORT_TIME, now)
            .apply()
    }

    fun getCachedReport(context: Context): String? {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val md = sp.getString(KEY_CACHED_REPORT, null)
        return md?.takeIf { it.isNotBlank() }
    }

    data class CachedReportMeta(
        val label: String,
        val start: String,
        val end: String,
        val time: String
    )

    fun getCachedReportMeta(context: Context): CachedReportMeta? {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val md = sp.getString(KEY_CACHED_REPORT, null)
        if (md.isNullOrBlank()) return null
        return CachedReportMeta(
            label = sp.getString(KEY_CACHED_REPORT_LABEL, "") ?: "",
            start = sp.getString(KEY_CACHED_REPORT_START, "") ?: "",
            end = sp.getString(KEY_CACHED_REPORT_END, "") ?: "",
            time = sp.getString(KEY_CACHED_REPORT_TIME, "") ?: ""
        )
    }

    fun clearCachedReport(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CACHED_REPORT)
            .remove(KEY_CACHED_REPORT_LABEL)
            .remove(KEY_CACHED_REPORT_START)
            .remove(KEY_CACHED_REPORT_END)
            .remove(KEY_CACHED_REPORT_TIME)
            .apply()
    }

    fun getAiApiKey(context: Context): String {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_AI_API_KEY, "") ?: ""
        // 用户未手动配置时，使用构建时注入的默认 Key（local.properties）
        return saved.ifBlank { BuildConfig.DEEPSEEK_API_KEY }
    }

    fun setAiApiKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_AI_API_KEY, key).apply()
    }
}
