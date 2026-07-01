package com.phoneclaw.app.bridge

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File

class GoAgentBridge(private val context: Context) {
    private var process: Process? = null
    private val rpc: JsonRpcClient
    private val socketName = "operit_agentd"

    init {
        rpc = JsonRpcClient(socketName)
    }

    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        try {
            val bin = ensureAgentBinary()
            val dataDir = File(context.filesDir, "agentd").absolutePath

            val pb = ProcessBuilder(bin.absolutePath, socketName, dataDir)
            pb.directory(context.filesDir)
            pb.environment()["HOME"] = context.filesDir.absolutePath
            pb.environment()["TMPDIR"] = context.cacheDir.absolutePath

            process = pb.start()
            Log.i("GoAgentBridge", "agentd started")
            delay(300)

            val connected = rpc.connect()
            if (!connected) {
                Log.e("GoAgentBridge", "connection failed")
                process?.destroy()
            }
            connected
        } catch (e: Exception) {
            Log.e("GoAgentBridge", "start error: ${e.message}")
            false
        }
    }

    suspend fun plan(goal: String, mode: String = "balanced", allowedTools: List<String> = emptyList()): AgentResponse? {
        val params = buildJsonObject {
            put("goal", JsonPrimitive(goal))
            put("mode", JsonPrimitive(mode))
            put("allowedTools", JsonArray(allowedTools.map { JsonPrimitive(it) }))
        }
        val result = rpc.call("agent.plan", params) ?: return null
        return deserialize<AgentResponse>(result.toString())
    }

    suspend fun execute(goal: String, mode: String = "balanced", allowedTools: List<String> = emptyList()): AgentResponse? {
        val params = buildJsonObject {
            put("goal", JsonPrimitive(goal))
            put("mode", JsonPrimitive(mode))
            put("allowedTools", JsonArray(allowedTools.map { JsonPrimitive(it) }))
        }
        val result = rpc.call("agent.execute", params) ?: return null
        return deserialize<AgentResponse>(result.toString())
    }

    suspend fun runTool(tool: String, action: String, input: String, allowedTools: List<String>): ToolResult? {
        val params = buildJsonObject {
            put("tool", JsonPrimitive(tool))
            put("action", JsonPrimitive(action))
            put("input", JsonPrimitive(input))
            put("allowedTools", JsonArray(allowedTools.map { JsonPrimitive(it) }))
        }
        val result = rpc.call("tools.run", params) ?: return null
        return deserialize<ToolResult>(result.toString())
    }

    suspend fun chatComplete(model: String, messages: List<ChatMsg>): ChatResponse? {
        val params = buildJsonObject {
            put("model", JsonPrimitive(model))
            put("messages", JsonArray(messages.map { buildJsonObject { put("role", JsonPrimitive(it.role)); put("content", JsonPrimitive(it.content)) } }))
            put("temperature", JsonPrimitive(0.7))
            put("maxTokens", JsonPrimitive(4096))
        }
        val result = rpc.call("chat.complete", params) ?: return null
        return deserialize<ChatResponse>(result.toString())
    }

    suspend fun listTools(): List<ToolDescriptor> {
        val result = rpc.call("tools.list") ?: return emptyList()
        val arr = result.jsonObject["tools"]?.jsonArray ?: return emptyList()
        return deserialize<List<ToolDescriptor>>(arr.toString()) ?: emptyList()
    }

    suspend fun listModels(): List<Map<String, String>> {
        val result = rpc.call("chat.models") ?: return emptyList()
        val arr = result.jsonObject["models"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val obj = el.jsonObject
            mapOf("id" to (obj["id"]?.jsonPrimitive?.content ?: ""), "provider" to (obj["provider"]?.jsonPrimitive?.content ?: ""))
        }
    }

    suspend fun memorySearch(query: String): List<MemoryItem> {
        val params = buildJsonObject { put("query", JsonPrimitive(query)) }
        val result = rpc.call("memory.search", params) ?: return emptyList()
        val arr = result.jsonObject["items"]?.jsonArray ?: return emptyList()
        return deserialize<List<MemoryItem>>(arr.toString()) ?: emptyList()
    }

    suspend fun memorySave(item: MemoryItem): Boolean {
        val json = Json.encodeToString(MemoryItem.serializer(), item)
        return rpc.call("memory.save", Json.parseToJsonElement(json)) != null
    }

    suspend fun memoryDelete(id: String): Boolean {
        return rpc.call("memory.delete", buildJsonObject { put("id", JsonPrimitive(id)) }) != null
    }

    suspend fun systemInfo(): Map<String, String> {
        val result = rpc.call("system.info") ?: return emptyMap()
        return result.jsonObject.mapValues { it.value.jsonPrimitive.content }
    }

    private fun ensureAgentBinary(): File {
        val abi = Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" }
            ?: error("仅支持 arm64-v8a 架构")
        val dir = File(context.filesDir, "agent/$abi").apply { mkdirs() }
        val target = File(dir, "agentd")
        if (!target.exists() || target.length() == 0L) {
            try {
                context.assets.open("bin/$abi/agentd").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                target.setExecutable(true, true)
                Log.i("GoAgentBridge", "extracted agentd")
            } catch (e: Exception) {
                Log.e("GoAgentBridge", "extract failed: ${e.message}")
                throw e
            }
        }
        return target
    }

    fun stop() {
        rpc.disconnect()
        process?.destroy()
        try { process?.waitFor() } catch (_: Exception) {}
    }
}

private inline fun <reified T> deserialize(json: String): T? {
    return try {
        Json { ignoreUnknownKeys = true }.decodeFromString<T>(json)
    } catch (e: Exception) {
        Log.e("GoAgentBridge", "parse error: ${e.message}")
        null
    }
}
