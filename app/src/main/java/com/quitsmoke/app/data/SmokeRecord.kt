package com.quitsmoke.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * 抽烟记录实体
 * 每次点击"抽一根"按钮，就会生成一条记录
 */
@Entity(
    tableName = "smoke_records",
    indices = [Index(value = ["timestamp"])]
)
data class SmokeRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 记录时间戳（毫秒） */
    val timestamp: Long = System.currentTimeMillis(),

    /** 归属日期（格式：yyyy-MM-dd），方便按日查询 */
    val dateStr: String = "",

    /** 记录所在的小时 (0-23)，用于分析时段分布 */
    val hourOfDay: Int = 0,

    /** 备注（可选） */
    val note: String = ""
)
