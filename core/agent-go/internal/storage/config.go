package storage

import (
	"encoding/json"
	"fmt"
)

type ConfigData struct {
	LLMProvider    string `json:"llmProvider,omitempty"`
	LLMEndpoint   string `json:"llmEndpoint,omitempty"`
	LLMModel      string `json:"llmModel,omitempty"`
	LLMAPIKey     string `json:"llmApiKey,omitempty"`
	Theme         string `json:"theme,omitempty"`
	Language      string `json:"language,omitempty"`
	UbuntuEnabled bool   `json:"ubuntuEnabled,omitempty"`
}

type CharacterCard struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	Description string `json:"description"`
	Personality string `json:"personality"`
	Greeting    string `json:"greeting"`
	SystemPrompt string `json:"systemPrompt"`
	Avatar      string `json:"avatar,omitempty"`
	CreatedAt   int64  `json:"createdAt"`
	UpdatedAt   int64  `json:"updatedAt"`
}

func (s *Store) HandleConfigGet(params json.RawMessage) (any, error) {
	var cfg ConfigData
	s.Read("config", "app", &cfg)
	return cfg, nil
}

func (s *Store) HandleConfigSet(params json.RawMessage) (any, error) {
	var cfg ConfigData
	if err := json.Unmarshal(params, &cfg); err != nil {
		return nil, err
	}
	if err := s.Write("config", "app", cfg); err != nil {
		return nil, err
	}
	return map[string]bool{"saved": true}, nil
}

func (s *Store) HandleCharacterList(params json.RawMessage) (any, error) {
	keys, err := s.List("characters")
	if err != nil {
		return nil, err
	}
	var chars []CharacterCard
	for _, k := range keys {
		var c CharacterCard
		if s.Read("characters", k, &c) == nil {
			chars = append(chars, c)
		}
	}
	if chars == nil {
		chars = make([]CharacterCard, 0)
	}
	return map[string]any{"characters": chars}, nil
}

func (s *Store) HandleCharacterSave(params json.RawMessage) (any, error) {
	var c CharacterCard
	if err := json.Unmarshal(params, &c); err != nil {
		return nil, err
	}
	if c.ID == "" {
		c.ID = "char_" + generateID()
	}
	if err := s.Write("characters", c.ID, c); err != nil {
		return nil, err
	}
	return map[string]any{"id": c.ID, "saved": true}, nil
}

func (s *Store) HandleCharacterDelete(params json.RawMessage) (any, error) {
	var req struct{ ID string `json:"id"` }
	if err := json.Unmarshal(params, &req); err != nil {
		return nil, err
	}
	s.Delete("characters", req.ID)
	return map[string]bool{"deleted": true}, nil
}

var idCounter int64 = 0

func generateID() string {
	idCounter++
	return fmt.Sprintf("%d_%d", idCounter, 0)
}
