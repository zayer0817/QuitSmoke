package com.quitsmoke.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.quitsmoke.app.MainActivity
import com.quitsmoke.app.R
import com.quitsmoke.app.data.SmokeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 戒烟小组件 Provider
 * 
 * 小组件设计理念：
 * - 极简，只有一个"抽一根"按钮
 * - 显示今日已抽烟次数
 * - 支持小米澎湃系统圆角卡片风格
 * - 点击计数区域可打开主应用查看统计
 */
class SmokeWidgetProvider : AppWidgetProvider() {

    companion object {
        /** 通知小组件更新的Action */
        const val ACTION_UPDATE_WIDGET = "com.quitsmoke.app.ACTION_UPDATE_WIDGET"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 异步获取数据后更新所有小组件
        CoroutineScope(Dispatchers.IO).launch {
            val repo = SmokeRepository.getInstance(context)
            val todayCount = repo.getTodayCount()

            appWidgetIds.forEach { widgetId ->
                val views = buildRemoteViews(context, todayCount)
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_UPDATE_WIDGET -> {
                // 收到更新通知，刷新所有小组件
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, SmokeWidgetProvider::class.java)
                val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
                onUpdate(context, appWidgetManager, widgetIds)
            }
        }
    }

    /**
     * 构建小组件的RemoteViews
     */
    private fun buildRemoteViews(context: Context, todayCount: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_smoke)

        // 更新今日计数
        views.setTextViewText(R.id.tv_today_count, "$todayCount")

        // 根据数量调整提示文字
        @Suppress("UNUSED_VARIABLE")
        val tipText = when {
            todayCount == 0 -> "今天还没有抽烟，坚持住！"
            todayCount <= 5 -> "还行，控制住自己"
            todayCount <= 10 -> "有点多了，注意控制"
            todayCount <= 20 -> "太多了，要克制！"
            else -> "严重超标！请立即停止！"
        }
        views.setTextViewText(R.id.tv_tip, tipText)

        // "抽一根"按钮点击 -> 发送广播记录一次
        val smokeIntent = Intent(context, SmokeActionReceiver::class.java).apply {
            action = SmokeActionReceiver.ACTION_SMOKE
            setPackage(context.packageName)
        }
        val smokePending = PendingIntent.getBroadcast(
            context, 0, smokeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_smoke, smokePending)

        // 计数区域点击 -> 打开主应用查看统计
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.layout_count_area, openPending)

        return views
    }

    /**
     * 通知所有小组件更新
     * 在记录或撤销后调用
     */
    fun notifyWidgetUpdate(context: Context) {
        val intent = Intent(context, SmokeWidgetProvider::class.java).apply {
            action = ACTION_UPDATE_WIDGET
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
}
