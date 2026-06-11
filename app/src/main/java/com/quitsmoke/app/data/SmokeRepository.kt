package com.quitsmoke.app.data

import android.content.Context
import androidx.lifecycle.LiveData
import com.quitsmoke.app.AppPreferences
import java.text.SimpleDateFormat
import java.util.*

class SmokeRepository internal constructor(
    private val dao: SmokeRecordDao,
    private val context: Context? = null
) {

    companion object {
        @Volatile
        private var INSTANCE: SmokeRepository? = null

        fun getInstance(context: Context): SmokeRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = SmokeRepository(
                    AppDatabase.getInstance(context.applicationContext).smokeRecordDao(),
                    context.applicationContext
                )
                INSTANCE = instance
                instance
            }
        }
    }

    fun getTodayStr(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun getCurrentHour(): Int {
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    }

    suspend fun recordSmoke(offsetMinutes: Int = 0): SmokeRecord {
        val now = System.currentTimeMillis()
        val adjustedTimestamp = now + offsetMinutes * 60_000L
        val calendar = Calendar.getInstance().apply { timeInMillis = adjustedTimestamp }
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)

        val record = SmokeRecord(
            timestamp = adjustedTimestamp,
            dateStr = dateStr,
            hourOfDay = hourOfDay
        )
        val id = dao.insert(record)
        return record.copy(id = id)
    }

    suspend fun undoLastSmoke(): Boolean {
        val latest = dao.getLatestRecord()
        if (latest != null && isToday(latest.dateStr)) {
            dao.deleteById(latest.id)
            return true
        }
        return false
    }

    suspend fun deleteRecord(id: Long) {
        dao.deleteById(id)
    }

    suspend fun getTodayCount(): Int {
        return dao.getCountByDate(getTodayStr())
    }

    suspend fun getTotalCount(): Int {
        return dao.getTotalCount()
    }

    fun getTodayCountLive(): LiveData<Int> {
        return dao.getCountByDateLive(getTodayStr())
    }

    suspend fun getWeeklyStats(): List<DailyStat> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -30)
        return dao.getDailyStats(calendar.timeInMillis)
    }

    suspend fun getTodayRecords(): List<SmokeRecord> {
        return dao.getRecordsByDate(getTodayStr())
    }

    suspend fun getRecordsByDate(dateStr: String): List<SmokeRecord> {
        return dao.getRecordsByDate(dateStr)
    }

    suspend fun getHourlyDistribution(): List<HourlyStat> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -30)
        return dao.getHourlyDistribution(calendar.timeInMillis)
    }

    fun getAllRecordsLive(): LiveData<List<SmokeRecord>> {
        return dao.getAllRecordsLive()
    }

    suspend fun getAllRecordsForExport(): List<SmokeRecord> {
        return dao.getAllRecords()
    }

    suspend fun previewInsertRecords(records: List<SmokeRecord>): Int {
        return findNewImportRecords(records).size
    }

    suspend fun insertRecords(records: List<SmokeRecord>): Int {
        val newRecords = findNewImportRecords(records)
        if (newRecords.isNotEmpty()) {
            dao.insertAll(newRecords)
        }
        return newRecords.size
    }

    private suspend fun findNewImportRecords(records: List<SmokeRecord>): List<SmokeRecord> {
        val uniqueRecords = records.distinctBy {
            ImportedRecordKey(it.timestamp, it.dateStr, it.hourOfDay, it.note)
        }
        val newRecords = mutableListOf<SmokeRecord>()
        for (record in uniqueRecords) {
            val duplicate = dao.findDuplicate(
                timestamp = record.timestamp,
                dateStr = record.dateStr,
                hourOfDay = record.hourOfDay,
                note = record.note
            )
            if (duplicate == null) {
                newRecords.add(record)
            }
        }
        return newRecords
    }

    suspend fun insertRecord(record: SmokeRecord) {
        dao.insert(record)
    }

    suspend fun getWeekReport(): WeekReport {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val trackingStartDate = getTrackingStartDate()
        val calendar = Calendar.getInstance()

        val endDate = sdf.format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -6)
        val sevenDayStartDate = sdf.format(calendar.time)
        val startDate = maxDateStr(sevenDayStartDate, trackingStartDate)

        val statsMap = dao.getDailyStatsRange(startDate, endDate)
            .associateBy { it.dateStr }

        val last7Days = mutableListOf<DailyStat>()
        val cal = Calendar.getInstance()
        for (i in 6 downTo 0) {
            cal.time = Date()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dateStr = sdf.format(cal.time)
            if (dateStr >= trackingStartDate) {
                last7Days.add(DailyStat(dateStr, statsMap[dateStr]?.count ?: 0))
            }
        }

        val totalWeek = last7Days.sumOf { it.count }
        val activeDays = last7Days.size.coerceAtLeast(1)
        val avgDaily = if (totalWeek > 0) {
            String.format(Locale.getDefault(), "%.1f", totalWeek / activeDays.toDouble())
        } else {
            "0"
        }
        val todayCount = last7Days.lastOrNull()?.count ?: 0

        val cal2 = Calendar.getInstance()
        cal2.add(Calendar.DAY_OF_YEAR, -13)
        val prevEnd = sdf.format(cal2.apply { add(Calendar.DAY_OF_YEAR, 6) }.time)
        val prevStart = sdf.format(cal2.apply { add(Calendar.DAY_OF_YEAR, -6) }.time)
        val prevWeekTotal = if (prevEnd >= trackingStartDate) {
            val safePrevStart = maxDateStr(prevStart, trackingStartDate)
            dao.getDailyStatsRange(safePrevStart, prevEnd).sumOf { it.count }
        } else {
            0
        }

        val trend = when {
            prevWeekTotal == 0 -> if (totalWeek > 0) 1 else 0
            totalWeek > prevWeekTotal -> 1
            totalWeek < prevWeekTotal -> -1
            else -> 0
        }

        return WeekReport(
            dailyStats = last7Days,
            totalWeek = totalWeek,
            avgDaily = avgDaily,
            todayCount = todayCount,
            trend = trend,
            prevWeekTotal = prevWeekTotal
        )
    }

    suspend fun getGoalReport(dailyTarget: Int): GoalReport {
        val target = dailyTarget.coerceAtLeast(1)
        val todayStr = getTodayStr()
        val todayCount = getTodayCount()
        val remaining = (target - todayCount).coerceAtLeast(0)

        val targetStreak = getBackwardStreak { count -> count <= target }
        val noSmokeStreak = getBackwardStreak { count -> count == 0 }
        val monthReport = getMonthReport(target, todayStr)

        return GoalReport(
            dailyTarget = target,
            todayCount = todayCount,
            remaining = remaining,
            targetStreak = targetStreak,
            noSmokeStreak = noSmokeStreak,
            monthTotal = monthReport.total,
            monthAvgDaily = monthReport.avgDaily,
            monthTargetDays = monthReport.targetDays,
            monthSmokeFreeDays = monthReport.smokeFreeDays,
            monthDaysElapsed = monthReport.daysElapsed
        )
    }

    private suspend fun getBackwardStreak(matches: (Int) -> Boolean): Int {
        val trackingStartDate = getTrackingStartDate()
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -364)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val earliestDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        if (earliestDate < trackingStartDate) {
            calendar.time = parseDate(trackingStartDate)
        }
        val statsMap = dao.getDailyStatsDesc(calendar.timeInMillis)
            .associateBy { it.dateStr }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        var streak = 0

        repeat(365) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -it)
            val dateStr = sdf.format(cal.time)
            if (dateStr < trackingStartDate) {
                return streak
            }
            val count = statsMap[dateStr]?.count ?: 0
            if (!matches(count)) {
                return streak
            }
            streak++
        }

        return streak
    }

    private suspend fun getTrackingStartDate(): String {
        val appContext = context
        if (appContext != null) {
            AppPreferences.getTrackingStartDate(appContext)?.let { return it }
        }

        val firstRecordDate = dao.getFirstRecordDate()
        val startDate = firstRecordDate ?: getTodayStr()
        if (appContext != null) {
            AppPreferences.setTrackingStartDate(appContext, startDate)
        }
        return startDate
    }

    private fun maxDateStr(first: String, second: String): String {
        return if (first >= second) first else second
    }

    private fun parseDate(dateStr: String): Date {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr) ?: Date()
    }

    private suspend fun getMonthReport(target: Int, todayStr: String): MonthReport {
        val calendar = Calendar.getInstance()
        val daysElapsed = calendar.get(Calendar.DAY_OF_MONTH)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val stats = dao.getDailyStatsSince(calendar.timeInMillis)
            .filter { it.dateStr <= todayStr }
            .associateBy { it.dateStr }

        var total = 0
        var targetDays = 0
        var smokeFreeDays = 0

        repeat(daysElapsed) {
            val dateStr = sdf.format(calendar.time)
            val count = stats[dateStr]?.count ?: 0
            total += count
            if (count <= target) targetDays++
            if (count == 0) smokeFreeDays++
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        return MonthReport(
            total = total,
            avgDaily = if (daysElapsed > 0) String.format(Locale.getDefault(), "%.1f", total / daysElapsed.toDouble()) else "0",
            targetDays = targetDays,
            smokeFreeDays = smokeFreeDays,
            daysElapsed = daysElapsed
        )
    }

    private fun isToday(dateStr: String): Boolean {
        return dateStr == getTodayStr()
    }
}

private data class ImportedRecordKey(
    val timestamp: Long,
    val dateStr: String,
    val hourOfDay: Int,
    val note: String
)

data class WeekReport(
    val dailyStats: List<DailyStat>,
    val totalWeek: Int,
    val avgDaily: String,
    val todayCount: Int,
    val trend: Int,
    val prevWeekTotal: Int
)

data class GoalReport(
    val dailyTarget: Int,
    val todayCount: Int,
    val remaining: Int,
    val targetStreak: Int,
    val noSmokeStreak: Int,
    val monthTotal: Int,
    val monthAvgDaily: String,
    val monthTargetDays: Int,
    val monthSmokeFreeDays: Int,
    val monthDaysElapsed: Int
)

private data class MonthReport(
    val total: Int,
    val avgDaily: String,
    val targetDays: Int,
    val smokeFreeDays: Int,
    val daysElapsed: Int
)
