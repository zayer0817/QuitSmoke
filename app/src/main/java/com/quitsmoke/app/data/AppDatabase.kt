package com.quitsmoke.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room数据库
 *
 * 数据库版本说明：
 * - Version 1: 初始版本，smoke_records 表
 *
 * 升级规则：每次修改 Entity 结构时，必须：
 * 1. 增加 version 号
 * 2. 编写对应的 Migration
 * 3. 在 databaseBuilder 中添加 .addMigrations()
 */
@Database(
    entities = [SmokeRecord::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun smokeRecordDao(): SmokeRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "quitsmoke_db"
                )
                    .setJournalMode(JournalMode.TRUNCATE)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
