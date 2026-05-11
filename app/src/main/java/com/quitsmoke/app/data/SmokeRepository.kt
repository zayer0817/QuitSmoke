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

    /** 批量插入记录（用于导入），跳过已经存在的同一条记录 */
    suspend fun insertRecords(records: List<SmokeRecord>): Int {
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

        if (newRecords.isNotEmpty()) {
            dao.insertAll(newRecords)
        }

        return newRecords.size
    }

    /** 插入单条记录（手动添加） */
    suspend fun insertRecord(record: SmokeRecord) {
        dao.insert(record)
    }

    // ========== 统计分析 ==========

    /**
     * 获取周报数据
     * 返回最近7天每天的抽烟次数
     */
    suspend fun getWeekReport(): WeekReport {
        val stats = getWeeklyStats()
        val todayStr = getTodayStr()

        // 最近7天的数据
        val last7Days = mutableListOf<DailyStat>()
        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        for (i in 6 downTo 0) {
            calendar.time = Date()
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            val dateStr = sdf.format(calendar.time)
            val stat = stats.find { it.dateStr == dateStr }
            last7Days.add(DailyStat(dateStr, stat?.count ?: 0))
        }

        val totalWeek = last7Days.sumOf { it.count }
        val avgDaily = if (totalWeek > 0) String.format("%.1f", totalWeek / 7.0) else "0"
        val todayCount = last7Days.last().count

        // 计算与上周对比
        val calendar2 = Calendar.getInstance()
        calendar2.add(Calendar.DAY_OF_YEAR, -13)
        val prevWeekStats = dao.getDailyStats(calendar2.timeInMillis)
            .filter { it.dateStr < last7Days.first().dateStr }
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
