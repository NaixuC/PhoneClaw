package com.phoneclaw.app.bridge

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Root/Shizuku 特权操作桥接
 * 支持: su, Shizuku, ADB shell
 */
class PrivilegedBridge(private val context: Context) {

    companion object {
        private const val TAG = "PrivilegedBridge"

        fun suExec(command: String): Pair<Boolean, String> {
            return try {
                val proc = Runtime.getRuntime().exec("su -c $command")
                val stdout = proc.inputStream.bufferedReader().readText()
                val stderr = proc.errorStream.bufferedReader().readText()
                val exitCode = proc.waitFor()
                val output = if (stdout.isNotBlank()) stdout else stderr
                Pair(exitCode == 0, output)
            } catch (e: Exception) {
                Pair(false, "Root执行失败: ${e.message}")
            }
        }

        fun shExec(command: String): Pair<Boolean, String> {
            return try {
                val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                val stdout = proc.inputStream.bufferedReader().readText()
                val stderr = proc.errorStream.bufferedReader().readText()
                val exitCode = proc.waitFor()
                val output = if (stdout.isNotBlank()) stdout else stderr
                Pair(exitCode == 0, output)
            } catch (e: Exception) {
                Pair(false, "Shell执行失败: ${e.message}")
            }
        }
    }

    private var shizukuAvailable = false

    suspend fun checkRoot(): Boolean = withContext(Dispatchers.IO) {
        try {
            val proc = Runtime.getRuntime().exec("su -c id")
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            val hasRoot = output.contains("uid=0")
            Log.i(TAG, "root check: $hasRoot")
            hasRoot
        } catch (e: Exception) {
            false
        }
    }

    suspend fun execPrivileged(command: String): ToolResult = withContext(Dispatchers.IO) {
        // Try su first
        val (success, output) = suExec(command)
        if (success) {
            ToolResult(true, "特权执行成功", output, "high")
        } else {
            // Try sh fallback
            val (_, shOutput) = shExec(command)
            ToolResult(false, "特权执行失败", shOutput, "high")
        }
    }

    suspend fun mountRW(): ToolResult = execPrivileged("mount -o rw,remount /system")
    suspend fun listSystemPackages(): ToolResult = execPrivileged("pm list packages -s")
    suspend fun forceStopPackage(pkg: String): ToolResult = execPrivileged("am force-stop $pkg")
    suspend fun clearAppData(pkg: String): ToolResult = execPrivileged("pm clear $pkg")
    suspend fun reboot(): ToolResult = execPrivileged("reboot")
    suspend fun shutdown(): ToolResult = execPrivileged("reboot -p")

    suspend fun grantPermission(pkg: String, permission: String): ToolResult {
        return execPrivileged("pm grant $pkg $permission")
    }

    suspend fun installPackage(path: String): ToolResult {
        return execPrivileged("pm install -r $path")
    }

    suspend fun uninstallPackage(pkg: String): ToolResult {
        return execPrivileged("pm uninstall $pkg")
    }

    suspend fun getBatteryLevel(): ToolResult {
        return try {
            val (success, output) = shExec("dumpsys battery | grep level")
            if (success) ToolResult(true, "电池电量", output.trim(), "low")
            else ToolResult(false, "获取失败", output, "low")
        } catch (e: Exception) {
            ToolResult(false, "获取失败", e.message ?: "", "low")
        }
    }

    suspend fun getNetworkInfo(): ToolResult {
        return try {
            val (success, output) = shExec("dumpsys wifi | grep SSID")
            if (success) ToolResult(true, "网络信息", output.take(500), "low")
            else ToolResult(false, "获取失败", output, "low")
        } catch (e: Exception) {
            ToolResult(false, "获取失败", e.message ?: "", "low")
        }
    }
}
