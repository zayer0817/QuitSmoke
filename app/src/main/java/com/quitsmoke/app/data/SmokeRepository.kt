package com.quitsmoke.app.data

import android.content.Context
import androidx.lifecycle.LiveData
import java.text.SimpleDateFormat
import java.util.*

/**
 * 数据仓库 - 封装所有数据操作
 * 小组件和Activity都通过此仓库访问数据
 */
class SmokeRepository private constructor(context: Context) {

    private val dao = AppDatabase.getInstance(context).smokeRecordDao()

    companion object {
        @Volatile
        private var INSTANCE: SmokeRepository? = null

        fun getInstance(context: Context): SmokeRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = SmokeRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    // ========== 日期工具 ==========

    /** 获取今天的日期字符串 */
    fun getTodayStr(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    /** 获取当前小时 */
    fun getCurrentHour(): Int {
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    }

    // ========== 写操作 ==========

    /** 记录一次抽烟 */
    suspend fun recordSmoke(): SmokeRecord {
        val record = SmokeRecord(
            timestamp = System.currentTimeMillis(),
            dateStr = getTodayStr(),
            hourOfDay = getCurrentHour()
        )
        val id = dao.insert(record)
        return record.copy(id = id)
    }

    /** 撤销最近一次记录 */
    suspend fun undoLastSmoke(): Boolean {
        val latest = dao.getLatestRecord()
        if (latest != null && isToday(latest.dateStr)) {
            dao.deleteById(latest.id)
            return true
        }
        return false
    }

    // ========== 读操作 ==========

    /** 获取今天的抽烟次数 */
    suspend fun getTodayCount(): Int {
        return dao.getCountByDate(getTodayStr())
    }

    /** 获取总记录数 */
    suspend fun getTotalCount(): Int {
        return dao.getTotalCount()
    }

    /** 获取今天记录数（LiveData） */
    fun getTodayCountLive(): LiveData<Int> {
        return dao.getCountByDateLive(getTodayStr())
    }

    /** 获取最近N天的每日统计 */
    suspend fun getWeeklyStats(): List<DailyStat> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -30) // 最近30天
        return dao.getDailyStats(calendar.timeInMillis)
    }

    /** 获取今天的记录列表 */
    suspend fun getTodayRecords(): List<SmokeRecord> {
        return dao.getRecordsByDate(getTodayStr())
    }

    /** 获取指定日期的记录 */
    suspend fun getRecordsByDate(dateStr: String): List<SmokeRecord> {
        return dao.getRecordsByDate(dateStr)
    }

    /** 获取时段分布 */
    suspend fun getHourlyDistribution(): List<HourlyStat> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -30)
        return dao.getHourlyDistribution(calendar.timeInMillis)
    }

    /** 获取所有记录（LiveData） */
    fun getAllRecordsLive(): LiveData<List<SmokeRecord>> {
        return dao.getAllRecordsLive()
    }

    /** 获取所有记录用于导出 */
    suspend fun getAllRecordsForExport(): List<SmokeRecord> {
        return dao.getAllRecords()
    }

    /** 预览导入记录，返回实际会新增的记录数 */
    suspend fun previewInsertRecords(records: List<SmokeRecord>): Int {
        return findNewImportRecords(records).size
    }

    /** 批量插入记录（用于导入），跳过已经存在的同一条记录 */
    suspend fun insertRecords(records: List<SmokeRecord>): Int {
        val newRecords = findNewImportRecords(records)

        if (newRecords.isNotEmpty()) {
            dao.insertAll(newRecords)
        }

        return newRecords.size
    }

    private suspend fun findNewImportRecords(records: List<SmokeRecord>): List<SmokeRecord> {
        val uniqueRecords = records.distinctBy {
            ImportedRecordKey(
                timestamp = it.timestamp,
                dateStr = it.dateStr,
                hourOfDay = it.hourOfDay,
                note = it.note
            )
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

    /** 插入单条记录（手动添加） */
    suspend fun insertRecord(record: SmokeRecord) {
        dao.insert(record)
    }

    // ========== 统计分析 ==========

    /**
     * 获取周报数据
     * 返回最近7天每天的抽烟次数
     *
     * 优化：直接查询7天范围，不再查30天再过滤
     */
    suspend fun getWeekReport(): WeekReport {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()

        // 本周：今天和前6天
        val endDate = sdf.format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -6)
        val startDate = sdf.format(calendar.time)

        // 一次查询获取7天数据
        val statsMap = dao.getDailyStatsRange(startDate, endDate)
            .associateBy { it.dateStr }

        val last7Days = mutableListOf<DailyStat>()
        val cal = Calendar.getInstance()
        for (i in 6 downTo 0) {
            cal.time = Date()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dateStr = sdf.format(cal.time)
            last7Days.add(DailyStat(dateStr, statsMap[dateStr]?.count ?: 0))
        }

        val totalWeek = last7Days.sumOf { it.count }
        val avgDaily = if (totalWeek > 0) String.format("%.1f", totalWeek / 7.0) else "0"
        val todayCount = last7Days.last().count

        // 上周：精确查询7天范围
        val cal2 = Calendar.getInstance()
        cal2.add(Calendar.DAY_OF_YEAR, -13)
        val prevEnd = sdf.format(cal2.apply { add(Calendar.DAY_OF_YEAR, 6) }.time)
        val prevStart = sdf.format(cal2.apply { add(Calendar.DAY_OF_YEAR, -6) }.time)
        val prevWeekStats = dao.getDailyStatsRange(prevStart, prevEnd)
        val prevWeekTotal = prevWeekStats.sumOf { it.count }

        val trend = when {
            prevWeekTotal == 0 -> if (totalWeek > 0) 1 else 0
            totalWeek > prevWeekTotal -> 1     // 上升
            totalWeek < prevWeekTotal -> -1    // 下降
            else -> 0                          // 持平
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

    /**
     * 从今天往前数，计算连续满足条件的天数
     *
     * 优化：一次 SQL 查询获取最近365天数据（降序），在内存中遍历，
     * 替代原来循环365次 SQL 的做法。
     */
    private suspend fun getBackwardStreak(matches: (Int) -> Boolean): Int {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -364) // 365天前
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val statsMap = dao.getDailyStatsDesc(calendar.timeInMillis)
            .associateBy { it.dateStr }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        var streak = 0

        repeat(365) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -it)
            val dateStr = sdf.format(cal.time)
            val count = statsMap[dateStr]?.count ?: 0
            if (!matches(count)) {
                return streak
            }
            streak++
        }

        return streak
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

/**
 * 周报数据类
 */
data class WeekReport(
    val dailyStats: List<DailyStat>,
    val totalWeek: Int,
    val avgDaily: String,
    val todayCount: Int,
    val trend: Int,       // 1=上升, -1=下降, 0=持平
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
