package com.phoneclaw.app.bridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.io.File

/**
 * 管理 MediaProjection（屏幕录制）和麦克风的前台服务
 * 对应 Manifest 中声明的 foregroundServiceType="mediaProjection|microphone"
 */
class AndroidToolService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null

    companion object {
        private const val CHANNEL_ID = "android_tools"
        private const val NOTIF_ID = 1005
        var mediaProjectionIntent: Intent? = null
        var resultCode: Int = 0
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Android 工具服务")
            .setContentText("管理屏幕录制和麦克风")
            .setSmallIcon(android.R.drawable.ic_menu_camera).build())
        Log.i("AndroidToolService", "服务已创建")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "INIT_MEDIA_PROJECTION" -> initMediaProjection()
            "RELEASE_MEDIA_PROJECTION" -> releaseMediaProjection()
        }
        return START_STICKY
    }

    private fun initMediaProjection() {
        if (mediaProjection != null) return
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mgr.getMediaProjection(resultCode, mediaProjectionIntent ?: return)
        Log.i("AndroidToolService", "MediaProjection 已初始化")
    }

    private fun releaseMediaProjection() {
        mediaRecorder?.release()
        mediaRecorder = null
        mediaProjection?.stop()
        mediaProjection = null
        Log.i("AndroidToolService", "MediaProjection 已释放")
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL_ID, "Android 工具", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onDestroy() {
        releaseMediaProjection()
        super.onDestroy()
    }
}
