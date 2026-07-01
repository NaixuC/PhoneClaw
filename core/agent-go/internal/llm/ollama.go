package llm

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

type OllamaProvider struct {
	baseURL string
	client  *http.Client
}

func (p *OllamaProvider) Name() string { return "ollama" }

func NewOllamaProvider(baseURL string) *OllamaProvider {
	if baseURL == "" { baseURL = "http://localhost:11434" }
	return &OllamaProvider{
		baseURL: strings.TrimRight(baseURL, "/"),
		client:  &http.Client{Timeout: 300 * time.Second},
	}
}

func (p *OllamaProvider) Complete(req ChatRequest) (*ChatResponse, error) {
	model := strings.TrimPrefix(req.Model, "ollama/")
	body := map[string]any{
		"model": model,
		"messages": req.Messages,
		"stream": false,
		"options": map[string]any{
			"temperature": req.Temperature,
		},
	}
	data, _ := json.Marshal(body)
	resp, err := p.client.Post(p.baseURL+"/api/chat", "application/json", bytes.NewReader(data))
	if err != nil { return nil, err }
	defer resp.Body.Close()
	respBody, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("Ollama error %d: %s", resp.StatusCode, string(respBody))
	}
	var ollamaResp struct {
		Message struct {
			Content string `json:"content"`
		} `json:"message"`
	}
	if err := json.Unmarshal(respBody, &ollamaResp); err != nil {
		return nil, err
	}
	return &ChatResponse{Content: ollamaResp.Message.Content, Model: req.Model}, nil
}

func (p *OllamaProvider) Stream(req ChatRequest) (<-chan StreamEvent, error) {
	ch := make(chan StreamEvent, 100)
	go func() {
		defer close(ch)
		resp, err := p.Complete(req)
		if err != nil { ch <- StreamEvent{Type: "error", Error: err.Error()}; ch <- StreamEvent{Type: "done"}; return }
		ch <- StreamEvent{Type: "delta", Content: resp.Content}
		ch <- StreamEvent{Type: "done"}
	}()
	return ch, nil
}

func (p *OllamaProvider) ListModels() ([]string, error) {
	resp, err := p.client.Get(p.baseURL + "/api/tags")
	if err != nil { return nil, err }
	defer resp.Body.Close()
	var listResp struct {
		Models []struct {
			Name string `json:"name"`
		} `json:"models"`
	}
	body, _ := io.ReadAll(resp.Body)
	if err := json.Unmarshal(body, &listResp); err != nil { return nil, err }
	var models []string
	for _, m := range listResp.Models { models = append(models, "ollama/"+m.Name) }
	return models, nil
}
