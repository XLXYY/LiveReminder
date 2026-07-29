package com.example.livereminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 启动服务进行检测并显示浮窗
        val serviceIntent = Intent(context, FloatingWindowService::class.java).apply {
            action = FloatingWindowService.ACTION_CHECK_AND_SHOW
        }
        context.startForegroundService(serviceIntent)
    }
}
