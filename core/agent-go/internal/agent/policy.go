package agent

// Policy implements tool-level approval checking.
// This checks which tools the user has approved for use.
// Android runtime permissions must still be requested through Android's
// standard checkSelfPermission/requestPermissions APIs at runtime.
type Policy struct{}

func (Policy) MissingPermissions(required []ToolKind, allowed []ToolKind) []PermissionAsk {
	aset := make(map[ToolKind]bool)
	for _, t := range allowed { aset[t] = true }
	var asks []PermissionAsk
	for _, t := range required {
		if aset[t] { continue }
		asks = append(asks, PermissionAsk{
			Tool:   t,
			Reason: permissionReason(t),
			Risk:   permissionRisk(t),
		})
	}
	return asks
}

func permissionReason(t ToolKind) string {
	switch t {
	case ToolFilesystem:
		return "需要读写文件（Android: READ/WRITE/MANAGE_EXTERNAL_STORAGE）"
	case ToolShell:
		return "需要执行 Shell 命令"
	case ToolNetwork:
		return "需要网络访问（Android: INTERNET）"
	case ToolBrowser:
		return "需要网络访问（Android: INTERNET）"
	case ToolIntent:
		return "需要执行 Android 系统操作（Android: SYSTEM_ALERT_WINDOW）"
	case ToolAccessibility:
		return "需要无障碍服务（Android: BIND_ACCESSIBILITY_SERVICE）"
	case ToolRoot:
		return "需要 Root/Shizuku 高权限"
	case ToolScreenShare:
		return "需要屏幕录制（Android: FOREGROUND_SERVICE_MEDIA_PROJECTION）"
	case ToolVoiceTTS:
		return "需要语音合成（TTS引擎）"
	case ToolVoiceSTT:
		return "需要录音权限（Android: RECORD_AUDIO）"
	case ToolBluetooth:
		return "需要蓝牙权限（Android: BLUETOOTH_CONNECT/SCAN）"
	case ToolMusic:
		return "需要媒体播放"
	case ToolSettings:
		return "需要修改系统设置（Android: WRITE_SETTINGS）"
	case ToolSystemOps:
		return "需要后台进程管理（Android: KILL_BACKGROUND_PROCESSES）"
	case ToolAPK:
		return "需要安装/卸载 APK（Android: REQUEST_INSTALL_PACKAGES）"
	case ToolUbuntu:
		return "需要 Ubuntu 环境"
	default:
		return "需要一个受控工具来完成此任务"
	}
}

func permissionRisk(t ToolKind) RiskLevel {
	switch t {
	case ToolShell, ToolRoot, ToolSystemOps:
		return RiskHigh
	case ToolFilesystem, ToolAccessibility, ToolAPK, ToolSettings:
		return RiskMedium
	default:
		return RiskLow
	}
}
