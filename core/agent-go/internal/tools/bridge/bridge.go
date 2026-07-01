package bridge

import (
	"fmt"
	"phoneclaw/agent-go/internal/agent"
)

type AndroidBridge struct{}

func NewAndroidBridge() *AndroidBridge {
	return &AndroidBridge{}
}

func (b *AndroidBridge) Forward(tool agent.ToolKind) func(action, input string, params map[string]any) agent.ToolResult {
	return func(action, input string, params map[string]any) agent.ToolResult {
		return agent.ToolResult{
			Success: true,
			Title:   "安卓桥接: " + string(tool),
			Output:  fmt.Sprintf("_android_bridge:%s:%s:%s", tool, action, input),
			Risk:    agent.RiskLow,
			Data: map[string]any{
				"_android": true,
				"tool":     tool,
				"action":   action,
				"input":    input,
				"params":   params,
			},
		}
	}
}
