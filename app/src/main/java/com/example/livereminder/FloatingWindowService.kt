package com.example.livereminder

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.livereminder.db.AppDatabase
import com.example.livereminder.db.FollowedRoomEntity
import kotlinx.coroutines.*

class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatView: View? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        const val ACTION_CHECK_AND_SHOW = "CHECK_AND_SHOW"
        const val ACTION_HIDE = "HIDE"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "live_float"
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CHECK_AND_SHOW -> {
                startForeground(NOTIFICATION_ID, buildNotification("检测中..."))
                checkAndShow()
            }
            ACTION_HIDE -> removeFloatView()
        }
        return START_STICKY
    }

    private fun checkAndShow() {
        scope.launch {
            val dao = AppDatabase.getInstance(this@FloatingWindowService).followedRoomDao()
            val rooms = withContext(Dispatchers.IO) { dao.getAllRooms() }
            val liveRooms = mutableListOf<FollowedRoomEntity>()
            // 暂存当前列表，用于检测新开播
            val currentEntities = rooms.toList()

            for (room in currentEntities) {
                val info = LiveChecker.getRoomInfo(room.roomId)
                if (info != null) {
                    // 更新数据库状态
                    withContext(Dispatchers.IO) {
                        dao.updateLiveStatus(room.roomId, info.liveStatus)
                    }
                    // 判断是否新开播（上次未直播，这次直播）
                    val wasOffline = room.liveStatus == 0
                    val isNowLive = info.liveStatus == 1
                    if (wasOffline && isNowLive) {
                        liveRooms.add(room.copy(liveStatus = 1))
                    } else if (isNowLive) {
                        // 已经在直播但未显示过浮窗？我们可以选择每次都把所有直播展示
                        liveRooms.add(room.copy(liveStatus = 1))
                    }
                }
            }

            if (liveRooms.isNotEmpty()) {
                showFloatWindow(liveRooms)
            } else {
                removeFloatView()
                Toast.makeText(this@FloatingWindowService, "没有主播正在直播", Toast.LENGTH_SHORT).show()
            }
            updateNotification(liveRooms)
        }
    }

    private fun showFloatWindow(liveRooms: List<FollowedRoomEntity>) {
        removeFloatView()
        val inflater = LayoutInflater.from(this)
        floatView = inflater.inflate(R.layout.floating_window_layout, null)

        val namesText = floatView?.findViewById<TextView>(R.id.tv_live_names)
        namesText?.text = liveRooms.joinToString("\n") { it.uname }

        floatView?.findViewById<View>(R.id.btn_close)?.setOnClickListener {
            removeFloatView()
            stopSelf()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER or Gravity.TOP
            y = 150
        }

        try {
            windowManager.addView(floatView, params)
        } catch (e: SecurityException) {
            Toast.makeText(this, "悬浮窗权限未授予，请前往设置开启", Toast.LENGTH_LONG).show()
        }
    }

    private fun removeFloatView() {
        floatView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            floatView = null
        }
    }

    private fun updateNotification(liveRooms: List<FollowedRoomEntity>) {
        val text = if (liveRooms.isNotEmpty())
            liveRooms.joinToString(", ") { it.uname } + " 正在直播"
        else
            "当前没有主播直播"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("直播提醒")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "直播提醒服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        removeFloatView()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
