package com.quitsmoke.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quitsmoke.app.data.DailyStat
import com.quitsmoke.app.data.GoalReport
import com.quitsmoke.app.data.HourlyStat
import com.quitsmoke.app.data.SmokeRepository
import com.quitsmoke.app.data.WeekReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val todayCount: Int = 0,
    val dailyTarget: Int = AppPreferences.DEFAULT_DAILY_TARGET,
    val goalReport: GoalReport? = null,
    val weekReport: WeekReport? = null,
    val hourlyStats: List<HourlyStat> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SmokeRepository.getInstance(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    fun loadData() {
        viewModelScope.launch {
            loadDataInternal()
        }
    }

    fun recordSmoke(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.recordSmoke()
            loadDataInternal()
            onDone()
        }
    }

    fun undoLastSmoke(onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repo.undoLastSmoke()
            if (success) loadDataInternal()
            onDone(success)
        }
    }

    private suspend fun loadDataInternal() {
        val target = AppPreferences.getDailyTarget(getApplication())
        val todayCount = repo.getTodayCount()
        val goalReport = repo.getGoalReport(target)
        val weekReport = repo.getWeekReport()
        val hourly = repo.getHourlyDistribution()

        _uiState.value = MainUiState(
            todayCount = todayCount,
            dailyTarget = target,
            goalReport = goalReport,
            weekReport = weekReport,
            hourlyStats = hourly
        )
    }
}
