<div align="center">
  <a href="README.md">中文</a> | <span>English</span>
</div>

<div align="center">
  <img src="https://img.shields.io/badge/Platform-Android_8.0%2B-brightgreen.svg" alt="Platform">
  <img src="https://img.shields.io/badge/Go-1.22+-blue.svg" alt="Go">
  <br>
</div>

<div align="center">
  <h1>PhoneClaw</h1>
  <p>📱 Android AI Assistant — Local Inference + Tool Calling</p>
  <p><b>This project is continuously updated and modified</b></p>
</div>

---

## About

PhoneClaw is an AI engine for Android. The core concept is "Claw" — grabbing and manipulating the system. It integrates LLM conversation, system tool calling, workflow automation, and local model inference. It uses Kotlin + Jetpack Compose for the UI and Go for the backend engine, communicating via Unix Socket.

---

## Architecture

```
+-------------------------------------------+
|            PhoneClaw System               |
+-------------------------------------------+
|   Go Engine (agentd)                      |
|  +----------+ +----------+ +------------+ |
|  |LLM Router| | Tool Reg | |Agent/Memory| |
|  |  Multiple| | 22 Tools | |Plan/Workflow| |
|  +----------+ +----------+ +------------+ |
|              | JSON-RPC                    |
+--------------|----------------------------+
               v Unix Socket
+-------------------------------------------+
|   Android App (Kotlin)                    |
|  +----------+ +----------+ +------------+ |
|  | UI Layer  | | Bridge   | |AndroidTool | |
|  | Compose   | | IPC      | | BT/Music…  | |
|  +----------+ +----------+ +------------+ |
+-------------------------------------------+
```

---

## Highlights

<table>
<tr>
<td width="50%">

### AI Chat
Supports OpenAI, Claude, Gemini, Deepseek, Qwen, Mistral, Ollama and more. Local model inference supported.

### Smart Memory
Memory save, search and management with TF-IDF vector ranking.

</td>
<td width="50%">

### Tool Ecosystem
File management, Shell execution, web scraping, media processing, Bluetooth, music playback, system settings, APK management, screen recording, TTS/STT, accessibility service, and more.

### Workflow and Automation
Multi-step automation tasks with MCP protocol extension.

</td>
</tr>
</table>

---

## Features

### AI Chat

| Feature | Description |
|---------|-------------|
| Multiple Providers | OpenAI, Claude, Gemini, Deepseek, Qwen, Mistral, Ollama |
| Local Inference | llama.cpp / MNN interfaces ready, requires NDK build |
| Conversation History | Context persistence |
| Custom Endpoints | Custom API URLs for OpenAI-compatible services |

### Tool System

| Tool | Operations | Status |
|------|------------|--------|
| File System | Browse, read, write, delete, copy, move, search | Done |
| Shell Command | Execution with timeout | Done |
| Network | HTTP GET / POST | Done |
| Web Scraper | Open pages, extract content, search, click links | Done |
| Media Processing | FFmpeg info, convert, compress, extract audio, resize, trim | Done |
| Calculator | Arithmetic, trig, log, sqrt | Done |
| Device Info | System summary, env, CPU | Done |
| System Actions | Intent open/share/dial/sms, broadcast | Done |
| Accessibility | Click, swipe, type, read screen, back, home | Done |
| Bluetooth | Status, scan, toggle, paired devices | Done |
| Music Playback | Play, pause, stop, resume, volume | Done |
| System Settings | Brightness, screen timeout | Done |
| APK Management | Installed list, APK info, install, uninstall | Done |
| Screen Recording | Screenshot, video recording (H264+AAC, MP4) | Done |
| Text to Speech | TTS engine | Done |
| Speech Recognition | STT voice to text | Done |
| System Management | Process list, force stop, clear cache | Done |
| Root Operations | Privileged commands, root detection | Done |

### Workflow

| Feature | Status |
|---------|--------|
| Multi-step task orchestration | Done |
| Sequential tool execution | Done |
| Storage and management | Done |
| Scheduled execution | Basic support done |

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Kotlin + Jetpack Compose + Material 3 |
| State Management | Android ViewModel + StateFlow |
| Backend | Go 1.22+ (stdlib) |
| Communication | Unix Socket JSON-RPC 2.0 |
| Local Inference | llama.cpp / MNN (via NDK) |
| Voice | Android TTS + SpeechRecognizer |
| Screen Capture | MediaProjection + MediaRecorder |
| Accessibility | AccessibilityService |
| Build | Gradle KTS + Go build |

---

## Project Structure

```
PhoneClaw/
├── app/                          # Android app
│   └── src/main/java/com/phoneclaw/app/
│       ├── bridge/               # Go bridge, Android tool bridge, accessibility
│       ├── ui/                   # Compose UI, ViewModel, floating, capture, voice
│       ├── ui/screens/           # File manager, Logcat, SQL viewer
│       ├── ui/theme/             # Theme, colors, typography
│       └── animation/            # Spring animation system
├── core/agent-go/                # Go backend engine
│   ├── cmd/agentd/               # Entry point
│   └── internal/
│       ├── agent/                # Planner, executor, permission policy
│       ├── llm/                  # LLM multi-provider router
│       ├── tools/                # Tool registry and implementations
│       ├── tools/bridge/         # Android bridge layer
│       ├── memory/               # Memory store + vector search
│       ├── server/               # Unix Socket JSON-RPC server
│       ├── workflow/             # Workflow engine
│       ├── mcp/                  # MCP protocol
│       └── storage/              # Config persistence
├── llama/                        # llama.cpp JNI module
├── mnn/                          # MNN LLM JNI module
└── gradle/
```

---

## Build

```bash
# Build Go backend engine
cd core/agent-go && go build ./cmd/agentd

# Build Android APK
./gradlew :app:assembleDebug
```

APK is at `app/build/outputs/apk/debug/`. Install and launch.

---

## Unfinished Parts

| Item | Description |
|------|-------------|
| Browser | Currently an HTML scraper, not a full browser |
| Terminal | Sessions are in-memory objects, not real shell processes |
| Ubuntu Environment | Requires manual rootfs download |
| Local Model Inference | JNI interfaces ready, native libs need NDK build |
| Screen Recording | Implemented, may need adaptation on some devices |
| Device Compatibility | Some features not fully tested on real devices |
