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
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = SmokeRepository.getInstance(context)
                repo.recordSmoke()
                val todayCount = repo.getTodayCount()

                withContext(Dispatchers.Main) {
                    val msg = when {
                        todayCount <= 3 -> context.getString(R.string.toast_recorded_low, todayCount)
                        todayCount <= 8 -> context.getString(R.string.toast_recorded_mid, todayCount)
                        todayCount <= 15 -> context.getString(R.string.toast_recorded_high, todayCount)
                        else -> context.getString(R.string.toast_recorded_severe, todayCount)
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

                    SmokeWidgetProvider.notifyWidgetUpdate(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
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
