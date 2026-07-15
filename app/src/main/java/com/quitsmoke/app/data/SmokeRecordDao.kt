package com.quitsmoke.app.data

import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * 抽烟记录的DAO接口
 */
@Dao
interface SmokeRecordDao {

    /** 插入一条记录 */
    @Insert
    suspend fun insert(record: SmokeRecord): Long

    /** 删除一条记录（用于撤销操作） */
    @Delete
    suspend fun delete(record: SmokeRecord)

    /** 删除指定ID的记录 */
    @Query("DELETE FROM smoke_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 获取今天所有记录 */
    @Query("SELECT * FROM smoke_records WHERE dateStr = :dateStr ORDER BY timestamp DESC")
    suspend fun getRecordsByDate(dateStr: String): List<SmokeRecord>

    /** 获取今天记录数 */
    @Query("SELECT COUNT(*) FROM smoke_records WHERE dateStr = :dateStr")
    suspend fun getCountByDate(dateStr: String): Int

    /** 获取今天记录数（LiveData，用于小组件和UI实时更新） */
    @Query("SELECT COUNT(*) FROM smoke_records WHERE dateStr = :dateStr")
    fun getCountByDateLive(dateStr: String): LiveData<Int>

    /** 获取最近N天的每日统计 */
    @Query("""
        SELECT dateStr, COUNT(*) as count 
        FROM smoke_records 
        WHERE timestamp >= :startTimestamp 
        GROUP BY dateStr 
        ORDER BY dateStr ASC
    """)
    suspend fun getDailyStats(startTimestamp: Long): List<DailyStat>

    /** 获取最近一条记录（用于撤销） */
    @Query("SELECT * FROM smoke_records ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestRecord(): SmokeRecord?

    /** 获取所有记录，按时间倒序 */
    @Query("SELECT * FROM smoke_records ORDER BY timestamp DESC")
    fun getAllRecordsLive(): LiveData<List<SmokeRecord>>

    /** 获取按时段分布统计（最近30天） */
    @Query("""
        SELECT hourOfDay, COUNT(*) as count 
        FROM smoke_records 
        WHERE timestamp >= :startTimestamp 
        GROUP BY hourOfDay 
        ORDER BY hourOfDay ASC
    """)
    suspend fun getHourlyDistribution(startTimestamp: Long): List<HourlyStat>

    /** 获取总记录数 */
    @Query("SELECT COUNT(*) FROM smoke_records")
    suspend fun getTotalCount(): Int

    /** 获取最早记录日期，用于确定统计观察期起点 */
    @Query("SELECT dateStr FROM smoke_records ORDER BY dateStr ASC LIMIT 1")
    suspend fun getFirstRecordDate(): String?

    /** 获取所有记录用于导出 */
    @Query("SELECT * FROM smoke_records ORDER BY timestamp ASC")
    suspend fun getAllRecords(): List<SmokeRecord>

    /** 获取指定时间之后的所有每日统计 */
    @Query("""
        SELECT dateStr, COUNT(*) as count
        FROM smoke_records
        WHERE timestamp >= :startTimestamp
        GROUP BY dateStr
        ORDER BY dateStr ASC
    """)
    suspend fun getDailyStatsSince(startTimestamp: Long): List<DailyStat>

    /** 批量插入记录（用于导入） */
    @Insert
    suspend fun insertAll(records: List<SmokeRecord>): List<Long>

    /** 查找已存在的同一条导入记录 */
    @Query("""
        SELECT * FROM smoke_records
        WHERE timestamp = :timestamp
            AND dateStr = :dateStr
            AND hourOfDay = :hourOfDay
            AND note = :note
        LIMIT 1
    """)
    suspend fun findDuplicate(
        timestamp: Long,
        dateStr: String,
        hourOfDay: Int,
        note: String
    ): SmokeRecord?

    /**
     * 获取从指定时间戳到现在的每日统计（按日期降序）
     * 用于 getBackwardStreak() 优化：一次查询代替循环
     */
    @Query("""
        SELECT dateStr, COUNT(*) as count
        FROM smoke_records
        WHERE timestamp >= :startTimestamp
        GROUP BY dateStr
        ORDER BY dateStr DESC
    """)
    suspend fun getDailyStatsDesc(startTimestamp: Long): List<DailyStat>

    /**
     * 获取指定日期范围内的每日统计（两端都包含）
     * 用于 getWeekReport() 优化：精确查询指定范围
     */
    @Query("""
        SELECT dateStr, COUNT(*) as count
        FROM smoke_records
        WHERE dateStr >= :startDate AND dateStr <= :endDate
        GROUP BY dateStr
        ORDER BY dateStr ASC
    """)
    suspend fun getDailyStatsRange(startDate: String, endDate: String): List<DailyStat>

    /**
     * 获取指定日期、指定小时范围内的记录数
     * 用于自动补录功能：检查某时段（如早上6-11点）实际抽了几根
     */
    @Query("""
        SELECT COUNT(*)
        FROM smoke_records
        WHERE dateStr = :dateStr AND hourOfDay >= :startHour AND hourOfDay < :endHour
    """)
    suspend fun getCountByHourRange(dateStr: String, startHour: Int, endHour: Int): Int
}

/**
 * 每日统计结果
 */
data class DailyStat(
    val dateStr: String,
    val count: Int
)

/**
 * 时段统计结果
 */
data class HourlyStat(
    val hourOfDay: Int,
    val count: Int
)
