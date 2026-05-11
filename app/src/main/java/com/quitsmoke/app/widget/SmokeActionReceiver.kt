package com.quitsmoke.app.widget

import android.content.BroadcastReceiver
import android.content.BroadcastReceiver.PendingResult
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.quitsmoke.app.data.SmokeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 小组件操作接收器
 * 处理"抽一根"按钮点击和撤销操作
 */
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

    /**
     * 处理"抽一根"操作
     */
    private fun handleSmoke(context: Context, pendingResult: PendingResult) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = SmokeRepository.getInstance(context)
                repo.recordSmoke()
                val todayCount = repo.getTodayCount()

                withContext(Dispatchers.Main) {
                    // 显示提示
                    val msg = when {
                        todayCount <= 3 -> "已记录，今天第 $todayCount 根"
                        todayCount <= 8 -> "已记录，今天第 $todayCount 根，注意控制"
                        todayCount <= 15 -> "今天第 $todayCount 根了，尽量克制！"
                        else -> "今天已经 $todayCount 根了！为了健康请停下来"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

                    // 通知小组件更新
                    SmokeWidgetProvider.notifyWidgetUpdate(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * 处理撤销操作
     */
    private fun handleUndo(context: Context, pendingResult: PendingResult) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = SmokeRepository.getInstance(context)
                val success = repo.undoLastSmoke()
                val todayCount = repo.getTodayCount()

                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(context, "已撤销，当前 $todayCount 根", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "没有可撤销的记录", Toast.LENGTH_SHORT).show()
                    }
                    SmokeWidgetProvider.notifyWidgetUpdate(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
