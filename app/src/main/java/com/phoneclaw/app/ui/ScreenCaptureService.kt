package com.phoneclaw.app.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var isRecording = false
    private var recordStartTime: Long = 0
    private var mediaRecorder: android.media.MediaRecorder? = null

    companion object {
        private const val TAG = "ScreenCapture"
        private const val CHANNEL_ID = "screen_capture"
        private const val NOTIF_ID = 1002

        var mediaProjectionIntent: Intent? = null
        var resultCode: Int = 0
        var lastBitmap: Bitmap? = null
            private set

        fun start(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = "START_CAPTURE"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun capture(context: Context): Bitmap? {
            // Blocking capture - wait for result
            val latch = java.util.concurrent.CountDownLatch(1)
            var result: Bitmap? = null
            handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler?.post {
                captureNow()
                result = lastBitmap
                latch.countDown()
            }
            try { latch.await(5, java.util.concurrent.TimeUnit.SECONDS) } catch (_: Exception) {}
            return result
        }

        private var handler: android.os.Handler? = null

        fun startRecording(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                action = "START_RECORD"
            }
            context.startService(intent)
        }

        fun stopRecording(context: Context) {
            context.startService(Intent(context, ScreenCaptureService::class.java).apply {
                action = "STOP_RECORD"
            })
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notif = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("屏幕捕获")
            .setContentText("正在等待截图...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        startForeground(NOTIF_ID, notif)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_CAPTURE" -> initCapture()
            "CAPTURE_NOW" -> captureNow()
            "START_RECORD" -> startRecord()
            "STOP_RECORD" -> stopRecord()
        }
        return START_STICKY
    }

    private fun initCapture() {
        if (mediaProjection != null) return
        val intent = mediaProjectionIntent ?: run {
            Log.e(TAG, "no media projection intent")
            return
        }
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mgr.getMediaProjection(resultCode, intent)
        Log.i(TAG, "MediaProjection initialized")
    }

    private fun ensureReader(): ImageReader? {
        if (imageReader != null) return imageReader
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val dpi = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
        return imageReader
    }

    private fun captureNow() {
        val reader = ensureReader() ?: return
        Thread.sleep(200)
        val image = reader.acquireLatestImage() ?: return
        val w = image.width; val h = image.height
        val planes = image.planes
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val buffer = planes[0].buffer
        val pixels = IntArray(w * h)
        val rowBytes = (w * pixelStride).coerceAtMost(rowStride)
        val tmp = ByteArray(rowBytes)
        var idx = 0
        for (row in 0 until h) {
            buffer.position(row * rowStride)
            buffer.get(tmp, 0, rowBytes)
            for (col in 0 until w) {
                val px = col * pixelStride
                val r = tmp[px].toInt() and 0xFF
                val g = tmp[px + 1].toInt() and 0xFF
                val b = tmp[px + 2].toInt() and 0xFF
                val a = if (pixelStride == 4) (tmp[px + 3].toInt() and 0xFF) else 0xFF
                pixels[idx++] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        image.close()
        val bitmap = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        lastBitmap = bitmap

        // Save to cache
        try {
            val dir = File(cacheDir, "screenshots").apply { mkdirs() }
            val file = File(dir, "screenshot_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Log.i(TAG, "screenshot saved: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "save screenshot failed: ${e.message}")
        }

        updateNotification(getString(android.R.string.ok))
    }

    private fun startRecord() {
        if (isRecording || mediaProjection == null) return
        try {
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val videoDir = File(cacheDir, "recordings").apply { mkdirs() }
            val videoFile = File(videoDir, "recording_${System.currentTimeMillis()}.mp4")

            mediaRecorder = android.media.MediaRecorder().apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setVideoSource(android.media.MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(android.media.MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                setVideoSize(width, height)
                setVideoFrameRate(30)
                setVideoBitRate(4000000)
                setAudioBitRate(128000)
                setOutputFile(videoFile.absolutePath)
                prepare()
            }

            val recordDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenRecord",
                width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder?.surface, null, null
            )

            mediaRecorder?.start()
            isRecording = true
            recordStartTime = System.currentTimeMillis()
            recordDisplay?.let { recordDisplays.add(it) }
            updateNotification("正在录制...")
            Log.i(TAG, "recording started: ${videoFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "recording start failed: ${e.message}")
            mediaRecorder?.release()
            mediaRecorder = null
        }
    }

    private val recordDisplays = mutableListOf<VirtualDisplay>()

    private fun stopRecord() {
        if (!isRecording) return
        try {
            mediaRecorder?.apply { stop(); release() }
            mediaRecorder = null
            recordDisplays.forEach { it.release() }
            recordDisplays.clear()
            isRecording = false
            val duration = System.currentTimeMillis() - recordStartTime
            updateNotification("录制完成 (${duration / 1000}s)")
            Log.i(TAG, "recording stopped: ${duration}ms")
        } catch (e: Exception) {
            Log.e(TAG, "recording stop error: ${e.message}")
        }
    }

    private fun updateNotification(text: String) {
        val notif = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("屏幕捕获")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIF_ID, notif)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "屏幕捕获", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        super.onDestroy()
    }
}
