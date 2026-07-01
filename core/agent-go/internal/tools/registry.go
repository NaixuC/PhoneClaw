package tools

import (
	"encoding/json"
	"fmt"
	"phoneclaw/agent-go/internal/agent"
	"phoneclaw/agent-go/internal/tools/bridge"
)

type Registry struct {
	tools    map[agent.ToolKind]ToolFn
	descs    []agent.ToolDescriptor
	android  *bridge.AndroidBridge
}

type ToolFn func(action string, input string, params map[string]any) agent.ToolResult

func NewRegistry(android *bridge.AndroidBridge) *Registry {
	r := &Registry{
		tools:   make(map[agent.ToolKind]ToolFn),
		android: android,
	}
	r.registerAll()
	return r
}

func (r *Registry) register(kind agent.ToolKind, fn ToolFn, desc agent.ToolDescriptor) {
	r.tools[kind] = fn
	r.descs = append(r.descs, desc)
}

func (r *Registry) registerAll() {
	r.register(agent.ToolFilesystem, runFilesystem, descFs)
	r.register(agent.ToolShell, runShell, descShell)
	r.register(agent.ToolNetwork, runNetwork, descNet)
	r.register(agent.ToolBrowser, runBrowser, descBrowser)
	r.register(agent.ToolFFmpeg, runFFmpeg, descFFmpeg)
	r.register(agent.ToolCalculator, runCalc, descCalc)
	r.register(agent.ToolDeviceInfo, runDevice, descDevice)
	r.register(agent.ToolIntent, r.android.Forward(agent.ToolIntent), descIntent)
	r.register(agent.ToolBroadcast, r.android.Forward(agent.ToolBroadcast), descBroadcast)
	r.register(agent.ToolBluetooth, r.android.Forward(agent.ToolBluetooth), descBluetooth)
	r.register(agent.ToolMusic, r.android.Forward(agent.ToolMusic), descMusic)
	r.register(agent.ToolSettings, r.android.Forward(agent.ToolSettings), descSettings)
	r.register(agent.ToolUIAutomation, r.android.Forward(agent.ToolUIAutomation), descUIAuto)
	r.register(agent.ToolScreenShare, r.android.Forward(agent.ToolScreenShare), descScreen)
	r.register(agent.ToolSystemOps, r.android.Forward(agent.ToolSystemOps), descSysOps)
	r.register(agent.ToolAPK, r.android.Forward(agent.ToolAPK), descAPK)
	r.register(agent.ToolAccessibility, r.android.Forward(agent.ToolAccessibility), descAccess)
	r.register(agent.ToolRoot, r.android.Forward(agent.ToolRoot), descRoot)
	r.register(agent.ToolVoiceTTS, r.android.Forward(agent.ToolVoiceTTS), descTTS)
	r.register(agent.ToolVoiceSTT, r.android.Forward(agent.ToolVoiceSTT), descSTT)
	r.register(agent.ToolTerminal, runTerminal, descTerm)
	r.register(agent.ToolUbuntu, runUbuntu, descUbuntu)
}

func (r *Registry) HandleList(params json.RawMessage) (any, error) {
	return map[string]any{"tools": r.descs}, nil
}

func (r *Registry) HandleRun(params json.RawMessage) (any, error) {
	var req agent.ToolRequest
	if err := json.Unmarshal(params, &req); err != nil {
		return nil, fmt.Errorf("bad tool request: %w", err)
	}
	fn, ok := r.tools[req.Tool]
	if !ok {
		return agent.ToolResult{Success: false, Title: "未知工具", Output: string(req.Tool), Risk: agent.RiskMedium}, nil
	}
	allowed := make(map[agent.ToolKind]bool)
	for _, t := range req.AllowedTools {
		allowed[t] = true
	}
	if !allowed[req.Tool] {
		return agent.ToolResult{Success: false, Title: "工具未授权", Output: "请先授权" + string(req.Tool), Risk: toolRisk(req.Tool)}, nil
	}
	result := fn(req.Action, req.Input, req.Params)
	return result, nil
}

func (r *Registry) Execute(req agent.ToolRequest) agent.ToolResult {
	fn, ok := r.tools[req.Tool]
	if !ok {
		return agent.ToolResult{Success: false, Title: "未知工具", Output: string(req.Tool), Risk: agent.RiskMedium}
	}
	return fn(req.Action, req.Input, req.Params)
}

func (r *Registry) ListTools() []agent.ToolDescriptor {
	return r.descs
}

func toolRisk(kind agent.ToolKind) agent.RiskLevel {
	switch kind {
	case agent.ToolShell, agent.ToolRoot, agent.ToolSystemOps:
		return agent.RiskHigh
	case agent.ToolFilesystem, agent.ToolAccessibility, agent.ToolAPK, agent.ToolSettings:
		return agent.RiskMedium
	default:
		return agent.RiskLow
	}
}
