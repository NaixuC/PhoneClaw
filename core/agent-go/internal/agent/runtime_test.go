package agent

import (
	"encoding/json"
	"testing"
)

func TestHandlePlan(t *testing.T) {
	r := &Runtime{}
	params := json.RawMessage(`{"goal":"list files","mode":"fast","allowedTools":[]}`)
	result, err := r.HandlePlan(params)
	if err != nil {
		t.Fatalf("HandlePlan error: %v", err)
	}
	resp, ok := result.(AgentResponse)
	if !ok {
		t.Fatalf("expected AgentResponse, got %T", result)
	}
	if resp.Summary == "" {
		t.Fatal("expected non-empty summary")
	}
	if len(resp.Plan) == 0 {
		t.Fatal("expected at least 1 plan step")
	}
}

func TestHandlePlan_EmptyGoal(t *testing.T) {
	r := &Runtime{}
	params := json.RawMessage(`{}`)
	result, err := r.HandlePlan(params)
	if err != nil {
		t.Fatalf("HandlePlan error: %v", err)
	}
	resp := result.(AgentResponse)
	if resp.Summary == "" {
		t.Fatal("expected summary even with empty goal")
	}
}

func TestHandleSystemInfo(t *testing.T) {
	r := &Runtime{}
	result, err := r.HandleSystemInfo(json.RawMessage(`{}`))
	if err != nil {
		t.Fatalf("HandleSystemInfo error: %v", err)
	}
	info, ok := result.(map[string]any)
	if !ok {
		t.Fatalf("expected map, got %T", result)
	}
	if info["version"] == nil {
		t.Fatal("expected version in system info")
	}
}

func TestInferTools(t *testing.T) {
	p := Planner{}
	req := AgentRequest{Goal: "read file.txt and run build"}
	tools := p.InferTools(req)
	found := map[ToolKind]bool{}
	for _, tool := range tools {
		found[tool] = true
	}
	if !found[ToolFilesystem] {
		t.Fatal("expected filesystem tool for file goal")
	}
	if !found[ToolShell] {
		t.Fatal("expected shell tool for run goal")
	}
}

func TestPolicy_MissingPermissions(t *testing.T) {
	p := Policy{}
	required := []ToolKind{ToolFilesystem, ToolShell}
	allowed := []ToolKind{ToolFilesystem}
	permits := p.MissingPermissions(required, allowed)
	if len(permits) != 1 {
		t.Fatalf("expected 1 missing permission, got %d", len(permits))
	}
	if permits[0].Tool != ToolShell {
		t.Fatalf("expected Shell to be missing, got %s", permits[0].Tool)
	}
}

func TestPolicy_AllApproved(t *testing.T) {
	p := Policy{}
	required := []ToolKind{ToolFilesystem, ToolShell}
	allowed := []ToolKind{ToolFilesystem, ToolShell}
	permits := p.MissingPermissions(required, allowed)
	if len(permits) != 0 {
		t.Fatalf("expected 0 missing permissions, got %d", len(permits))
	}
}
