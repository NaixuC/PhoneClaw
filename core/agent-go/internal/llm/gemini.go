package llm

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

type GeminiProvider struct {
	apiKey string
	client *http.Client
}

func (p *GeminiProvider) Name() string { return "gemini" }

func NewGeminiProvider(apiKey string) *GeminiProvider {
	return &GeminiProvider{
		apiKey: apiKey,
		client: &http.Client{Timeout: 120 * time.Second},
	}
}

func (p *GeminiProvider) Complete(req ChatRequest) (*ChatResponse, error) {
	var contents []map[string]any
	for _, m := range req.Messages {
		if m.Role == "system" { continue }
		contents = append(contents, map[string]any{
			"role": m.Role,
			"parts": []map[string]any{{"text": m.Content}},
		})
	}
	body := map[string]any{
		"contents": contents,
		"generationConfig": map[string]any{
			"temperature": req.Temperature,
			"maxOutputTokens": 4096,
		},
	}
	data, _ := json.Marshal(body)
	url := fmt.Sprintf("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s", req.Model, p.apiKey)
	resp, err := p.client.Post(url, "application/json", bytes.NewReader(data))
	if err != nil { return nil, err }
	defer resp.Body.Close()
	respBody, _ := io.ReadAll(resp.Body)
	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("Gemini error %d: %s", resp.StatusCode, string(respBody))
	}
	var geminiResp struct {
		Candidates []struct {
			Content struct {
				Parts []struct {
					Text string `json:"text"`
				} `json:"parts"`
			} `json:"content"`
		} `json:"candidates"`
	}
	if err := json.Unmarshal(respBody, &geminiResp); err != nil {
		return nil, err
	}
	if len(geminiResp.Candidates) == 0 {
		return nil, fmt.Errorf("no candidates")
	}
	text := ""
	for _, p := range geminiResp.Candidates[0].Content.Parts {
		text += p.Text
	}
	return &ChatResponse{Content: text, Model: req.Model}, nil
}

func (p *GeminiProvider) Stream(req ChatRequest) (<-chan StreamEvent, error) {
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
