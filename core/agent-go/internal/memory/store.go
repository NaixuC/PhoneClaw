package memory

import (
	"encoding/json"
	"fmt"
	"phoneclaw/agent-go/internal/storage"
	"strings"
	"sync"
	"time"
)

type MemoryItem struct {
	ID        string   `json:"id"`
	Title     string   `json:"title"`
	Body      string   `json:"body"`
	Category  string   `json:"category,omitempty"`
	Tags      []string `json:"tags,omitempty"`
	CreatedAt int64    `json:"createdAt"`
	UpdatedAt int64    `json:"updatedAt"`
}

type Store struct {
	store *storage.Store
	items []MemoryItem
	mu    sync.RWMutex
}

func NewStore(s *storage.Store) *Store {
	m := &Store{store: s}
	m.load()
	return m
}

func (m *Store) load() {
	m.mu.Lock()
	defer m.mu.Unlock()
	var data struct { Items []MemoryItem `json:"items"` }
	m.store.Read("memory", "index", &data)
	m.items = data.Items
	if m.items == nil { m.items = make([]MemoryItem, 0) }
}

func (m *Store) save() {
	m.store.Write("memory", "index", map[string]any{"items": m.items})
}

func (m *Store) HandleSearch(params json.RawMessage) (any, error) {
	var req struct {
		Query    string   `json:"query"`
		Category string   `json:"category,omitempty"`
		Tags     []string `json:"tags,omitempty"`
		Limit    int      `json:"limit,omitempty"`
	}
	if err := json.Unmarshal(params, &req); err != nil {
		return nil, fmt.Errorf("bad request: %w", err)
	}
	m.mu.RLock()
	defer m.mu.RUnlock()

	query := strings.ToLower(req.Query)
	var results []MemoryItem

	for _, item := range m.items {
		if req.Category != "" && item.Category != req.Category { continue }
		if query != "" {
			if !strings.Contains(strings.ToLower(item.Title+" "+item.Body), query) { continue }
		}
		if len(req.Tags) > 0 {
			hasTag := false
			for _, t := range req.Tags {
				for _, it := range item.Tags { if t == it { hasTag = true; break } }
				if hasTag { break }
			}
			if !hasTag { continue }
		}
		results = append(results, item)
	}
	if req.Limit > 0 && len(results) > req.Limit { results = results[:req.Limit] }
	return map[string]any{"items": results, "count": len(results)}, nil
}

func (m *Store) HandleSave(params json.RawMessage) (any, error) {
	var item MemoryItem
	if err := json.Unmarshal(params, &item); err != nil {
		return nil, fmt.Errorf("bad request: %w", err)
	}
	now := time.Now().UnixMilli()
	if item.ID == "" { item.ID = fmt.Sprintf("mem_%d", now) }
	item.CreatedAt = now
	item.UpdatedAt = now

	m.mu.Lock()
	m.items = append(m.items, item)
	m.mu.Unlock()
	m.save()
	return map[string]any{"id": item.ID, "saved": true}, nil
}

func (m *Store) HandleDelete(params json.RawMessage) (any, error) {
	var req struct { ID string `json:"id"` }
	if err := json.Unmarshal(params, &req); err != nil { return nil, fmt.Errorf("bad request: %w", err) }
	m.mu.Lock()
	defer m.mu.Unlock()
	for i, item := range m.items {
		if item.ID == req.ID {
			m.items = append(m.items[:i], m.items[i+1:]...)
			m.save()
			return map[string]any{"deleted": true}, nil
		}
	}
	return map[string]any{"deleted": false}, nil
}

func (m *Store) HandleList(params json.RawMessage) (any, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return map[string]any{"items": m.items, "count": len(m.items)}, nil
}
