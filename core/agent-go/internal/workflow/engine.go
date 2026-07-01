package workflow

import (
	"encoding/json"
	"fmt"
	"phoneclaw/agent-go/internal/agent"
	"phoneclaw/agent-go/internal/storage"
	"sync"
	"time"
)

type Engine struct {
	store     *storage.Store
	toolRunner agent.ToolRunner
	workflows []Workflow
	mu        sync.RWMutex
}

func NewEngine(s *storage.Store, tr agent.ToolRunner) *Engine {
	e := &Engine{store: s, toolRunner: tr}
	e.load()
	return e
}

func (e *Engine) load() {
	e.mu.Lock()
	defer e.mu.Unlock()
	var data struct {
		Items []Workflow `json:"items"`
	}
	if err := e.store.Read("workflows", "index", &data); err == nil {
		e.workflows = data.Items
	}
	if e.workflows == nil {
		e.workflows = make([]Workflow, 0)
	}
}

func (e *Engine) save() {
	e.store.Write("workflows", "index", map[string]any{"items": e.workflows})
}

func (e *Engine) HandleList(params json.RawMessage) (any, error) {
	e.mu.RLock()
	defer e.mu.RUnlock()
	return map[string]any{"workflows": e.workflows, "count": len(e.workflows)}, nil
}

func (e *Engine) HandleSave(params json.RawMessage) (any, error) {
	var wf Workflow
	if err := json.Unmarshal(params, &wf); err != nil {
		return nil, fmt.Errorf("bad request: %w", err)
	}
	now := time.Now().UnixMilli()
	if wf.ID == "" {
		wf.ID = fmt.Sprintf("wf_%d", now)
	}
	wf.UpdatedAt = now
	wf.CreatedAt = now
	e.mu.Lock()
	// Update if exists
	found := false
	for i, w := range e.workflows {
		if w.ID == wf.ID {
			e.workflows[i] = wf
			found = true
			break
		}
	}
	if !found {
		e.workflows = append(e.workflows, wf)
	}
	e.mu.Unlock()
	e.save()
	return map[string]any{"id": wf.ID, "saved": true}, nil
}

func (e *Engine) HandleDelete(params json.RawMessage) (any, error) {
	var req struct{ ID string `json:"id"` }
	if err := json.Unmarshal(params, &req); err != nil {
		return nil, fmt.Errorf("bad request: %w", err)
	}
	e.mu.Lock()
	defer e.mu.Unlock()
	for i, w := range e.workflows {
		if w.ID == req.ID {
			e.workflows = append(e.workflows[:i], e.workflows[i+1:]...)
			e.save()
			return map[string]bool{"deleted": true}, nil
		}
	}
	return map[string]bool{"deleted": false}, nil
}

func (e *Engine) HandleRun(params json.RawMessage) (any, error) {
	var req struct {
		ID string `json:"id"`
	}
	if err := json.Unmarshal(params, &req); err != nil {
		return nil, fmt.Errorf("bad request: %w", err)
	}
	e.mu.RLock()
	var wf *Workflow
	for _, w := range e.workflows {
		if w.ID == req.ID {
			wf = &w
			break
		}
	}
	e.mu.RUnlock()
	if wf == nil {
		return nil, fmt.Errorf("workflow %s not found", req.ID)
	}

	// Execute steps sequentially
	var outputs []string
	for i, step := range wf.Steps {
		result := e.toolRunner.Execute(agent.ToolRequest{
			Tool:   step.Tool,
			Action: step.Action,
			Input:  step.Input,
			Params: step.Params,
		})
		outputs = append(outputs, fmt.Sprintf("[%d/%d] %s/%s: %s", i+1, len(wf.Steps),
			step.Tool, step.Action, result.Title))
		if !result.Success {
			break
		}
	}
	return map[string]any{
		"workflowId": wf.ID,
		"steps":      len(wf.Steps),
		"output":     outputs,
	}, nil
}
