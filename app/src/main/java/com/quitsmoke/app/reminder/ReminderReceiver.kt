package com.quitsmoke.app.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.quitsmoke.app.AppPreferences
import com.quitsmoke.app.R
import com.quitsmoke.app.data.SmokeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CHECK_REMINDER = "com.quitsmoke.app.ACTION_CHECK_REMINDER"
        const val EXTRA_PERIOD = "reminder_period"
        const val CHANNEL_ID = "quitsmoke_reminder"
        const val CHANNEL_NAME = "补录提醒"

        private const val REQUEST_CHECK = 2001
        private const val NOTIFICATION_ID_START = 3000

        /**
         * 调度所有时段的检查闹钟
         */
        fun scheduleAllReminders(context: Context) {
            if (!AppPreferences.isReminderEnabled(context)) {
                cancelAllReminders(context)
                return
            }

            val periods = listOf("morning", "noon", "evening", "end_of_day")
            for (period in periods) {
                scheduleReminder(context, period)
            }
        }

        /**
         * 调度单个时段的检查闹钟
         */
        fun scheduleReminder(context: Context, period: String) {
            if (!AppPreferences.isReminderEnabled(context)) return
            if (AppPreferences.isPeriodSkippedToday(context, period)) return

            val (hour, minute) = AppPreferences.getCheckTime(context, period)
            val triggerAt = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }.timeInMillis

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_CHECK_REMINDER
                putExtra(EXTRA_PERIOD, period)
                setPackage(context.packageName)
            }
            val requestCode = REQUEST_CHECK + when (period) {
                "morning" -> 0
                "noon" -> 1
                "evening" -> 2
                "end_of_day" -> 3
                else -> 4
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        }

        /**
         * 在指定延迟（分钟）后重新检查某个时段
         */
        fun scheduleReminderDelayed(context: Context, period: String, delayMinutes: Int) {
            val triggerAt = Calendar.getInstance().apply {
                add(Calendar.MINUTE, delayMinutes)
            }.timeInMillis

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_CHECK_REMINDER
                putExtra(EXTRA_PERIOD, period)
                setPackage(context.packageName)
            }
            val requestCode = REQUEST_CHECK + when (period) {
                "morning" -> 0; "noon" -> 1; "evening" -> 2; "end_of_day" -> 3; else -> 4
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        }

        fun cancelAllReminders(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            for (i in 0..4) {
                val intent = Intent(context, ReminderReceiver::class.java).apply {
                    action = ACTION_CHECK_REMINDER
                    setPackage(context.packageName)
                }
                val pending = PendingIntent.getBroadcast(
                    context, REQUEST_CHECK + i, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pending)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val action = intent.action
        val pendingResult = goAsync()

        if (action == ACTION_CHECK_REMINDER) {
            val period = intent.getStringExtra(EXTRA_PERIOD) ?: return
            handleCheckReminder(appContext, period, pendingResult)
        } else if (action == "com.quitsmoke.app.ACTION_FILL_REMINDER") {
            val period = intent.getStringExtra(EXTRA_PERIOD) ?: return
            val missing = intent.getIntExtra("missing", 0)
            handleFillReminder(appContext, period, missing, pendingResult)
        } else if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_DATE_CHANGED) {
            scheduleAllReminders(appContext)
            pendingResult.finish()
        } else {
            pendingResult.finish()
        }
    }

    private fun handleFillReminder(context: Context, period: String, missing: Int, pendingResult: PendingResult) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        val notificationId = NOTIFICATION_ID_START + when (period) {
            "morning" -> 0; "noon" -> 1; "evening" -> 2; "end_of_day" -> 3; else -> 4
        }
        notificationManager.cancel(notificationId)

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val repo = SmokeRepository.getInstance(context)
            val calendar = Calendar.getInstance()
            val (startHour, endHour) = if (period == "end_of_day") 6 to 23
            else AppPreferences.getPeriodHourRange(period)
            val hourSpan = if (endHour > startHour) endHour - startHour else 1
            val interval = (hourSpan * 60) / (missing + 1)

            for (i in 0 until missing) {
                calendar.set(Calendar.HOUR_OF_DAY, startHour)
                calendar.set(Calendar.MINUTE, (i + 1) * interval)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                repo.recordSmokeAt(calendar.timeInMillis)
            }

            AppPreferences.markPeriodSkippedToday(context, period)

            // Notify widget update
            com.quitsmoke.app.widget.SmokeWidgetProvider.notifyWidgetUpdate(context)

            pendingResult.finish()
        }
    }

    private fun handleCheckReminder(context: Context, period: String, pendingResult: PendingResult) {
        if (!AppPreferences.isReminderEnabled(context)) {
            pendingResult.finish()
            return
        }
        if (AppPreferences.isPeriodSkippedToday(context, period)) {
            pendingResult.finish()
            return
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val repo = SmokeRepository.getInstance(context)
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(java.util.Date())
            val (startHour, endHour) = AppPreferences.getPeriodHourRange(period)
            val actualCount = repo.getCountByHourRange(todayStr, startHour, endHour)
            val expectedCount = if (period == "end_of_day") {
                // 全天兜底：汇总三个时段的预期
                AppPreferences.getExpectedCount(context, "morning") +
                    AppPreferences.getExpectedCount(context, "noon") +
                    AppPreferences.getExpectedCount(context, "evening")
            } else {
                AppPreferences.getExpectedCount(context, period)
            }
            val missing = expectedCount - actualCount

            if (missing > 0) {
                showReminderNotification(context, period, missing, actualCount, expectedCount)
            }
            pendingResult.finish()
        }
    }

    private fun showReminderNotification(
        context: Context, period: String,
        missing: Int, actual: Int, expected: Int
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

        createNotificationChannel(notificationManager)

        val periodLabel = AppPreferences.getPeriodLabel(period)
        val title = if (period == "end_of_day") {
            "今天抽了几根？"
        } else {
            "${periodLabel}抽烟记录提醒"
        }
        val content = if (period == "end_of_day") {
            "今天共记录了 $actual 根，平时约 $expected 根，差 $missing 根要补上吗？"
        } else {
            "${periodLabel}记录了 $actual 根（平时约 $expected 根），差 $missing 根要补上吗？"
        }

        val clickIntent = Intent(context, ReminderActivity::class.java).apply {
            putExtra(EXTRA_PERIOD, period)
            putExtra("missing", missing)
            putExtra("actual", actual)
            putExtra("expected", expected)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val clickPendingIntent = PendingIntent.getActivity(
            context, 0, clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "补上"快捷操作——直接加记录而不打开 Activity
        val fillIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.quitsmoke.app.ACTION_FILL_REMINDER"
            putExtra(EXTRA_PERIOD, period)
            putExtra("missing", missing)
            setPackage(context.packageName)
        }
        val fillPendingIntent = PendingIntent.getBroadcast(
            context, 1, fillIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(clickPendingIntent)
            .addAction(0, "补上 $missing 根", fillPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notificationId = NOTIFICATION_ID_START + when (period) {
            "morning" -> 0
            "noon" -> 1
            "evening" -> 2
            "end_of_day" -> 3
            else -> 4
        }
        notificationManager.notify(notificationId, notification)
    }

    private fun createNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "抽烟补录提醒"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
