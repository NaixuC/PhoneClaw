package llm

import (
	"encoding/json"
	"fmt"
	"strings"
)

type Router struct {
	providers map[string]Provider
}

type Provider interface {
	Complete(req ChatRequest) (*ChatResponse, error)
	Stream(req ChatRequest) (<-chan StreamEvent, error)
	Name() string
}

type ChatRequest struct {
	Model       string        `json:"model"`
	Messages    []ChatMessage `json:"messages"`
	Temperature float32       `json:"temperature,omitempty"`
	MaxTokens   int           `json:"maxTokens,omitempty"`
	Stream      bool          `json:"stream,omitempty"`
	Tools       []ToolDef     `json:"tools,omitempty"`
}

type ChatMessage struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

type StreamFunc func(token string)

type ChatResponse struct {
	Content string `json:"content"`
	Model   string `json:"model"`
	Usage   Usage  `json:"usage,omitempty"`
}

type Usage struct {
	PromptTokens  int `json:"promptTokens"`
	OutputTokens  int `json:"outputTokens"`
}

type StreamEvent struct {
	Type    string `json:"type"` // "delta", "done", "error"
	Content string `json:"content,omitempty"`
	Error   string `json:"error,omitempty"`
}

type ToolDef struct {
	Name        string `json:"name"`
	Description string `json:"description"`
	Schema      any    `json:"schema"`
}

type ModelInfo struct {
	ID       string   `json:"id"`
	Provider string   `json:"provider"`
	Name     string   `json:"name"`
	Features []string `json:"features"` // "streaming", "tools", "vision"
}

func NewRouter() *Router {
	r := &Router{providers: make(map[string]Provider)}
	r.Register(&OpenAIProvider{})
	r.Register(&ClaudeProvider{})
	r.Register(NewGeminiProvider(""))
	r.Register(NewOllamaProvider(""))
		r.Register(NewLocalProvider("llama"))
	r.Register(NewLocalProvider("mnn"))
	RegisterAllCompatible(r)
	return r
}

func (r *Router) Register(p Provider) {
	r.providers[p.Name()] = p
}

func (r *Router) getProvider(model string) Provider {
	model = strings.ToLower(model)
	for name, p := range r.providers {
		if strings.Contains(model, name) {
			return p
		}
	}
	// Default to OpenAI-compatible
	return r.providers["openai"]
}

func (r *Router) HandleComplete(params json.RawMessage) (any, error) {
	var req ChatRequest
	if err := json.Unmarshal(params, &req); err != nil {
		return nil, fmt.Errorf("bad request: %w", err)
	}
	provider := r.getProvider(req.Model)
	if provider == nil {
		return nil, fmt.Errorf("no provider for model: %s", req.Model)
	}
	resp, err := provider.Complete(req)
	if err != nil {
		return nil, fmt.Errorf("completion failed: %w", err)
	}
	return resp, nil
}

func (r *Router) HandleStream(params json.RawMessage) (any, error) {
	type streamReq struct {
		Model       string        `json:"model"`
		Messages    []ChatMessage `json:"messages"`
		Temperature float32       `json:"temperature,omitempty"`
	}
	var req streamReq
	if err := json.Unmarshal(params, &req); err != nil {
		return nil, fmt.Errorf("bad request: %w", err)
	}
	provider := r.getProvider(req.Model)
	if provider == nil {
		return nil, fmt.Errorf("no provider for model: %s", req.Model)
	}
	chatReq := ChatRequest{
		Model: req.Model, Messages: req.Messages,
		Temperature: req.Temperature, Stream: true,
	}
	ch, err := provider.Stream(chatReq)
	if err != nil {
		return nil, err
	}
	var fullContent strings.Builder
	for evt := range ch {
		if evt.Type == "delta" {
			fullContent.WriteString(evt.Content)
		}
	}
	return &ChatResponse{Content: fullContent.String(), Model: req.Model}, nil
}

func (r *Router) HandleListModels(params json.RawMessage) (any, error) {
	models := []ModelInfo{
		{ID: "gpt-4.1", Provider: "openai", Name: "GPT-4.1", Features: []string{"streaming", "tools", "vision"}},
		{ID: "gpt-4.1-mini", Provider: "openai", Name: "GPT-4.1 Mini", Features: []string{"streaming", "tools"}},
		{ID: "claude-sonnet-5", Provider: "anthropic", Name: "Claude Sonnet 5", Features: []string{"streaming", "tools", "vision"}},
		{ID: "claude-haiku-4.5", Provider: "anthropic", Name: "Claude Haiku 4.5", Features: []string{"streaming", "tools"}},
		{ID: "gemini-2.5-pro", Provider: "gemini", Name: "Gemini 2.5 Pro", Features: []string{"streaming", "tools", "vision"}},
		{ID: "deepseek-chat", Provider: "deepseek", Name: "DeepSeek Chat", Features: []string{"streaming", "tools"}},
		{ID: "ollama/llama3", Provider: "ollama", Name: "Llama 3 (本地)", Features: []string{"streaming"}},
		{ID: "llama/local", Provider: "llama", Name: "Llama.cpp (本地)", Features: []string{"streaming"}},
		{ID: "mnn/local", Provider: "mnn", Name: "MNN (本地)", Features: []string{"streaming"}},
	}
	return map[string]any{"models": models}, nil
}
