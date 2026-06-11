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
import kotlinx.coroutines.withContext

class SmokeActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SMOKE = "com.quitsmoke.app.ACTION_SMOKE"
        const val ACTION_UNDO = "com.quitsmoke.app.ACTION_UNDO"

        private const val RAPID_CLICK_THRESHOLD_MS = 3000L
        private const val INTERVAL_MINUTES = 15

        private var lastClickTime = 0L
        private var rapidClickCount = 0
        private var cachedTodayCount = -1
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

        val offsetMinutes = rapidClickCount * INTERVAL_MINUTES
        val estimatedCount = if (cachedTodayCount >= 0) cachedTodayCount + 1 + rapidClickCount else -1

        showQuickToast(context, estimatedCount, rapidClickCount)

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = SmokeRepository.getInstance(context)
                repo.recordSmoke(offsetMinutes)
                val todayCount = repo.getTodayCount()
                cachedTodayCount = todayCount

                withContext(Dispatchers.Main) {
                    SmokeWidgetProvider.notifyWidgetUpdate(context)
                }
            } finally {
                pendingResult.finish()
            }
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
                val repo = SmokeRepository.getInstance(context)
                val success = repo.undoLastSmoke()
                val todayCount = repo.getTodayCount()

                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(context, context.getString(R.string.toast_undo_success, todayCount), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.toast_undo_empty), Toast.LENGTH_SHORT).show()
                    }
                    SmokeWidgetProvider.notifyWidgetUpdate(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
