package llm

import (
	"fmt"
	"net/http"
	"strings"
	"time"
)

// Generic OpenAI-compatible provider for all compatible endpoints
// Covers: Deepseek, Qwen, Mistral, OpenRouter, Nvidia, Kimi, Doubao, etc.

type GenericProvider struct {
	name    string
	baseURL string
	apiKey  string
}

func NewGenericProvider(name, baseURL, apiKey string) *GenericProvider {
	return &GenericProvider{name: name, baseURL: baseURL, apiKey: apiKey}
}

func (p *GenericProvider) Name() string { return p.name }

func (p *GenericProvider) Complete(req ChatRequest) (*ChatResponse, error) {
	openAI := &OpenAIProvider{
		baseURL: p.baseURL,
		apiKey:  p.apiKey,
		client:  &http.Client{Timeout: 120 * time.Second},
	}
	req.Model = resolveModel(p.name, req.Model)
	resp, err := openAI.Complete(req)
	if err != nil {
		return nil, fmt.Errorf("%s: %w", p.name, err)
	}
	resp.Model = req.Model
	return resp, nil
}

func (p *GenericProvider) Stream(req ChatRequest) (<-chan StreamEvent, error) {
	openAI := &OpenAIProvider{
		baseURL: p.baseURL,
		apiKey:  p.apiKey,
		client:  &http.Client{Timeout: 120 * time.Second},
	}
	req.Model = resolveModel(p.name, req.Model)
	return openAI.Stream(req)
}

func resolveModel(provider, model string) string {
	prefixes := []string{
		"deepseek/", "qwen/", "mistral/", "openrouter/",
		"nvidia/", "kimi/", "doubao/", "mimo/", "nous/",
	}
	for _, prefix := range prefixes {
		if strings.HasPrefix(model, prefix) {
			return strings.TrimPrefix(model, prefix)
		}
	}
	return model
}

// RegisterAllCompatible registers all OpenAI-compatible providers
func RegisterAllCompatible(r *Router) {
	type cfg struct {
		name    string
		baseURL string
	}
	providers := []cfg{
		{"deepseek", "https://api.deepseek.com/v1"},
		{"qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1"},
		{"mistral", "https://api.mistral.ai/v1"},
		{"openrouter", "https://openrouter.ai/api/v1"},
		{"nvidia", "https://integrate.api.nvidia.com/v1"},
		{"kimi", "https://api.moonshot.cn/v1"},
	}
	for _, p := range providers {
		r.Register(NewGenericProvider(p.name, p.baseURL, ""))
	}
}
