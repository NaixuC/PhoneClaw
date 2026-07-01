package agent

import (
	"encoding/json"
	"fmt"
)

type Runtime struct {
	planner        Planner
	policy         Policy
	toolRunner     ToolRunner
	llmRouter      LLMRouter
	memStore       MemoryStore
	workflowEngine WorkflowEngine
	mcpManager     MCPManager
	store          DataStore

}

type ToolRunner interface {
	Execute(req ToolRequest) ToolResult
	ListTools() []ToolDescriptor
}

type LLMRouter interface {
	HandleComplete(params json.RawMessage) (any, error)
	HandleStream(params json.RawMessage) (any, error)
	HandleListModels(params json.RawMessage) (any, error)
}

type MemoryStore interface {
	HandleSearch(params json.RawMessage) (any, error)
	HandleSave(params json.RawMessage) (any, error)
	HandleDelete(params json.RawMessage) (any, error)
	HandleList(params json.RawMessage) (any, error)
}

type WorkflowEngine interface {
	HandleList(params json.RawMessage) (any, error)
	HandleSave(params json.RawMessage) (any, error)
	HandleDelete(params json.RawMessage) (any, error)
	HandleRun(params json.RawMessage) (any, error)
}

type MCPManager interface {
	HandleList(params json.RawMessage) (any, error)
	HandleAdd(params json.RawMessage) (any, error)
	HandleRemove(params json.RawMessage) (any, error)
	HandleInvoke(params json.RawMessage) (any, error)
}

type DataStore interface {
	HandleConfigGet(params json.RawMessage) (any, error)
	HandleConfigSet(params json.RawMessage) (any, error)
	HandleCharacterList(params json.RawMessage) (any, error)
	HandleCharacterSave(params json.RawMessage) (any, error)
	HandleCharacterDelete(params json.RawMessage) (any, error)
}

func NewRuntime(tr ToolRunner, llr LLMRouter, ms MemoryStore, we WorkflowEngine, mm MCPManager, ds DataStore) *Runtime {
	return &Runtime{
		toolRunner:     tr,
		llmRouter:      llr,
		memStore:       ms,
		workflowEngine: we,
		mcpManager:     mm,
		store:          ds,
	}
}

func (r *Runtime) HandlePlan(params json.RawMessage) (any, error) {
	var req AgentRequest
	if err := json.Unmarshal(params, &req); err != nil {
		return nil, fmt.Errorf("bad request: %w", err)
	}
	req = r.normalize(req)
	tools := r.planner.InferTools(req)
	perms := r.policy.MissingPermissions(tools, req.AllowedTools)
	steps := r.planner.BuildPlan(req, tools, len(perms) > 0)
	evts := []Event{
		{Label: "规划器", Body: fmt.Sprintf("为「%s」创建了执行计划", req.Goal), Tone: ToneGood},
		{Label: "模式", Body: fmt.Sprintf("使用%s模式", r.modeLabel(req.Mode)), Tone: ToneNeutral},
	}
	if len(perms) > 0 {
		evts = append(evts, Event{Label: "权限", Body: fmt.Sprintf("%d个工具需要授权", len(perms)), Tone: ToneWarn})
	}
	return AgentResponse{
		Summary:     fmt.Sprintf("已创建%d步计划", len(steps)),
		Confidence:  r.confidence(req.Mode, len(perms)),
		Plan:        steps,
		Permissions: perms,
		Events:      evts,
	}, nil
}

