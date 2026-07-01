package tools

import (
	"fmt"
	"os"
	"os/exec"
	"phoneclaw/agent-go/internal/agent"
	"strings"
	"sync"
	"time"
)

type terminalSession struct {
	ID        string
	Name      string
	Cmd       *exec.Cmd
	Output    strings.Builder
	CreatedAt time.Time
	Dir       string
}

var (
	termSessions = make(map[string]*terminalSession)
	termMu       sync.Mutex
	termCounter  int
)

func runTerminal(action, input string, params map[string]any) agent.ToolResult {
	switch action {
	case "exec":
		return termExec(input)
	case "session_create":
		return termCreateSession(input)
	case "session_write":
		return termWriteSession(input)
	case "session_close":
		return termCloseSession(input)
	case "list_sessions":
		return termListSessions()
	default:
		return agent.ToolResult{Success: false, Title: "不支持", Output: action, Risk: agent.RiskMedium}
	}
}

func termCreateSession(name string) agent.ToolResult {
	termMu.Lock()
	termCounter++
	id := fmt.Sprintf("term_%d", termCounter)
	if name == "" { name = fmt.Sprintf("Session %d", termCounter) }

	home, _ := os.UserHomeDir()
	workDir := home
	if workDir == "" { workDir = "/data/data/com.phoneclaw.app/files" }

	termSessions[id] = &terminalSession{
		ID: id, Name: name, Dir: workDir, CreatedAt: time.Now(),
	}
	termMu.Unlock()

	return agent.ToolResult{Success: true, Title: "终端会话已创建",
		Output: fmt.Sprintf("会话ID: %s\n名称: %s\n目录: %s", id, name, workDir),
		Risk: agent.RiskLow}
}

func termExec(input string) agent.ToolResult {
	// Parse "session_id:command" format
	parts := strings.SplitN(input, ":", 2)
	sessionID := "default"
	command := input
	if len(parts) == 2 {
		sessionID = strings.TrimSpace(parts[0])
		command = strings.TrimSpace(parts[1])
	}

	// Use default session if not found
	termMu.Lock()
	session, ok := termSessions[sessionID]
	if !ok {
		termCounter++
		id := fmt.Sprintf("term_%d", termCounter)
		home, _ := os.UserHomeDir()
		wd := home
		if wd == "" { wd = "/data/data/com.phoneclaw.app/files" }
		session = &terminalSession{ID: id, Name: "default", Dir: wd, CreatedAt: time.Now()}
		termSessions[id] = session
		sessionID = id
	}
	termMu.Unlock()

	if command == "" {
		return agent.ToolResult{Success: false, Title: "空命令", Risk: agent.RiskHigh}
	}

	cmd := exec.Command("sh", "-c", command)
	cmd.Dir = session.Dir
	output, err := cmd.CombinedOutput()
	outStr := string(output)

	// Update session state
	session.Output.WriteString(fmt.Sprintf("$ %s\n%s\n", command, outStr))

	if err != nil {
		return agent.ToolResult{Success: false, Title: "命令执行失败",
			Output: fmt.Sprintf("[会话 %s] $ %s\n%s\n错误: %v", sessionID, command, outStr, err),
			Risk: agent.RiskMedium}
	}

	return agent.ToolResult{Success: true, Title: "命令执行成功",
		Output: fmt.Sprintf("[会话 %s] $ %s\n%s", sessionID, command, outStr),
		Risk: agent.RiskMedium}
}

func termWriteSession(input string) agent.ToolResult {
	return agent.ToolResult{Success: true, Title: "已写入", Output: input, Risk: agent.RiskLow}
}

func termCloseSession(input string) agent.ToolResult {
	termMu.Lock()
	defer termMu.Unlock()
	sessionID := strings.TrimSpace(input)
	if sessionID == "" {
		// Close all
		count := len(termSessions)
		for id := range termSessions { delete(termSessions, id) }
		return agent.ToolResult{Success: true, Title: fmt.Sprintf("已关闭 %d 个会话", count), Risk: agent.RiskLow}
	}
	if _, ok := termSessions[sessionID]; ok {
		delete(termSessions, sessionID)
		return agent.ToolResult{Success: true, Title: "会话已关闭", Output: sessionID, Risk: agent.RiskLow}
	}
	return agent.ToolResult{Success: false, Title: "会话未找到", Output: sessionID, Risk: agent.RiskLow}
}

func termListSessions() agent.ToolResult {
	termMu.Lock()
	defer termMu.Unlock()
	if len(termSessions) == 0 {
		return agent.ToolResult{Success: true, Title: "终端会话", Output: "无活跃会话", Risk: agent.RiskLow}
	}
	var sb strings.Builder
	for _, s := range termSessions {
		sb.WriteString(fmt.Sprintf("ID: %s | 名称: %s | 目录: %s | 创建: %s\n",
			s.ID, s.Name, s.Dir, s.CreatedAt.Format("15:04:05")))
	}
	return agent.ToolResult{Success: true, Title: fmt.Sprintf("%d 个活跃会话", len(termSessions)),
		Output: sb.String(), Risk: agent.RiskLow}
}

func runUbuntu(action, input string, params map[string]any) agent.ToolResult {
	ubuntuDir := "/data/ubuntu"
	if a, ok := os.LookupEnv("UBUNTU_ROOT"); ok { ubuntuDir = a }

	switch action {
	case "status":
		info, err := os.Stat(ubuntuDir)
		if err != nil {
			return agent.ToolResult{Success: true, Title: "Ubuntu 环境状态",
				Output: fmt.Sprintf("Ubuntu 根目录: %s\n状态: 未安装 (目录不存在)", ubuntuDir),
				Risk: agent.RiskLow}
		}
		return agent.ToolResult{Success: true, Title: "Ubuntu 环境状态",
			Output: fmt.Sprintf("Ubuntu 根目录: %s\n大小: %d MB\n状态: 已安装",
				ubuntuDir, info.Size()/1024/1024),
			Risk: agent.RiskLow}

	case "exec":
		cmd := exec.Command("sh", "-c", fmt.Sprintf("chroot %s /bin/bash -c %s", ubuntuDir, input))
		cmd.Env = append(os.Environ(),
			"PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
			"HOME=/root",
			"TERM=xterm-256color",
		)
		output, err := cmd.CombinedOutput()
		if err != nil {
			return agent.ToolResult{Success: false, Title: "Ubuntu 执行失败",
				Output: string(output) + "\n" + err.Error(), Risk: agent.RiskMedium}
		}
		return agent.ToolResult{Success: true, Title: "Ubuntu 执行成功",
			Output: string(output), Risk: agent.RiskMedium}

	case "install":
		return agent.ToolResult{Success: false, Title: "Ubuntu 安装",
			Output: fmt.Sprintf("请先将 Ubuntu rootfs 解压到 %s\n可执行: curl -L https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04-base-arm64.tar.gz | tar -xz -C %s", ubuntuDir, ubuntuDir),
			Risk: agent.RiskMedium}

	default:
		return agent.ToolResult{Success: false, Title: "不支持", Output: action, Risk: agent.RiskMedium}
	}
}
