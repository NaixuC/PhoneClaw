package com.phoneclaw.app.bridge

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Operit NG 无障碍服务
 * 提供UI自动化操作能力: 点击、滑动、输入、读取屏幕
 */
class OperitAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val actionQueue = ConcurrentLinkedQueue<AccessibilityAction>()
    private var isProcessing = false

    companion object {
        private const val TAG = "OperitAccessibility"
        var instance: OperitAccessibilityService? = null
            private set

        fun click(text: String): Boolean {
            val service = instance ?: return false
            service.actionQueue.add(AccessibilityAction("click", text))
            service.processQueue()
            return true
        }

        fun clickByCoordinates(x: Float, y: Float): Boolean {
            val service = instance ?: return false
            service.actionQueue.add(AccessibilityAction("click_coord", "$x:$y"))
            service.processQueue()
            return true
        }

        fun type(text: String): Boolean {
            val service = instance ?: return false
            service.actionQueue.add(AccessibilityAction("type", text))
            service.processQueue()
            return true
        }

        fun swipe(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
            val service = instance ?: return false
            service.actionQueue.add(AccessibilityAction("swipe", "$x1:$y1:$x2:$y2"))
            service.processQueue()
            return true
        }

        fun readScreen(): String {
            val service = instance ?: return ""
            val root = service.rootInActiveWindow ?: return ""
            return extractText(root)
        }

        fun back(): Boolean {
            val service = instance ?: return false
            service.performGlobalAction(GLOBAL_ACTION_BACK)
            return true
        }

        fun home(): Boolean {
            val service = instance ?: return false
            service.performGlobalAction(GLOBAL_ACTION_HOME)
            return true
        }

        fun recentApps(): Boolean {
            val service = instance ?: return false
            service.performGlobalAction(GLOBAL_ACTION_RECENTS)
            return true
        }

        private fun extractText(node: AccessibilityNodeInfo): String {
            val sb = StringBuilder()
            if (node.text != null) {
                sb.append(node.text).append(" ")
            }
            if (node.contentDescription != null) {
                sb.append(node.contentDescription).append(" ")
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { sb.append(extractText(it)) }
            }
            return sb.toString().trim()
        }
    }

    data class AccessibilityAction(val type: String, val data: String)

    override fun onServiceConnected() {
        instance = this
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
        setServiceInfo(info)
        Log.i(TAG, "accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Queue processing happens via actionQueue
    }

    override fun onInterrupt() {
        Log.i(TAG, "accessibility service interrupted")
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        super.onDestroy()
    }

    private fun processQueue() {
        if (isProcessing) return
        isProcessing = true
        scope.launch {
            while (actionQueue.isNotEmpty()) {
                val action = actionQueue.poll() ?: break
                executeAction(action)
                delay(200) // pause between actions
            }
            isProcessing = false
        }
    }

    private suspend fun executeAction(action: AccessibilityAction) {
        try {
            when (action.type) {
                "click" -> findAndClick(action.data)
                "click_coord" -> {
                    val parts = action.data.split(":")
                    if (parts.size == 2) {
                        clickScreen(parts[0].toFloat(), parts[1].toFloat())
                    }
                }
                "type" -> typeText(action.data)
                "swipe" -> {
                    val parts = action.data.split(":")
                    if (parts.size == 4) {
                        swipeScreen(parts[0].toFloat(), parts[1].toFloat(),
                            parts[2].toFloat(), parts[3].toFloat())
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "action failed: ${e.message}")
        }
    }

    private fun findAndClick(text: String) {
        val root = rootInActiveWindow ?: return
        val nodes = root.findAccessibilityNodeInfosByText(text)
        if (nodes.isNotEmpty()) {
            nodes[0].parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }
        // Try content description
        findNodeByContentDescription(root, text)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun findNodeByContentDescription(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString()?.contains(text, ignoreCase = true) == true) {
            return node
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let {
                val found = findNodeByContentDescription(it, text)
                if (found != null) return found
            }
        }
        return null
    }

    private fun clickScreen(x: Float, y:Float) {
        val path = android.graphics.Path().apply { moveTo(x, y); lineTo(x + 1, y + 1) }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun swipeScreen(x1: Float, y1: Float, x2: Float, y2: Float) {
        val path = android.graphics.Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 500))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun typeText(text: String) {
        val root = rootInActiveWindow ?: return
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val args = android.os.Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }
        }
    }
}
