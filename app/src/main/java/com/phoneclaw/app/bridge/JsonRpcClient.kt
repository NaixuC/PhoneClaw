package com.phoneclaw.app.bridge

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class JsonRpcClient(
    private val socketName: String,
) {
    private var socket: LocalSocket? = null
    private var reader: BufferedReader? = null
    private var writer: OutputStreamWriter? = null
    private var requestId = 0

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            val s = LocalSocket()
            s.connect(LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT))
            s.soTimeout = 30000
            reader = BufferedReader(InputStreamReader(s.inputStream))
            writer = OutputStreamWriter(s.outputStream)
            socket = s
            Log.i("JsonRpcClient", "connected to $socketName")
            true
        } catch (e: Exception) {
            Log.e("JsonRpcClient", "connect failed: ${e.message}")
            false
        }
    }

    suspend fun call(method: String, params: JsonElement = JsonNull): JsonElement? = withContext(Dispatchers.IO) {
        try {
            requestId++
            val request = buildJsonObject {
                put("jsonrpc", JsonPrimitive("2.0"))
                put("id", JsonPrimitive(requestId))
                put("method", JsonPrimitive(method))
                put("params", params)
            }
            val reqStr = request.toString() + "\n"
            Log.d("RPC->", "$method #$requestId")
            writer?.write(reqStr)
            writer?.flush()
            val line = reader?.readLine() ?: return@withContext null
            val response = Json.parseToJsonElement(line).jsonObject
            val err = response["error"]
            if (err != null && err !is JsonNull) {
                Log.e("RPC-ERR", "$method: ${err}")
                return@withContext null
            }
            response["result"]
        } catch (e: Exception) {
            Log.e("JsonRpcClient", "$method failed: ${e.message}")
            null
        }
    }

    fun disconnect() {
        try { writer?.close() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
    }
}
