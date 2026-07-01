package tools

import "phoneclaw/agent-go/internal/agent"

var (
	descFs = agent.ToolDescriptor{
		Kind: agent.ToolFilesystem, Label: "文件系统", Description: "读写文件和目录",
		DefaultAct: "list", DefaultInput: ".", Risk: agent.RiskMedium, Runtime: agent.RuntimeGo,
		Actions: []string{"list", "read", "write", "delete", "copy", "move", "search", "info"},
		Categories: []string{"standard", "core"},
	}
	descShell = agent.ToolDescriptor{
		Kind: agent.ToolShell, Label: "Shell", Description: "执行Shell命令和脚本",
		DefaultAct: "run", DefaultInput: "echo hello", Risk: agent.RiskHigh, Runtime: agent.RuntimeGo,
		Actions: []string{"run"},
		Categories: []string{"standard", "core"},
	}
	descNet = agent.ToolDescriptor{
		Kind: agent.ToolNetwork, Label: "网络", Description: "HTTP请求和网页内容获取",
		DefaultAct: "get", DefaultInput: "https://example.com", Risk: agent.RiskLow, Runtime: agent.RuntimeGo,
		Actions: []string{"get", "post", "head"},
		Categories: []string{"standard", "core"},
	}
	descBrowser = agent.ToolDescriptor{
		Kind: agent.ToolBrowser, Label: "网页抓取", Description: "网页内容抓取和链接提取（非完整浏览器）",
		DefaultAct: "open", DefaultInput: "https://google.com", Risk: agent.RiskLow, Runtime: agent.RuntimeGo,
		Actions: []string{"open", "click", "extract", "search"},
		Categories: []string{"standard", "web"},
	}
	descFFmpeg = agent.ToolDescriptor{
		Kind: agent.ToolFFmpeg, Label: "媒体处理", Description: "音视频格式转换和处理",
		DefaultAct: "info", DefaultInput: "input.mp4", Risk: agent.RiskLow, Runtime: agent.RuntimeGo,
		Actions: []string{"info", "convert", "compress", "extract_audio", "resize", "trim"},
		Categories: []string{"standard", "media"},
	}
	descCalc = agent.ToolDescriptor{
		Kind: agent.ToolCalculator, Label: "计算器", Description: "数学表达式计算",
		DefaultAct: "eval", DefaultInput: "1+2*3", Risk: agent.RiskLow, Runtime: agent.RuntimeGo,
		Actions: []string{"eval"},
		Categories: []string{"standard", "utility"},
	}
	descDevice = agent.ToolDescriptor{
		Kind: agent.ToolDeviceInfo, Label: "设备信息", Description: "获取设备和运行时信息",
		DefaultAct: "summary", DefaultInput: "", Risk: agent.RiskLow, Runtime: agent.RuntimeGo,
		Actions: []string{"summary", "env", "cpu"},
		Categories: []string{"standard", "system"},
	}
	descIntent = agent.ToolDescriptor{
		Kind: agent.ToolIntent, Label: "系统操作", Description: "启动Activity和系统交互",
		DefaultAct: "open", DefaultInput: "https://example.com", Risk: agent.RiskLow, Runtime: agent.RuntimeAndroid,
		Actions: []string{"open", "share", "dial", "send", "settings"},
		Categories: []string{"android", "core"},
	}
	descAccess = agent.ToolDescriptor{
		Kind: agent.ToolAccessibility, Label: "无障碍", Description: "通过无障碍服务操作界面",
		DefaultAct: "click", DefaultInput: "描述要点击的元素", Risk: agent.RiskMedium, Runtime: agent.RuntimeAndroid,
		Actions: []string{"click", "swipe", "type", "scroll", "find", "read_screen", "wait_and_click", "back", "home", "recent"},
		Categories: []string{"android", "automation"},
	}
	descRoot = agent.ToolDescriptor{
		Kind: agent.ToolRoot, Label: "Root/Shizuku", Description: "通过Root或Shizuku执行高权限操作",
		DefaultAct: "exec", DefaultInput: "whoami", Risk: agent.RiskHigh, Runtime: agent.RuntimeAndroid,
		Actions: []string{"exec", "check", "reboot", "grant"},
		Categories: []string{"android", "privilege"},
	}
	descBroadcast = agent.ToolDescriptor{
		Kind: agent.ToolBroadcast, Label: "广播", Description: "发送Android广播",
		DefaultAct: "send", DefaultInput: "com.example.ACTION", Risk: agent.RiskLow, Runtime: agent.RuntimeAndroid,
		Actions: []string{"send"},
		Categories: []string{"android"},
	}
	descBluetooth = agent.ToolDescriptor{
		Kind: agent.ToolBluetooth, Label: "蓝牙", Description: "蓝牙设备管理",
		DefaultAct: "status", DefaultInput: "", Risk: agent.RiskLow, Runtime: agent.RuntimeAndroid,
		Actions: []string{"status", "scan", "enable", "disable", "paired"},
		Categories: []string{"android", "hardware"},
	}
	descMusic = agent.ToolDescriptor{
		Kind: agent.ToolMusic, Label: "音乐", Description: "音乐播放控制",
		DefaultAct: "play", DefaultInput: "file:///sdcard/Music/song.mp3", Risk: agent.RiskLow, Runtime: agent.RuntimeAndroid,
		Actions: []string{"play", "pause", "stop", "resume", "volume", "status"},
		Categories: []string{"android", "media"},
	}
	descSettings = agent.ToolDescriptor{
		Kind: agent.ToolSettings, Label: "系统设置", Description: "修改Android系统设置",
		DefaultAct: "get", DefaultInput: "wifi", Risk: agent.RiskMedium, Runtime: agent.RuntimeAndroid,
		Actions: []string{"get", "set", "brightness", "screen_timeout"},
		Categories: []string{"android", "system"},
	}
	descUIAuto = agent.ToolDescriptor{
		Kind: agent.ToolUIAutomation, Label: "UI自动化", Description: "模拟用户操作",
		DefaultAct: "click", DefaultInput: "x:y", Risk: agent.RiskMedium, Runtime: agent.RuntimeAndroid,
		Actions: []string{"click", "swipe", "type", "gesture", "keyevent"},
		Categories: []string{"android", "automation"},
	}
	descScreen = agent.ToolDescriptor{
		Kind: agent.ToolScreenShare, Label: "屏幕录制", Description: "屏幕截图和录制",
		DefaultAct: "capture", DefaultInput: "", Risk: agent.RiskLow, Runtime: agent.RuntimeAndroid,
		Actions: []string{"capture", "record", "stop_record"},
		Categories: []string{"android", "media"},
	}
	descSysOps = agent.ToolDescriptor{
		Kind: agent.ToolSystemOps, Label: "系统管理", Description: "系统管理和进程操作",
		DefaultAct: "processes", DefaultInput: "", Risk: agent.RiskHigh, Runtime: agent.RuntimeAndroid,
		Actions: []string{"processes", "force_stop", "clear_cache", "uninstall"},
		Categories: []string{"android", "system"},
	}
	descAPK = agent.ToolDescriptor{
		Kind: agent.ToolAPK, Label: "APK管理", Description: "安装、卸载和解析APK",
		DefaultAct: "info", DefaultInput: "/path/to/app.apk", Risk: agent.RiskMedium, Runtime: agent.RuntimeAndroid,
		Actions: []string{"list_installed", "info", "install", "uninstall"},
		Categories: []string{"android", "package"},
	}
	descTTS = agent.ToolDescriptor{
		Kind: agent.ToolVoiceTTS, Label: "语音合成", Description: "文字转语音",
		DefaultAct: "speak", DefaultInput: "你好世界", Risk: agent.RiskLow, Runtime: agent.RuntimeAndroid,
		Actions: []string{"speak", "stop"},
		Categories: []string{"android", "voice"},
	}
	descSTT = agent.ToolDescriptor{
		Kind: agent.ToolVoiceSTT, Label: "语音识别", Description: "语音转文字",
		DefaultAct: "listen", DefaultInput: "", Risk: agent.RiskLow, Runtime: agent.RuntimeAndroid,
		Actions: []string{"listen", "stop_listen", "is_listening"},
		Categories: []string{"android", "voice"},
	}
	descTerm = agent.ToolDescriptor{
		Kind: agent.ToolTerminal, Label: "命令执行器", Description: "轻量命令执行器（非完整终端）",
		DefaultAct: "exec", DefaultInput: "ls", Risk: agent.RiskMedium, Runtime: agent.RuntimeGo,
		Actions: []string{"exec", "session_create", "session_close", "list_sessions"},
		Categories: []string{"android", "terminal"},
	}
	descUbuntu = agent.ToolDescriptor{
		Kind: agent.ToolUbuntu, Label: "Ubuntu环境", Description: "内置Ubuntu Linux环境操作",
		DefaultAct: "exec", DefaultInput: "apt update", Risk: agent.RiskMedium, Runtime: agent.RuntimeGo,
		Actions: []string{"exec", "status", "install"},
		Categories: []string{"android", "environment"},
	}
)
