package com.example.livereminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.*

object ScheduleManager {
    private const val PREFS_NAME = "schedule_prefs"
    private const val KEY_ENABLED = "schedule_enabled"
    private const val KEY_TIMES = "schedule_times"
    private const val ALARM_REQUEST_CODE_BASE = 1000

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) rescheduleAll(context) else cancelAll(context)
    }

    fun getTimes(context: Context): MutableSet<String> {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_TIMES, mutableSetOf()) ?: mutableSetOf()
    }

    fun addTime(context: Context, hour: Int, minute: Int) {
        val timeStr = String.format("%02d:%02d", hour, minute)
        val times = getTimes(context).toMutableSet()
        if (times.add(timeStr)) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_TIMES, times).apply()
            if (isEnabled(context)) {
                setAlarm(context, hour, minute)
            }
        }
    }

    fun removeTime(context: Context, timeStr: String) {
        val times = getTimes(context).toMutableSet()
        if (times.remove(timeStr)) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_TIMES, times).apply()
            // 取消对应闹钟
            val parts = timeStr.split(":")
            if (parts.size == 2) {
                val hour = parts[0].toIntOrNull() ?: return
                val minute = parts[1].toIntOrNull() ?: return
                cancelAlarm(context, hour, minute)
            }
        }
    }

    fun rescheduleAll(context: Context) {
        cancelAll(context)
        val times = getTimes(context)
        for (time in times) {
            val parts = time.split(":")
            if (parts.size == 2) {
                val h = parts[0].toIntOrNull() ?: continue
                val m = parts[1].toIntOrNull() ?: continue
                setAlarm(context, h, m)
            }
        }
    }

    private fun setAlarm(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val requestCode = ALARM_REQUEST_CODE_BASE + hour * 60 + minute
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)  // 如果今天时间已过，安排到明天
            }
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }

    private fun cancelAlarm(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val requestCode = ALARM_REQUEST_CODE_BASE + hour * 60 + minute
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun cancelAll(context: Context) {
        val times = getTimes(context)
        for (time in times) {
            val parts = time.split(":")
            if (parts.size == 2) {
                val h = parts[0].toIntOrNull() ?: continue
                val m = parts[1].toIntOrNull() ?: continue
                cancelAlarm(context, h, m)
            }
        }
    }
}
