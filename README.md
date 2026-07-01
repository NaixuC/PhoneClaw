<div align="center">
  <span>中文</span> | <a href="README.en.md">English</a>
</div>

<div align="center">
  <img src="https://img.shields.io/badge/Platform-Android_8.0%2B-brightgreen.svg" alt="Platform">
  <img src="https://img.shields.io/badge/Go-1.22+-blue.svg" alt="Go">
  <br>
</div>

<div align="center">
  <h1>PhoneClaw</h1>
  <p>📱 Android AI 智能助手 — 本地推理 + 工具调用</p>
  <p><b>本项目持续更新持续修改</b></p>
</div>

---

## 项目简介

PhoneClaw 是一个 Android 设备上的 AI 引擎。不只是聊天界面，而是集成了 LLM 对话、系统工具调用、工作流自动化、本地模型推理的完整平台。使用 Kotlin + Jetpack Compose 构建界面，Go 语言编写后端引擎，通过 Unix Socket 通信。

---

## 核心架构

```
+-------------------------------------------+
|            PhoneClaw System               |
+-------------------------------------------+
|   Go Engine (agentd)                      |
|  +----------+ +----------+ +------------+ |
|  |LLM Router| | Tool Reg | |Agent/Memory| |
|  | 多供应商   | | 22 种工具 | | 规划/工作流 | |
|  +----------+ +----------+ +------------+ |
|              | JSON-RPC                    |
+--------------|----------------------------+
               v Unix Socket
+-------------------------------------------+
|   Android App (Kotlin)                    |
|  +----------+ +----------+ +------------+ |
|  | UI 层    | | Bridge   | |AndroidTool | |
|  | Compose  | | IPC 通信  | | 蓝牙/音乐… | |
|  +----------+ +----------+ +------------+ |
+-------------------------------------------+
```

---

## 核心亮点

<table>
<tr>
<td width="50%">

### AI 对话
支持 OpenAI、Claude、Gemini、Deepseek、Qwen、Mistral、Ollama 等多种供应商。支持本地模型离线推理。

### 智能记忆
支持记忆的保存、搜索和管理，TF-IDF 向量排序。

</td>
<td width="50%">

### 丰富工具生态
文件管理、Shell 执行、网页抓取、媒体处理、蓝牙管理、音乐播放、系统设置、APK 管理、屏幕录制、语音合成与识别、无障碍服务等。

### 工作流与自动化
多步骤自动化任务，MCP 协议扩展。

</td>
</tr>
</table>

---

## 功能一览

### AI 对话

| 功能 | 说明 |
|------|------|
| 多供应商 | OpenAI、Claude、Gemini、Deepseek、Qwen、Mistral、Ollama 等 |
| 本地推理 | llama.cpp / MNN 引擎接口已接入，需 NDK 编译 C++ 库 |
| 对话历史 | 上下文保持 |
| 自定义端点 | 支持 OpenAI 兼容接口的自定义 API 地址 |

### 工具系统

| 工具 | 操作 | 实际状态 |
|------|------|---------|
| 文件系统 | 浏览、读取、写入、删除、复制、移动、搜索 | 已完成 |
| Shell 命令 | 命令执行，超时控制 | 已完成 |
| 网络请求 | HTTP GET / POST | 已完成 |
| 网页抓取 | 打开网页、提取正文、搜索关键词、链接点击 | 已完成（非完整浏览器） |
| 媒体处理 | FFmpeg 音视频信息、转换、压缩、提取音频、缩放、裁剪 | 已完成（需设备安装 ffmpeg） |
| 计算器 | 四则运算、三角函数、对数、平方根 | 已完成 |
| 设备信息 | 系统摘要、环境变量、CPU 信息 | 已完成 |
| 系统操作 | Intent 打开/分享/拨号/短信、广播发送 | 已完成 |
| 无障碍服务 | 元素点击、滑动、输入文字、读取屏幕、返回、主页 | 已完成（需用户启用无障碍） |
| 蓝牙管理 | 状态查询、扫描、开关、配对列表 | 已完成 |
| 音乐播放 | 播放、暂停、停止、恢复、音量控制 | 已完成 |
| 系统设置 | 亮度调节、屏幕超时 | 已完成 |
| APK 管理 | 已安装列表、APK 信息、安装引导、卸载引导 | 已完成 |
| 屏幕录制 | 截图、视频录制（H264+AAC，MP4） | 已完成（真机测试有限） |
| 语音合成 | TTS 文字转语音 | 已完成 |
| 语音识别 | STT 语音转文字 | 已完成 |
| 系统管理 | 进程列表、强制停止、清理缓存 | 已完成 |
| Root 操作 | 特权命令执行、Root 检测 | 已完成（需 Root 权限） |

### 工作流

| 功能 | 状态 |
|------|------|
| 多步骤任务编排 | 已完成 |
| 顺序执行工具链 | 已完成 |
| 存储与管理 | 已完成 |
| 定时调度 | 基础版已完成 |

---

## 技术栈

| 层 | 技术 |
|----|------|
| 前端 UI | Kotlin + Jetpack Compose + Material 3 |
| 状态管理 | Android ViewModel + StateFlow |
| 后端引擎 | Go 1.22+ (标准库) |
| 通信 | Unix Socket JSON-RPC 2.0 |
| 本地推理 | llama.cpp / MNN（通过 NDK） |
| 语音 | Android TTS + SpeechRecognizer |
| 截图录制 | MediaProjection + MediaRecorder |
| 无障碍 | AccessibilityService |
| 构建 | Gradle KTS + Go build |

---

## 项目结构

```
PhoneClaw/
├── app/                          # Android 端
│   └── src/main/java/com/phoneclaw/app/
│       ├── bridge/               # Go 引擎桥接、Android 工具桥接、无障碍服务
│       ├── ui/                   # Compose UI、ViewModel、悬浮窗、截图、语音服务
│       ├── ui/screens/           # 文件管理器、日志、SQL 查看器
│       ├── ui/theme/             # 主题、颜色、字体
│       └── animation/            # 弹簧动画系统
├── core/agent-go/                # Go 后端引擎
│   ├── cmd/agentd/               # 入口
│   └── internal/
│       ├── agent/                # 规划器、执行器、权限策略
│       ├── llm/                  # LLM 多供应商路由
│       ├── tools/                # 工具注册表和实现
│       ├── tools/bridge/         # Android 桥接层
│       ├── memory/               # 记忆存储 + 向量搜索
│       ├── server/               # Unix Socket JSON-RPC 服务器
│       ├── workflow/             # 工作流引擎
│       ├── mcp/                  # MCP 协议
│       └── storage/              # 配置文件持久化
├── llama/                        # llama.cpp JNI 模块
├── mnn/                          # MNN LLM JNI 模块
└── gradle/
```

---

## 构建

```bash
# 编译 Go 后端引擎
cd core/agent-go && go build ./cmd/agentd

# 编译 Android APK
./gradlew :app:assembleDebug
```

APK 位于 `app/build/outputs/apk/debug/`，安装后启动即可。

---

## 未完成部分

| 项目 | 说明 |
|------|------|
| 浏览器 | 当前为 HTML 抓取器，不是完整浏览器 |
| 终端模拟器 | session 为内存对象，不是真实 shell 进程 |
| Ubuntu 环境 | 需要用户自行下载 rootfs 安装 |
| 本地模型推理 | JNI 接口已接入，C++ 库需要 NDK 编译 |
| 屏幕录制 | 功能已实现，在部分机型上需适配 |
| 真机兼容性 | 部分功能在真机上未充分测试 |