func (r *Runtime) HandleExecute(params json.RawMessage) (any, error) {
	var req AgentRequest
	if err := json.Unmarshal(params, &req); err != nil {
		return nil, fmt.Errorf("bad request: %w", err)
	}
	req = r.normalize(req)
	tools := r.planner.InferTools(req)
	perms := r.policy.MissingPermissions(tools, req.AllowedTools)
	if len(perms) > 0 {
		return AgentResponse{
			Summary: "执行被权限阻止", Permissions: perms,
			Events: []Event{{Label: "阻止", Body: "请先批准所需工具权限", Tone: ToneWarn}},
			Execution: &Execution{Success: false, Title: "执行阻止", Detail: "缺少必要权限", CompletedSteps: 0},
		}, nil
	}
	steps := r.planner.BuildPlan(req, tools, false)
	var completed []PlanStep
	var allSucceeded = true
	for _, s := range steps {
		s.Status = StepDone
		if s.Tool != nil && r.toolRunner != nil {
			result := r.toolRunner.Execute(ToolRequest{
				Tool:         *s.Tool,
				Action:       "exec",
				Input:        req.Goal,
				AllowedTools: req.AllowedTools,
			})
			if !result.Success {
				s.Status = StepBlocked
				allSucceeded = false
			}
		}
		completed = append(completed, s)
	}
	if !allSucceeded {
		return AgentResponse{
			Summary: fmt.Sprintf("部分步骤执行失败，共%d步", len(completed)),
			Plan:    completed,
			Events:  []Event{{Label: "执行器", Body: "部分步骤执行失败", Tone: ToneWarn}},
			Execution: &Execution{Success: false, Title: "执行失败",
				Detail: fmt.Sprintf("%d步骤已完成，部分失败", len(completed)), CompletedSteps: len(completed)},
		}, nil
	}
	return AgentResponse{
		Summary: fmt.Sprintf("执行完成，共%d步", len(completed)),
		Plan:    completed,
		Events:  []Event{{Label: "执行器", Body: "所有步骤已执行", Tone: ToneGood}},
		Execution: &Execution{Success: true, Title: "执行完成",
			Detail: fmt.Sprintf("%d步骤已执行", len(completed)), CompletedSteps: len(completed)},
	}, nil
}

func (r *Runtime) HandleAndroidTool(params json.RawMessage) (any, error) {
	var action AndroidAction
	if err := json.Unmarshal(params, &action); err != nil {
		return nil, fmt.Errorf("bad android action: %w", err)
	}
	// Return the action descriptor. The GoAgentBridge on Android detects _android:true
	// and executes the action via AndroidToolBridge, then sends result back.
	return map[string]any{
		"actionId": action.ActionID,
		"tool":     action.Tool,
		"action":   action.Action,
		"input":    action.Input,
		"params":   action.Params,
		"_android": true,
	}, nil
}

func (r *Runtime) HandleAndroidResult(params json.RawMessage) (any, error) {
	var result AndroidActionResult
	if err := json.Unmarshal(params, &result); err != nil {
		return nil, fmt.Errorf("bad result: %w", err)
	}
	// Log and acknowledge. Result is already stored by the AndroidToolBridge.
	return map[string]bool{"ok": true}, nil
}

func (r *Runtime) HandleSystemInfo(params json.RawMessage) (any, error) {
	tc := 0
	if r.toolRunner != nil { tc = len(r.toolRunner.ListTools()) }
	return map[string]any{
		"version":   "1.0.0",
		"goos":      "android",
		"goarch":    "arm64",
		"tools":     tc,
		"platform":  "Operit NG",
	}, nil
}

func (r *Runtime) normalize(req AgentRequest) AgentRequest {
	if req.Goal == "" {
		req.Goal = "创建一个可执行的计划"
	}
	if req.Mode == "" {
		req.Mode = ModeBalanced
	}
	return req
}

func (r *Runtime) modeLabel(m Mode) string {
	switch m {
	case ModeFast:
		return "快速"
	case ModeDeep:
		return "深度"
	default:
		return "平衡"
	}
}

func (r *Runtime) confidence(m Mode, blocked int) float32 {
	base := float32(0.83)
	if m == ModeFast {
		base = 0.74
	} else if m == ModeDeep {
		base = 0.88
	}
	return base - float32(blocked)*0.04
}
