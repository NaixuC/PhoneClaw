package com.phoneclaw.app.bridge

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 本地模型推理桥接
 * 通过 JNI 调用 llama.cpp / MNN 进行本地推理
 */
class LocalModelBridge {
    companion object {
        private const val TAG = "LocalModelBridge"
        private var nativeLoaded = false
        private var loadedModel: String? = null
        private var loadedType: String? = null // "llama" or "mnn"

        /**
         * 加载 llama.cpp 模型
         */
        fun loadLlama(modelPath: String): Boolean {
            try {
                if (!nativeLoaded) {
                    System.loadLibrary("llama")
                    nativeLoaded = true
                }
                loadedModel = modelPath
                loadedType = "llama"
                nativeInit(modelPath)
                Log.i(TAG, "llama model loaded: $modelPath")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "load llama failed: ${e.message}")
                return false
            }
        }

        /**
         * 加载 MNN 模型
         */
        fun loadMNN(modelPath: String): Boolean {
            try {
                if (!nativeLoaded) {
                    System.loadLibrary("mnn")
                    nativeLoaded = true
                }
                loadedModel = modelPath
                loadedType = "mnn"
                mnnInit(modelPath)
                Log.i(TAG, "MNN model loaded: $modelPath")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "load MNN failed: ${e.message}")
                return false
            }
        }

        /**
         * 推理生成文本
         */
        fun generate(prompt: String, maxTokens: Int = 512): String {
            if (loadedModel == null) return "错误: 未加载模型"
            return try {
                when (loadedType) {
                    "llama" -> nativeGenerate(prompt, maxTokens)
                    "mnn" -> mnnGenerate(prompt, maxTokens)
                    else -> "错误: 未知模型类型"
                }
            } catch (e: Exception) {
                Log.e(TAG, "generate error: ${e.message}")
                "推理失败: ${e.message}"
            }
        }

        fun unload() {
            try {
                when (loadedType) {
                    "llama" -> nativeUnload()
                    "mnn" -> mnnUnload()
                }
            } catch (_: Exception) {}
            loadedModel = null
            loadedType = null
            System.gc()
        }

        fun isLoaded(): Boolean = loadedModel != null

        // llama.cpp JNI
        private external fun nativeInit(modelPath: String)
        private external fun nativeGenerate(prompt: String, maxTokens: Int): String
        private external fun nativeUnload()

        // MNN JNI
        private external fun mnnInit(modelPath: String)
        private external fun mnnGenerate(prompt: String, maxTokens: Int): String
        private external fun mnnUnload()
    }

    suspend fun generateAsync(prompt: String, maxTokens: Int = 512): String = withContext(Dispatchers.IO) {
        generate(prompt, maxTokens)
    }
}
