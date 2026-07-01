package llm

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
)

type ClaudeProvider struct {
	apiKey string
	client *http.Client
}

func (p *ClaudeProvider) Name() string { return "anthropic" }

func (p *ClaudeProvider) Complete(req ChatRequest) (*ChatResponse, error) {
	// Convert messages to Claude format
	var systemMsg string
	var msgs []map[string]any
	for _, m := range req.Messages {
		if m.Role == "system" {
			systemMsg = m.Content
			continue
		}
		role := m.Role
		if role == "assistant" { role = "assistant" }
		msgs = append(msgs, map[string]any{"role": role, "content": m.Content})
	}
	body := map[string]any{
		"model":      req.Model,
		"messages":   msgs,
		"max_tokens": 4096,
	}
	if systemMsg != "" {
		body["system"] = systemMsg
	}
	if req.Temperature > 0 {
		body["temperature"] = req.Temperature
	}

	data, _ := json.Marshal(body)
	if p.apiKey == "" {
		return nil, fmt.Errorf("Claude API key 未配置")
	}
	httpReq, _ := http.NewRequest("POST", "https://api.anthropic.com/v1/messages", bytes.NewReader(data))
	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("x-api-key", p.apiKey)
	httpReq.Header.Set("anthropic-version", "2023-06-01")

	resp, err := p.client.Do(httpReq)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	respBody, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("Claude API error %d: %s", resp.StatusCode, string(respBody))
	}

	var claudeResp struct {
		Content []struct {
			Text string `json:"text"`
		} `json:"content"`
		Usage struct {
			InputTokens  int `json:"input_tokens"`
			OutputTokens int `json:"output_tokens"`
		} `json:"usage"`
	}
	if err := json.Unmarshal(respBody, &claudeResp); err != nil {
		return nil, err
	}

	var fullText string
	for _, c := range claudeResp.Content {
		fullText += c.Text
	}
	return &ChatResponse{
		Content: fullText,
		Model:   req.Model,
		Usage:   Usage{PromptTokens: claudeResp.Usage.InputTokens, OutputTokens: claudeResp.Usage.OutputTokens},
	}, nil
}

func (p *ClaudeProvider) Stream(req ChatRequest) (<-chan StreamEvent, error) {
	ch := make(chan StreamEvent, 100)
	go func() {
		defer close(ch)
		resp, err := p.Complete(req)
		if err != nil {
			ch <- StreamEvent{Type: "error", Error: err.Error()}
			ch <- StreamEvent{Type: "done"}
			return
		}
		ch <- StreamEvent{Type: "delta", Content: resp.Content}
		ch <- StreamEvent{Type: "done"}
	}()
	return ch, nil
}
