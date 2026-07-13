package com.quitsmoke.app

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
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
    name = PREFS_NAME,
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, PREFS_NAME))
    }
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
}
