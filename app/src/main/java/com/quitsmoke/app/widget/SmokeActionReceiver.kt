package com.quitsmoke.app.widget

import android.content.BroadcastReceiver
import android.content.BroadcastReceiver.PendingResult
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.quitsmoke.app.R
import com.quitsmoke.app.data.SmokeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar

class SmokeActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SMOKE = "com.quitsmoke.app.ACTION_SMOKE"
        const val ACTION_UNDO = "com.quitsmoke.app.ACTION_UNDO"

        private const val RAPID_CLICK_THRESHOLD_MS = 3000L
        private const val INTERVAL_MINUTES = 15

        private var lastClickTime = 0L
        private var rapidClickCount = 0
        private var cachedTodayCount = -1

        /** 串行化数据库写入，防止并发导致卡死 */
        private val smokeMutex = Mutex()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        when (intent.action) {
            ACTION_SMOKE -> handleSmoke(appContext, pendingResult)
            ACTION_UNDO -> handleUndo(appContext, pendingResult)
            else -> pendingResult.finish()
        }
    }

    private fun handleSmoke(context: Context, pendingResult: PendingResult) {
        val now = System.currentTimeMillis()
        val isRapidClick = (now - lastClickTime) < RAPID_CLICK_THRESHOLD_MS
        lastClickTime = now

        if (isRapidClick) {
            rapidClickCount++
        } else {
            rapidClickCount = 0
        }

        // 计算偏移量，但不能跨到第二天
        val offsetMinutes = clampOffsetToToday(now, rapidClickCount * INTERVAL_MINUTES)
        val estimatedCount = if (cachedTodayCount >= 0) cachedTodayCount + 1 + rapidClickCount else -1

        showQuickToast(context, estimatedCount, rapidClickCount)

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // 用 Mutex 串行化，防止并发 DB 写入导致卡死
                smokeMutex.withLock {
                    val repo = SmokeRepository.getInstance(context)
                    repo.recordSmoke(offsetMinutes)
                    val todayCount = repo.getTodayCount()
                    cachedTodayCount = todayCount
                }
                // 锁外刷新小组件（sendBroadcast 线程安全，不需要切 Main 线程）
                SmokeWidgetProvider.notifyWidgetUpdate(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * 如果偏移后的时间会跨到第二天，就把偏移量截断到今天 23:59。
     * 防止接近零点时快速点击导致记录跑到第二天。
     */
    private fun clampOffsetToToday(now: Long, offsetMinutes: Int): Int {
        if (offsetMinutes <= 0) return 0
        val endOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val projectedTime = now + offsetMinutes * 60_000L
        return if (projectedTime > endOfToday) {
            ((endOfToday - now) / 60_000L).toInt().coerceIn(0, offsetMinutes)
        } else {
            offsetMinutes
        }
    }

    private fun showQuickToast(context: Context, estimatedCount: Int, rapidClickCount: Int) {
        val msg = if (rapidClickCount > 0) {
            context.getString(R.string.toast_rapid_record, rapidClickCount + 1, INTERVAL_MINUTES)
        } else if (estimatedCount > 0) {
            when {
                estimatedCount <= 3 -> context.getString(R.string.toast_recorded_low, estimatedCount)
                estimatedCount <= 8 -> context.getString(R.string.toast_recorded_mid, estimatedCount)
                estimatedCount <= 15 -> context.getString(R.string.toast_recorded_high, estimatedCount)
                else -> context.getString(R.string.toast_recorded_severe, estimatedCount)
            }
        } else {
            context.getString(R.string.toast_recording)
        }
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    private fun handleUndo(context: Context, pendingResult: PendingResult) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                smokeMutex.withLock {
                    val repo = SmokeRepository.getInstance(context)
                    val success = repo.undoLastSmoke()
                    val todayCount = repo.getTodayCount()
                    cachedTodayCount = todayCount

                    if (success) {
                        Toast.makeText(context, context.getString(R.string.toast_undo_success, todayCount), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.toast_undo_empty), Toast.LENGTH_SHORT).show()
                    }
                }
                SmokeWidgetProvider.notifyWidgetUpdate(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
