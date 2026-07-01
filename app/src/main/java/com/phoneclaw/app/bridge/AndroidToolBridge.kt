package com.phoneclaw.app.bridge

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.phoneclaw.app.ui.FloatingService
import com.phoneclaw.app.ui.ScreenCaptureService
import com.phoneclaw.app.ui.VoiceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidToolBridge(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    suspend fun execute(tool: String, action: String, input: String): ToolResult = withContext(Dispatchers.IO) {
        try {
            Log.i("AndroidBridge", "exec: $tool/$action")
            when (tool) {
                "intent" -> handleIntent(action, input)
                "broadcast" -> handleBroadcast(action, input)
                "bluetooth" -> handleBluetooth(action, input)
                "music" -> handleMusic(action, input)
                "settings" -> handleSettings(action, input)
                "systemops" -> handleSystemOps(action, input)
                "apk" -> handleAPK(action, input)
                "screenshare" -> handleScreenShare(action, input)
                "voice_tts" -> handleTTS(action, input)
                "voice_stt" -> handleSTT(action, input)
                "accessibility" -> handleAccessibility(action, input)
                "ui_automation" -> handleAccessibility(action, input)
                "root" -> handleRoot(action, input)
                "terminal" -> ToolResult(false, "终端工具", "请通过Go引擎的终端管理操作", "medium")
                "ubuntu" -> ToolResult(false, "Ubuntu环境", "请通过Go引擎的Ubuntu管理操作", "medium")
                else -> ToolResult(false, "未知工具", tool, "medium")
            }
        } catch (e: Exception) {
            ToolResult(false, "执行失败", e.message ?: "未知错误", "medium")
        }
    }

    private fun handleIntent(action: String, input: String): ToolResult = when (action) {
        "open" -> { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(input)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); ToolResult(true, "已打开", input, "low") }
        "share" -> { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, input) }, "分享").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); ToolResult(true, "分享面板已打开", "", "low") }
        "dial" -> { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$input")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); ToolResult(true, "拨号面板已打开", input, "low") }
        "send" -> { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$input")).apply { putExtra("sms_body", ""); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); ToolResult(true, "短信面板已打开", input, "low") }
        "settings" -> { context.startActivity(Intent(input).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); ToolResult(true, "已打开设置", input, "low") }
        else -> ToolResult(false, "不支持", action, "low")
    }

    private fun handleBroadcast(action: String, input: String): ToolResult = try {
        context.sendBroadcast(Intent(input).apply { addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES) })
        ToolResult(true, "广播已发送", input, "low")
    } catch (e: Exception) { ToolResult(false, "发送失败", e.message ?: "", "low") }

    private fun handleBluetooth(action: String, input: String): ToolResult {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return ToolResult(false, "设备不支持蓝牙", "", "low")
        return when (action) {
            "status" -> { val s = buildString { appendLine("蓝牙名称: ${adapter.name}"); appendLine("地址: ${adapter.address}"); appendLine("已启用: ${adapter.isEnabled}"); appendLine("扫描中: ${adapter.isDiscovering}"); append("可见: ${adapter.scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE}") }; ToolResult(true, "蓝牙状态", s, "low") }
            "scan" -> { if (!adapter.isEnabled) ToolResult(false, "蓝牙未开启", "", "low") else { adapter.startDiscovery(); ToolResult(true, "正在扫描", "请稍候查看结果", "low") } }
            "enable" -> { adapter.enable(); ToolResult(true, "已开启蓝牙", "", "low") }
            "disable" -> { adapter.disable(); ToolResult(true, "已关闭蓝牙", "", "low") }
            "paired" -> { val d = adapter.bondedDevices?.joinToString("\n") { "${it.name} (${it.address})" } ?: "无已配对设备"; ToolResult(true, "已配对设备", d, "low") }
            else -> ToolResult(false, "不支持", action, "low")
        }
    }

    private fun handleMusic(action: String, input: String): ToolResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return when (action) {
            "play" -> try { mediaPlayer?.release(); val uri = if (input.startsWith("content://") || input.startsWith("file://")) Uri.parse(input) else Uri.parse("file://$input"); mediaPlayer = MediaPlayer().apply { setDataSource(context, uri); setAudioStreamType(AudioManager.STREAM_MUSIC); prepare(); start() }; ToolResult(true, "正在播放", input, "low") } catch (e: Exception) { ToolResult(false, "播放失败", e.message ?: "", "low") }
            "pause" -> { mediaPlayer?.pause(); ToolResult(true, "已暂停", "", "low") }
            "stop" -> { mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer = null; ToolResult(true, "已停止", "", "low") }
            "resume" -> { mediaPlayer?.start(); ToolResult(true, "已恢复播放", "", "low") }
            "volume" -> { val v = input.toIntOrNull() ?: 50; val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC); am.setStreamVolume(AudioManager.STREAM_MUSIC, v * max / 100, 0); ToolResult(true, "音量已设置", "$v%", "low") }
            "status" -> { val s = buildString { appendLine("播放中: ${mediaPlayer?.isPlaying == true}"); appendLine("音量: ${am.getStreamVolume(AudioManager.STREAM_MUSIC)}"); append("最大音量: ${am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}") }; ToolResult(true, "音乐状态", s, "low") }
            else -> ToolResult(false, "不支持", action, "low")
        }
    }

    private fun handleSettings(action: String, input: String): ToolResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply { data = Uri.parse("package:${context.packageName}"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            return ToolResult(false, "需要WRITE_SETTINGS权限", "请在设置中授权", "medium")
        }
        return try { when (action) {
            "brightness" -> { val level = (input.toFloatOrNull() ?: 50f) / 100f * 255f; Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, level.toInt()); ToolResult(true, "亮度已设置", "$input%", "medium") }
            "screen_timeout" -> { val ms = (input.toLongOrNull() ?: 30000); Settings.System.putLong(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, ms); ToolResult(true, "屏幕超时已设置", "${ms/1000}秒", "medium") }
            "get" -> { val v = when (input) { "brightness" -> Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128).toString(); "screen_timeout" -> (Settings.System.getLong(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30000)/1000).toString() + "秒"; "volume" -> { val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager; am.getStreamVolume(AudioManager.STREAM_MUSIC).toString() }; else -> return ToolResult(false, "未知设置", input, "low") }; ToolResult(true, "设置值", "$input = $v", "low") }
            else -> ToolResult(false, "不支持", action, "medium") }
        } catch (e: Exception) { ToolResult(false, "设置失败", e.message ?: "", "medium") }
    }

    private fun handleSystemOps(action: String, input: String): ToolResult = when (action) {
        "processes" -> try { val p = Runtime.getRuntime().exec("ps -A"); val o = p.inputStream.bufferedReader().readText().lines().take(30).joinToString("\n"); ToolResult(true, "进程列表", o, "medium") } catch (e: Exception) { ToolResult(false, "获取失败", e.message ?: "", "medium") }
        "force_stop" -> try { val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager; am.killBackgroundProcesses(input); ToolResult(true, "已停止", input, "high") } catch (e: Exception) { ToolResult(false, "停止失败", e.message ?: "", "high") }
        "clear_cache" -> try { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }; ToolResult(true, "缓存已清理", "", "medium") } catch (e: Exception) { ToolResult(false, "清理失败", e.message ?: "", "medium") }
        "uninstall" -> { context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$input")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); ToolResult(true, "卸载页面已打开", input, "high") }
        "reboot" -> ToolResult(false, "重启需要Root", "请通过Root工具执行", "high")
        else -> ToolResult(false, "不支持", action, "medium")
    }

    private fun handleAPK(action: String, input: String): ToolResult {
        val pm = context.packageManager
        return when (action) {
            "list_installed" -> { val apps = pm.getInstalledPackages(0).map { "${it.packageName} (${it.versionName ?: "?"})" }; ToolResult(true, "已安装应用", "${apps.size}个应用\n${apps.take(100).joinToString("\n")}", "low") }
            "info" -> { try { val pkg = pm.getPackageArchiveInfo(input, 0); val out = buildString { appendLine("包名: ${pkg?.packageName ?: "未知"}"); appendLine("版本: ${pkg?.versionName ?: "未知"}"); append("应用名: ${pkg?.applicationInfo?.loadLabel(pm)}") }; ToolResult(true, "APK信息", out, "medium") } catch (e: Exception) { ToolResult(false, "解析失败", e.message ?: "", "medium") } }
            "install" -> { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) { context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply { data = Uri.parse("package:${context.packageName}"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); ToolResult(false, "需要安装权限", "请在设置中授权", "medium") } else { context.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse("file://$input"), "application/vnd.android.package-archive"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }); ToolResult(true, "安装页面已打开", input, "medium") } }
            "uninstall" -> { context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$input")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); ToolResult(true, "卸载页面已打开", input, "medium") }
            else -> ToolResult(false, "不支持", action, "medium")
        }
    }

    // === Screen capture - real implementation ===
    private fun handleScreenShare(action: String, input: String): ToolResult = when (action) {
        "capture" -> { ScreenCaptureService.start(context); ToolResult(true, "屏幕捕获已启动", "请查看通知栏", "low") }
        "record" -> { ScreenCaptureService.startRecording(context); ToolResult(true, "屏幕录制已启动", "", "medium") }
        "stop_record" -> { ScreenCaptureService.stopRecording(context); ToolResult(true, "录制已停止", "", "low") }
        else -> ToolResult(false, "不支持", action, "low")
    }

    // === TTS/STT - real implementation ===
    private fun handleTTS(action: String, input: String): ToolResult = when (action) {
        "speak" -> { VoiceService.speak(context, input); ToolResult(true, "正在朗读", input.take(50), "low") }
        "stop" -> { VoiceService.stop(context); ToolResult(true, "已停止朗读", "", "low") }
        else -> ToolResult(false, "不支持", action, "low")
    }

    private fun handleSTT(action: String, input: String): ToolResult = when (action) {
        "listen" -> { VoiceService.listen(context); ToolResult(true, "正在聆听", "请说话...", "low") }
        "stop_listen" -> { VoiceService.stopListening(context); val result = VoiceService.getLastResult(); ToolResult(true, "已停止聆听", result, "low") }
        "is_listening" -> ToolResult(true, "聆听状态", if (VoiceService.isListening()) "正在聆听" else "未在聆听", "low")
        else -> ToolResult(false, "不支持", action, "low")
    }

    // === Accessibility - real implementation ===
    private fun handleAccessibility(action: String, input: String): ToolResult = when (action) {
        "click" -> { val ok = OperitAccessibilityService.click(input); ToolResult(ok, if (ok) "已点击" else "点击失败", input, "medium") }
        "swipe" -> { val parts = input.split(":"); if (parts.size == 4) { OperitAccessibilityService.swipe(parts[0].toFloatOrNull() ?: 0f, parts[1].toFloatOrNull() ?: 0f, parts[2].toFloatOrNull() ?: 0f, parts[3].toFloatOrNull() ?: 0f); ToolResult(true, "已滑动", input, "medium") } else ToolResult(false, "参数错误", "需要 x1:y1:x2:y2", "medium") }
        "type" -> { val ok = OperitAccessibilityService.type(input); ToolResult(ok, if (ok) "已输入" else "输入失败", input, "medium") }
        "read_screen" -> { val text = OperitAccessibilityService.readScreen(); ToolResult(true, "屏幕内容", text.take(5000), "low") }
        "back" -> { OperitAccessibilityService.back(); ToolResult(true, "已返回", "", "low") }
        "home" -> { OperitAccessibilityService.home(); ToolResult(true, "已回到桌面", "", "low") }
        else -> ToolResult(false, "不支持", action, "medium")
    }

    // === Root - real implementation ===
    private suspend fun handleRoot(action: String, input: String): ToolResult {
        val bridge = PrivilegedBridge(context)
        return when (action) {
            "exec" -> bridge.execPrivileged(input)
            "check" -> { val hasRoot = bridge.checkRoot(); ToolResult(hasRoot, if (hasRoot) "已获取Root权限" else "无Root权限", "", "high") }
            "reboot" -> bridge.reboot()
            "grant" -> bridge.grantPermission(input.split(" ").firstOrNull() ?: "", input.split(" ").getOrElse(1) { "" })
            else -> ToolResult(false, "不支持", action, "high")
        }
    }

    fun release() { mediaPlayer?.release(); mediaPlayer = null }
}
