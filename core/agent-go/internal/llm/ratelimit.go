package llm

import (
	"sync"
	"time"
)

type RateLimiter struct {
	mu        sync.Mutex
	requests  map[string][]time.Time
	limit     int
	window    time.Duration
}

func NewRateLimiter(limit int, window time.Duration) *RateLimiter {
	return &RateLimiter{
		requests: make(map[string][]time.Time),
		limit:    limit,
		window:   window,
	}
}

func (rl *RateLimiter) Allow(key string) bool {
	rl.mu.Lock()
	defer rl.mu.Unlock()
	now := time.Now()
	windowStart := now.Add(-rl.window)

	// Clean old entries
	recent := rl.requests[key][:0]
	for _, t := range rl.requests[key] {
		if t.After(windowStart) {
			recent = append(recent, t)
		}
	}

	if len(recent) >= rl.limit {
		rl.requests[key] = recent
		return false
	}

	rl.requests[key] = append(recent, now)
	return true
}

func (rl *RateLimiter) Remaining(key string) int {
	rl.mu.Lock()
	defer rl.mu.Unlock()
	windowStart := time.Now().Add(-rl.window)
	count := 0
	for _, t := range rl.requests[key] {
		if t.After(windowStart) {
			count++
		}
	}
	if count >= rl.limit {
		return 0
	}
	return rl.limit - count
}

type APIKeyManager struct {
	mu     sync.RWMutex
	keys   map[string][]string // provider -> []apiKey
	idx    map[string]int
}

func NewAPIKeyManager() *APIKeyManager {
	return &APIKeyManager{
		keys: make(map[string][]string),
		idx:  make(map[string]int),
	}
}

func (m *APIKeyManager) AddKey(provider, key string) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.keys[provider] = append(m.keys[provider], key)
}

func (m *APIKeyManager) GetKey(provider string) string {
	m.mu.RLock()
	defer m.mu.RUnlock()
	keys := m.keys[provider]
	if len(keys) == 0 { return "" }
	m.mu.RUnlock()

	m.mu.Lock()
	defer m.mu.Unlock()
	idx := m.idx[provider]
	key := keys[idx%len(keys)]
	m.idx[provider] = idx + 1
	return key
}
