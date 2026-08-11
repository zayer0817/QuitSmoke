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
        return recordSmokeAt(adjustedTimestamp)
    }

    suspend fun recordSmokeAt(timestamp: Long): SmokeRecord {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)

        val record = SmokeRecord(
            timestamp = timestamp,
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

    suspend fun getCountByHourRange(dateStr: String, startHour: Int, endHour: Int): Int {
        return dao.getCountByHourRange(dateStr, startHour, endHour)
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

    // ==================== AI 区间分析数据 ====================

    /** 获取最早记录日期，用于「全部」区间起点 */
    suspend fun getFirstRecordDate(): String? = dao.getFirstRecordDate()

    /** 小时 → 时段索引：0=早间 1=午间 2=晚间 3=深夜 */
    private fun hourToPeriodIndex(hour: Int): Int = when (hour) {
        in 6..10 -> 0
        in 11..16 -> 1
        in 17..22 -> 2
        else -> 3
    }

    /**
     * 基于区间内原始记录做基础统计分析，供 AI 提示词参考。
     * 计算：周末 vs 工作日各时段日均及差异%、零记录分布、趋势、峰值/低谷、最集中时段。
     * 日均按「自然天数」（含零记录天）计算，这样漏记会真实反映在低数值上。
     */
    private suspend fun computeBaselineAnalysis(
        startDate: String,
        endDate: String,
        allDays: List<DailyStat>
    ): BaselineAnalysis {
        val records = dao.getRecordsByDateRange(startDate, endDate)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()

        var weekendDays = 0
        var weekdayDays = 0
        var zeroWeekend = 0
        var zeroWeekday = 0
        val weekendPeriod = IntArray(4)
        val weekdayPeriod = IntArray(4)

        for (d in allDays) {
            val date = sdf.parse(d.dateStr) ?: continue
            cal.time = date
            val isWeekend = cal.get(Calendar.DAY_OF_WEEK).let {
                it == Calendar.SATURDAY || it == Calendar.SUNDAY
            }
            if (isWeekend) {
                weekendDays++
                if (d.count == 0) zeroWeekend++
            } else {
                weekdayDays++
                if (d.count == 0) zeroWeekday++
            }
        }

        for (r in records) {
            val date = sdf.parse(r.dateStr) ?: continue
            cal.time = date
            val isWeekend = cal.get(Calendar.DAY_OF_WEEK).let {
                it == Calendar.SATURDAY || it == Calendar.SUNDAY
            }
            val p = hourToPeriodIndex(r.hourOfDay)
            if (isWeekend) weekendPeriod[p]++ else weekdayPeriod[p]++
        }

        fun avg(count: Int, days: Int): Double = if (days > 0) count.toDouble() / days else 0.0
        fun fmt(d: Double): String = String.format(Locale.getDefault(), "%.1f", d)
        fun diffLine(label: String, wAvg: Double, wdAvg: Double): String {
            val sb = StringBuilder("- $label：周末 ${fmt(wAvg)} 根/天 vs 工作日 ${fmt(wdAvg)} 根/天")
            if (wdAvg > 0.05 && wAvg > 0.05) {
                val pct = ((wdAvg - wAvg) / wdAvg * 100).toInt()
                if (pct != 0) {
                    sb.append("（周末${if (pct > 0) "低" else "高"} ${kotlin.math.abs(pct)}%）")
                }
            }
            return sb.toString()
        }

        val wm = avg(weekendPeriod[0], weekendDays)
        val wdm = avg(weekdayPeriod[0], weekdayDays)
        val wn = avg(weekendPeriod[1], weekendDays)
        val wdn = avg(weekdayPeriod[1], weekdayDays)
        val we = avg(weekendPeriod[2], weekendDays)
        val wde = avg(weekdayPeriod[2], weekdayDays)
        val wni = avg(weekendPeriod[3], weekendDays)
        val wdni = avg(weekdayPeriod[3], weekdayDays)

        val total = allDays.size
        val third = (total / 3).coerceAtLeast(1)
        val firstThird = allDays.take(third)
        val lastThird = allDays.takeLast(third)
        val firstAvg = if (firstThird.isNotEmpty()) firstThird.sumOf { it.count }.toDouble() / firstThird.size else 0.0
        val lastAvg = if (lastThird.isNotEmpty()) lastThird.sumOf { it.count }.toDouble() / lastThird.size else 0.0
        val trendDir = when {
            firstAvg > 0.05 && lastAvg > firstAvg * 1.1 -> "上升"
            firstAvg > 0.05 && lastAvg < firstAvg * 0.9 -> "下降"
            else -> "基本持平"
        }

        val peak = allDays.maxByOrNull { it.count }
        val low = allDays.filter { it.count > 0 }.minByOrNull { it.count }

        val periodNames = arrayOf("早间", "午间", "晚间", "深夜")
        val periodTotals = IntArray(4) { weekendPeriod[it] + weekdayPeriod[it] }
        val totalRecords = records.size.coerceAtLeast(1)
        val weakestIdx = periodTotals.indices.maxByOrNull { periodTotals[it] } ?: 0
        val weakestPct = periodTotals[weakestIdx] * 100 / totalRecords

        return BaselineAnalysis(
            weekendDays = weekendDays,
            weekdayDays = weekdayDays,
            morningLine = diffLine("早间", wm, wdm),
            noonLine = diffLine("午间", wn, wdn),
            eveningLine = diffLine("晚间", we, wde),
            nightLine = diffLine("深夜", wni, wdni),
            zeroWeekend = zeroWeekend,
            zeroWeekday = zeroWeekday,
            trendText = "前1/3日均 ${fmt(firstAvg)} 根 → 后1/3日均 ${fmt(lastAvg)} 根（$trendDir）",
            peakText = if (peak != null && peak.count > 0) "高峰日：${peak.dateStr} 共 ${peak.count} 根" else "",
            lowText = if (low != null) "最低记录日：${low.dateStr} 共 ${low.count} 根" else "",
            weakestText = "${periodNames[weakestIdx]}，占区间总量 ${weakestPct}%"
        )
    }

    /**
     * 获取指定日期区间的分析数据，格式化为 AI prompt
     * @param startDate 开始日期 yyyy-MM-dd（含）
     * @param endDate 结束日期 yyyy-MM-dd（含）
     * @param rangeLabel 区间描述，如「近30天（2026-07-09 至 2026-08-07）」
     * @return 格式化的数据文本，用于发送给 AI
     */
    suspend fun getAnalysisPrompt(startDate: String, endDate: String, rangeLabel: String): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        // 每日统计
        val dailyStats = dao.getDailyStatsRange(startDate, endDate)
        val statsMap = dailyStats.associateBy { it.dateStr }

        // 填充区间内每一天（没有记录的天补 0）
        val allDays = mutableListOf<DailyStat>()
        val cal = Calendar.getInstance().apply {
            time = sdf.parse(startDate) ?: return ""
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        while (true) {
            val dateStr = sdf.format(cal.time)
            if (dateStr > endDate) break
            allDays.add(DailyStat(dateStr, statsMap[dateStr]?.count ?: 0))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        val totalDays = allDays.size
        val total = allDays.sumOf { it.count }
        val activeDays = allDays.count { it.count > 0 }
        val avgDaily = if (activeDays > 0) String.format(Locale.getDefault(), "%.1f", total.toDouble() / activeDays) else "0"

        // 时段统计
        val morningCount = dao.getCountByDateRangeAndHourRange(startDate, endDate, 6, 11)
        val noonCount = dao.getCountByDateRangeAndHourRange(startDate, endDate, 11, 17)
        val eveningCount = dao.getCountByDateRangeAndHourRange(startDate, endDate, 17, 23)
        val nightCount = dao.getCountByDateRangeAndHourRange(startDate, endDate, 23, 24) +
                         dao.getCountByDateRangeAndHourRange(startDate, endDate, 0, 6)

        // 小时分布
        val hourlyDist = dao.getHourlyDistributionByDateRange(startDate, endDate)
        val hourMap = hourlyDist.associate { it.hourOfDay to it.count }

        // 异常天数（超过日均+50% 或为 0 但该天已过）
        val today = sdf.format(Date())
        val anomalyHigh = allDays.filter { it.count > 0 && it.count > avgDaily.toDouble() * 1.5 }
        val anomalyZero = allDays.filter { it.count == 0 && it.dateStr < today && it.dateStr >= startDate }

        // 基础分析（自动预处理，写进提示词供 AI 参考）
        val baseline = computeBaselineAnalysis(startDate, endDate, allDays)

        // 构建 prompt
        val sb = StringBuilder()
        sb.append("你是一个专业的健康数据分析专家，正在帮用户分析戒烟追踪 App 的吸烟记录数据。\n")
        sb.append("用户的日常习惯：早上约2根、中午约3根、晚上约3根，日均约8根。\n\n")

        val b = baseline
        sb.append("【数据基础分析（已为你预处理，请直接参考，不必重复计算）】\n")
        sb.append("1. 周末 vs 工作日时段日均对比（按自然天数平均，含零记录天）：\n")
        sb.append("   ${b.morningLine}\n")
        sb.append("   ${b.noonLine}\n")
        sb.append("   ${b.eveningLine}\n")
        sb.append("   ${b.nightLine}\n")
        sb.append("   说明：差异为「周末低」时，可能源于周末忘记点记录（漏记）或真实减少，需结合第2条判断。\n")
        sb.append("2. 零记录天数：周末 ${b.zeroWeekend} 天 / 工作日 ${b.zeroWeekday} 天。")
        sb.append("用户日均约8根，单日完全0记录大概率是漏记而非真的没抽。\n")
        sb.append("3. 区间趋势：${b.trendText}\n")
        if (b.peakText.isNotEmpty()) {
            sb.append("4. ${b.peakText}")
            if (b.lowText.isNotEmpty()) sb.append("；${b.lowText}\n") else sb.append("\n")
        } else if (b.lowText.isNotEmpty()) {
            sb.append("4. ${b.lowText}\n")
        }
        sb.append("5. 最集中时段：${b.weakestText}\n\n")

        sb.append("以下是用户 $rangeLabel 的吸烟记录原始数据：\n\n")

        sb.append("## 区间总览\n")
        sb.append("- 总根数：$total 根\n")
        sb.append("- 有记录天数：$activeDays / $totalDays 天\n")
        sb.append("- 日均：$avgDaily 根（按有记录天数算）\n\n")

        sb.append("## 时段分布\n")
        sb.append("- 早间 (6:00-11:00)：$morningCount 根\n")
        sb.append("- 午间 (11:00-17:00)：$noonCount 根\n")
        sb.append("- 晚间 (17:00-23:00)：$eveningCount 根\n")
        sb.append("- 深夜 (23:00-6:00)：$nightCount 根\n\n")

        sb.append("## 每日明细\n")
        for (day in allDays) {
            val dayOfWeek = SimpleDateFormat("E", Locale.CHINESE).format(
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(day.dateStr) ?: Date()
            )
            sb.append("- ${day.dateStr} ($dayOfWeek)：${day.count} 根\n")
        }
        sb.append("\n")

        sb.append("## 24小时分布\n")
        for (h in 0..23) {
            val count = hourMap[h] ?: 0
            if (count > 0) {
                sb.append("- ${String.format("%02d", h)}:00 - ${String.format("%02d", h)}:59：$count 根\n")
            }
        }
        sb.append("\n")

        if (anomalyHigh.isNotEmpty()) {
            sb.append("## 异常偏高天数（超过日均50%以上）\n")
            for (day in anomalyHigh) {
                sb.append("- ${day.dateStr}：${day.count} 根\n")
            }
            sb.append("\n")
        }

        if (anomalyZero.isNotEmpty()) {
            sb.append("## 零记录天数（可能漏记）\n")
            for (day in anomalyZero) {
                sb.append("- ${day.dateStr}：0 根\n")
            }
            sb.append("\n")
        }

        sb.append("## 请你分析以下内容，用 Markdown 格式输出：\n")
        sb.append("1. **区间总评**：一句话概括这个区间的表现\n")
        sb.append("2. **时段分析**：各时段表现如何，哪个时段是薄弱环节\n")
        sb.append("3. **趋势变化**：区间内从头到尾的变化趋势\n")
        sb.append("4. **异常诊断**：异常偏高或零记录的原因推测（注意结合周末/晚间漏记的可能性）\n")
        sb.append("5. **个性化建议**：基于数据给出 2-3 条具体可执行的建议\n")
        sb.append("6. **鼓励语**：一句温暖的鼓励\n")
        sb.append("\n注意：语气要亲切自然，像朋友聊天一样，不要太官方。用 emoji 适当点缀。")

        return sb.toString()
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

/**
 * 基础分析结论，写入 AI 提示词供参考
 */
private data class BaselineAnalysis(
    val weekendDays: Int,
    val weekdayDays: Int,
    val morningLine: String,
    val noonLine: String,
    val eveningLine: String,
    val nightLine: String,
    val zeroWeekend: Int,
    val zeroWeekday: Int,
    val trendText: String,
    val peakText: String,
    val lowText: String,
    val weakestText: String
)
