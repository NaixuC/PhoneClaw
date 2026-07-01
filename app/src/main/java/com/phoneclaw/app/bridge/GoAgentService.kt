package com.phoneclaw.app.bridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * 保持 Go agentd 进程存活的前台服务
 * 当 App 进入后台时防止系统杀死 Go 引擎进程
 */
class GoAgentService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Operit NG 引擎")
            .setContentText("AI 引擎运行中")
            .setSmallIcon(android.R.drawable.ic_popup_reminder).build())
        Log.i("GoAgentService", "前台服务已启动，保活 Go 引擎")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i("GoAgentService", "前台服务已销毁")
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "AI 引擎", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    companion object {
        private const val CHANNEL_ID = "go_agent"
        private const val NOTIF_ID = 1001
    }
}
