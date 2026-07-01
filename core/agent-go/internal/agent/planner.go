package agent

import "strings"

type Planner struct{}

func (Planner) InferTools(req AgentRequest) []ToolKind {
	goal := strings.ToLower(req.Goal)
	var tools []ToolKind
	add := func(t ToolKind) {
		for _, x := range tools {
			if x == t {
				return
			}
		}
		tools = append(tools, t)
	}

	if containsAny(goal, "file", "apk", "gradle", "project", "directory", "read", "write", "path") {
		add(ToolFilesystem)
	}
	if containsAny(goal, "build", "test", "run", "shell", "command", "terminal", "exec") {
		add(ToolShell)
	}
	if containsAny(goal, "http", "api", "download", "network", "curl", "fetch") {
		add(ToolNetwork)
	}
	if containsAny(goal, "browser", "web", "html", "page", "url", "navigate") {
		add(ToolBrowser)
	}
	if containsAny(goal, "ffmpeg", "media", "video", "audio", "convert", "transcode") {
		add(ToolFFmpeg)
	}
	if containsAny(goal, "calculate", "math", "calc", "compute") {
		add(ToolCalculator)
	}
	if containsAny(goal, "android", "phone", "intent", "system", "device", "setting") {
		add(ToolIntent)
	}
	if containsAny(goal, "accessibility", "ui", "click", "swipe", "scroll", "tap", "automation") {
		add(ToolAccessibility)
	}
	if containsAny(goal, "root", "shizuku", "su", "privilege") {
		add(ToolRoot)
	}
	if containsAny(goal, "broadcast", "send") {
		add(ToolBroadcast)
	}
	if containsAny(goal, "bluetooth") {
		add(ToolBluetooth)
	}
	if containsAny(goal, "music", "play", "audio") {
		add(ToolMusic)
	}
	if containsAny(goal, "screenshot", "screen", "record", "capture") {
		add(ToolScreenShare)
	}
	if containsAny(goal, "restart", "reboot", "kill", "process", "uninstall") {
		add(ToolSystemOps)
	}
	if containsAny(goal, "device", "runtime", "system info", "diagnostic") {
		add(ToolDeviceInfo)
	}
	if containsAny(goal, "ubuntu", "linux", "chroot") {
		add(ToolUbuntu)
	}
	if containsAny(goal, "voice", "speak", "tts", "stt", "speech") {
		add(ToolVoiceTTS)
	}
	if containsAny(goal, "terminal") {
		add(ToolTerminal)
	}

	if req.Mode == ModeDeep {
		add(ToolShell)
		add(ToolFilesystem)
	}
	if len(tools) == 0 {
		add(ToolFilesystem)
		add(ToolShell)
	}
	return tools
}

func (Planner) BuildPlan(req AgentRequest, tools []ToolKind, blocked bool) []PlanStep {
	cs := StepReady
	if blocked {
		cs = StepBlocked
	}
	steps := []PlanStep{
		{ID: "intent", Title: "理解目标", Detail: "分析用户意图、约束条件和交付物", Status: StepDone},
		{ID: "context", Title: "收集上下文", Detail: "读取任务相关的文件和信息", Status: cs, Tool: firstTool(tools)},
		{ID: "strategy", Title: "制定策略", Detail: "将任务分解为可验证的步骤", Status: StepReady},
	}
	if !blocked {
		for i, t := range tools {
			steps = append(steps, PlanStep{
				ID:     string(t),
				Title:  toolTitle(t),
				Detail: toolDetail(t),
				Status: StepReady,
				Tool:   &tools[i],
			})
		}
	}
	steps = append(steps, PlanStep{ID: "verify", Title: "验证结果", Detail: "检查结果是否满足目标要求", Status: StepReady})
	return steps
}

func firstTool(tools []ToolKind) *ToolKind {
	if len(tools) == 0 {
		return nil
	}
	return &tools[0]
}

func toolTitle(t ToolKind) string {
	m := map[ToolKind]string{
		ToolFilesystem: "文件操作", ToolShell: "命令执行", ToolNetwork: "网络请求",
		ToolBrowser: "浏览器操作", ToolFFmpeg: "媒体处理", ToolCalculator: "计算",
		ToolDeviceInfo: "设备信息", ToolIntent: "系统操作", ToolAccessibility: "无障碍操作",
		ToolRoot: "Root操作", ToolBroadcast: "广播", ToolBluetooth: "蓝牙",
		ToolMusic: "音乐", ToolSettings: "系统设置", ToolScreenShare: "屏幕录制",
		ToolSystemOps: "系统管理", ToolAPK: "APK管理", ToolUbuntu: "Ubuntu环境",
		ToolVoiceTTS: "语音合成", ToolVoiceSTT: "语音识别", ToolTerminal: "终端",
	}
	if v, ok := m[t]; ok {
		return v
	}
	return string(t)
}

func toolDetail(t ToolKind) string {
	m := map[ToolKind]string{
		ToolFilesystem: "读/写/列出/搜索文件系统", ToolShell: "执行Shell命令和脚本",
		ToolNetwork: "HTTP请求和网络资源获取", ToolBrowser: "网页浏览和交互",
		ToolFFmpeg: "音视频格式转换和处理", ToolCalculator: "数学表达式计算",
		ToolDeviceInfo: "获取设备和运行时信息", ToolIntent: "启动Activity/发送Intent",
		ToolAccessibility: "通过无障碍服务操作UI", ToolRoot: "通过Root/Shizuku执行高权限操作",
	}
	if v, ok := m[t]; ok {
		return v
	}
	return "执行" + string(t) + "操作"
}

func containsAny(text string, needles ...string) bool {
	lower := strings.ToLower(text)
	for _, n := range needles {
		if strings.Contains(lower, n) {
			return true
		}
	}
	return false
}
