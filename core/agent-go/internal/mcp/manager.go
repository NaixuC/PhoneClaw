package mcp

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"phoneclaw/agent-go/internal/storage"
	"sync"
	"time"
)

type MCPServer struct {
	Name        string `json:"name"`
	URL         string `json:"url"`
	Description string `json:"description,omitempty"`
	APIKey      string `json:"apiKey,omitempty"`
	Enabled     bool   `json:"enabled"`
}

type MCPTool struct {
	Name        string `json:"name"`
	Description string `json:"description"`
	ServerName  string `json:"serverName"`
	Schema      any    `json:"schema"`
}

type Manager struct {
	store   *storage.Store
	servers []MCPServer
	mu      sync.RWMutex
}

func NewManager(s *storage.Store) *Manager {
	m := &Manager{store: s}
	m.load()
	return m
}

func (m *Manager) load() {
	m.mu.Lock()
	defer m.mu.Unlock()
	var data struct{ Items []MCPServer `json:"items"` }
	if err := m.store.Read("mcp", "servers", &data); err == nil {
		m.servers = data.Items
	}
	if m.servers == nil {
		m.servers = make([]MCPServer, 0)
	}
}

func (m *Manager) save() {
	m.store.Write("mcp", "servers", map[string]any{"items": m.servers})
}

func (m *Manager) HandleList(params json.RawMessage) (any, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()
	var tools []MCPTool
	for _, s := range m.servers {
		if !s.Enabled { continue }
		st, err := m.fetchTools(s)
		if err == nil {
			tools = append(tools, st...)
		}
	}
	return map[string]any{"servers": m.servers, "tools": tools}, nil
}

func (m *Manager) HandleAdd(params json.RawMessage) (any, error) {
	var srv MCPServer
	if err := json.Unmarshal(params, &srv); err != nil {
		return nil, fmt.Errorf("bad request: %w", err)
	}
	srv.Enabled = true
	m.mu.Lock()
	m.servers = append(m.servers, srv)
	m.mu.Unlock()
	m.save()
	return map[string]any{"added": srv.Name}, nil
}

func (m *Manager) HandleRemove(params json.RawMessage) (any, error) {
	var req struct{ Name string `json:"name"` }
	if err := json.Unmarshal(params, &req); err != nil {
		return nil, fmt.Errorf("bad request: %w", err)
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	for i, s := range m.servers {
		if s.Name == req.Name {
			m.servers = append(m.servers[:i], m.servers[i+1:]...)
			m.save()
			return map[string]bool{"removed": true}, nil
		}
	}
	return map[string]bool{"removed": false}, nil
}

func (m *Manager) HandleInvoke(params json.RawMessage) (any, error) {
	var req struct {
		Server string `json:"server"`
		Tool   string `json:"tool"`
		Input  string `json:"input"`
	}
	if err := json.Unmarshal(params, &req); err != nil {
		return nil, fmt.Errorf("bad request: %w", err)
	}
	m.mu.RLock()
	var srv *MCPServer
	for _, s := range m.servers {
		if s.Name == req.Server && s.Enabled {
			srv = &s
			break
		}
	}
	m.mu.RUnlock()
	if srv == nil {
		return nil, fmt.Errorf("MCP server %s not found or disabled", req.Server)
	}
	return m.callTool(srv, req.Tool, req.Input)
}

func (m *Manager) fetchTools(srv MCPServer) ([]MCPTool, error) {
	body := map[string]any{"jsonrpc": "2.0", "method": "tools/list", "id": 1}
	data, _ := json.Marshal(body)
	resp, err := http.Post(srv.URL, "application/json", bytes.NewReader(data))
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	respBody, _ := io.ReadAll(resp.Body)
	var result struct {
		Result struct {
			Tools []struct {
				Name        string `json:"name"`
				Description string `json:"description"`
				InputSchema any    `json:"inputSchema"`
			} `json:"tools"`
		} `json:"result"`
	}
	json.Unmarshal(respBody, &result)
	var tools []MCPTool
	for _, t := range result.Result.Tools {
		tools = append(tools, MCPTool{
			Name: t.Name, Description: t.Description,
			ServerName: srv.Name, Schema: t.InputSchema,
		})
	}
	return tools, nil
}

// HandleNotification processes incoming MCP notifications
func (m *Manager) HandleNotification(notification map[string]any) {
	method, _ := notification["method"].(string)
	if method == "" { return }
	// Log notification for now - full notification support requires persistent connection
}

func (m *Manager) callTool(srv *MCPServer, tool, input string) (any, error) {
	var inputJSON any
	json.Unmarshal([]byte(input), &inputJSON)
	body := map[string]any{
		"jsonrpc": "2.0", "method": "tools/call", "id": 1,
		"params": map[string]any{
			"name":      tool,
			"arguments": inputJSON,
		},
	}
	data, _ := json.Marshal(body)
	client := &http.Client{Timeout: 60 * time.Second}
	resp, err := client.Post(srv.URL, "application/json", bytes.NewReader(data))
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	respBody, _ := io.ReadAll(resp.Body)
	var result map[string]any
	json.Unmarshal(respBody, &result)
	return result, nil
}
