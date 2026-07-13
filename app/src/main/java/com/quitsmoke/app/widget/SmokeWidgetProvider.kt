package com.quitsmoke.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.quitsmoke.app.AppPreferences
import com.quitsmoke.app.MainActivity
import com.quitsmoke.app.R
import com.quitsmoke.app.data.SmokeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

class SmokeWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.quitsmoke.app.ACTION_UPDATE_WIDGET"
        const val ACTION_MIDNIGHT_UPDATE = "com.quitsmoke.app.ACTION_MIDNIGHT_UPDATE"
        private const val REQUEST_MIDNIGHT_UPDATE = 1001

        fun notifyWidgetUpdate(context: Context) {
            val intent = Intent(context, SmokeWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleNextMidnightUpdate(context.applicationContext)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelMidnightUpdate(context.applicationContext)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val appContext = context.applicationContext
        scheduleNextMidnightUpdate(appContext)

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val repo = SmokeRepository.getInstance(appContext)
            val todayCount = repo.getTodayCount()
            val dailyTarget = AppPreferences.getDailyTarget(appContext)

            appWidgetIds.forEach { widgetId ->
                val views = buildRemoteViews(appContext, todayCount, dailyTarget)
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appContext = context.applicationContext

        when (intent.action) {
            ACTION_UPDATE_WIDGET,
            ACTION_MIDNIGHT_UPDATE,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                val appWidgetManager = AppWidgetManager.getInstance(appContext)
                val componentName = ComponentName(appContext, SmokeWidgetProvider::class.java)
                val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
                onUpdate(appContext, appWidgetManager, widgetIds)
            }
        }
    }

    private fun scheduleNextMidnightUpdate(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = midnightUpdatePendingIntent(context)
        val triggerAtMillis = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancelMidnightUpdate(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(midnightUpdatePendingIntent(context))
    }

    private fun midnightUpdatePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, SmokeWidgetProvider::class.java).apply {
            action = ACTION_MIDNIGHT_UPDATE
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_MIDNIGHT_UPDATE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private suspend fun buildRemoteViews(context: Context, todayCount: Int, dailyTarget: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_smoke)

        views.setTextViewText(R.id.tv_today_count, "$todayCount")

        // 跟随软件内主题色（从 SharedPreferences 缓存读取，与 DataStore 同步）
        val themeColor = AppPreferences.getCachedThemeColor(context)
        val colorInt = android.graphics.Color.parseColor(themeColor)

        // Create rounded bitmap matching button size (ImageView scales via fitXY)
        val density = context.resources.displayMetrics.density
        val radius = 18f * density
        val btnWidth = (90 * density).toInt()
        val btnHeight = (64 * density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(btnWidth, btnHeight, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = colorInt
        val rect = android.graphics.RectF(0f, 0f, btnWidth.toFloat(), btnHeight.toFloat())
        canvas.drawRoundRect(rect, radius, radius, paint)

        views.setImageViewBitmap(R.id.btn_smoke_bg, bitmap)

        val tipText = when {
            todayCount == 0 -> context.getString(R.string.widget_tip_no_smoke)
            todayCount <= dailyTarget -> context.getString(R.string.widget_tip_on_target, (dailyTarget - todayCount).coerceAtLeast(0))
            todayCount <= 5 -> context.getString(R.string.widget_tip_mild)
            todayCount <= 10 -> context.getString(R.string.widget_tip_moderate)
            todayCount <= 20 -> context.getString(R.string.widget_tip_heavy)
            else -> context.getString(R.string.widget_tip_severe)
        }
        views.setTextViewText(R.id.tv_tip, tipText)

        val smokeIntent = Intent(context, SmokeActionReceiver::class.java).apply {
            action = SmokeActionReceiver.ACTION_SMOKE
            setPackage(context.packageName)
        }
        val smokePending = PendingIntent.getBroadcast(
            context, 0, smokeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_smoke, smokePending)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, openPending)

        return views
    }
}
