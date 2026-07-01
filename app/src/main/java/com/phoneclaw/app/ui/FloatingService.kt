package com.phoneclaw.app.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat

class FloatingService : Service() {
    private lateinit var wm: WindowManager
    private var overlay: FrameLayout? = null
    private var isExpanded = false
    private var ballX = 0
    private var ballY = 0
    private var lastMessages: List<String> = emptyList()

    companion object {
        private const val CHANNEL_ID = "floating"
        private var isRunning = false
        var messages: List<String> = emptyList()

        fun start(ctx: Context) {
            if (isRunning) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(Intent(ctx, FloatingService::class.java))
            else ctx.startService(Intent(ctx, FloatingService::class.java))
        }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, FloatingService::class.java)) }
        fun isActive(): Boolean = isRunning
        fun updateMessages(msgs: List<String>) { messages = msgs }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        startForeground(1004, Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Operit NG").setContentText("悬浮窗运行中")
            .setSmallIcon(android.R.drawable.ic_popup_reminder).build())
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlay == null) showBall()
        return START_STICKY
    }

    private fun showBall() {
        val params = lp(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START, ballX, ballY)

        val frame = FrameLayout(this).apply {
            setBackgroundResource(android.R.drawable.alert_light_frame)
        }

        // Ball icon + close button
        val icon = TextView(this).apply { text = "🤖"; textSize = 28f; gravity = Gravity.CENTER; setPadding(20, 16, 20, 16) }
        frame.addView(icon)
        val close = TextView(this).apply { text = "×"; textSize = 20f; setTextColor(0xFFE9785F.toInt()); gravity = Gravity.CENTER; setPadding(8, 8, 8, 8) }
        frame.addView(close, FrameLayout.LayoutParams(WC, WC, Gravity.END))
        close.setOnClickListener { stopSelf() }

        frame.setOnTouchListener(object : View.OnTouchListener {
            private var ix = 0; private var iy = 0; private var itx = 0f; private var ity = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { ix = params.x; iy = params.y; itx = e.rawX; ity = e.rawY; return true }
                    MotionEvent.ACTION_MOVE -> { params.x = (ix + (e.rawX - itx)).toInt(); params.y = (iy + (e.rawY - ity)).toInt(); wm.updateViewLayout(overlay, params); ballX = params.x; ballY = params.y; return true }
                    MotionEvent.ACTION_UP -> { if (kotlin.math.abs(e.rawX - itx) < 15 && kotlin.math.abs(e.rawY - ity) < 15) toggle(); return true }
                }; return false
            }
        })

        try { wm.addView(frame, params); overlay = frame } catch (_: Exception) { stopSelf() }
    }

    private fun toggle() { if (isExpanded) collapse() else expand() }

    private fun expand() {
        isExpanded = true
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        val params = lp(w, h, Gravity.TOP or Gravity.START, 0, 0)
        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

        // Replace content with chat
        overlay?.removeAllViews()
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 48, 16, 16)
            setBackgroundColor(0xFFF8FAF7.toInt())
        }

        // Title + close
        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        topRow.addView(TextView(this).apply { text = "Operit NG"; textSize = 20f; setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(0xFF17201B.toInt()) })
        topRow.addView(TextView(this).apply { text = "     ×"; textSize = 20f; setTextColor(0xFFE9785F.toInt()); gravity = Gravity.END; layoutParams = LinearLayout.LayoutParams(0, WC, 1f); setOnClickListener { collapse() } })
        content.addView(topRow)

        // Messages
        val msgs = messages.toList().ifEmpty { listOf("🤖 Operit NG 已就绪") }
        msgs.reversed().take(20).forEach { msg ->
            val tv = TextView(this).apply {
                text = msg; textSize = 14f; setTextColor(0xFF17201B.toInt())
                setPadding(8, 8, 8, 8)
                setBackgroundResource(android.R.drawable.editbox_background_normal)
            }
            content.addView(tv)
            content.addView(Space(this).apply { layoutParams = LinearLayout.LayoutParams(0, 8) })
        }

        scroll.addView(content)
        overlay?.addView(scroll)
        wm.updateViewLayout(overlay!!, params)
    }

    private fun collapse() {
        isExpanded = false
        overlay?.removeAllViews()
        showBallContent()
        val params = lp(WC, WC, Gravity.TOP or Gravity.START, ballX, ballY)
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        wm.updateViewLayout(overlay!!, params)
    }

    private fun showBallContent() {
        val icon = TextView(this).apply { text = "🤖"; textSize = 28f; gravity = Gravity.CENTER; setPadding(20, 16, 20, 16) }
        overlay?.addView(icon)
    }

    private fun lp(w: Int, h: Int, g: Int, x: Int, y: Int) = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT)
    else WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_PHONE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)).apply { this.gravity = g; this.x = x; this.y = y }

    private fun createChannel() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID, "悬浮窗", NotificationManager.IMPORTANCE_LOW)) }

    override fun onDestroy() { overlay?.let { wm.removeView(it) }; isRunning = false; super.onDestroy() }
}

private val WC = ViewGroup.LayoutParams.WRAP_CONTENT
