package memory

import (
	"math"
	"sort"
	"strings"
	"sync"
	"unicode"
)

// Simple TF-IDF vector search for memory
// No external dependencies, works in pure Go for Android cross-compilation

type VectorStore struct {
	mu     sync.RWMutex
	items  []VectorItem
	nextID int
}

type VectorItem struct {
	ID       string
	Text     string
	Tokens   map[string]float64
	Metadata map[string]string
}

func NewVectorStore() *VectorStore {
	return &VectorStore{
		items: make([]VectorItem, 0),
	}
}

func (vs *VectorStore) Add(id, text string, metadata map[string]string) {
	vs.mu.Lock()
	defer vs.mu.Unlock()
	tokens := tokenize(text)
	// TF computation
	tf := make(map[string]float64)
	total := float64(len(tokens))
	for _, t := range tokens {
		tf[t]++
	}
	for k, v := range tf {
		tf[k] = v / total
	}
	vs.items = append(vs.items, VectorItem{
		ID: id, Text: text, Tokens: tf, Metadata: metadata,
	})
}

func (vs *VectorStore) Remove(id string) {
	vs.mu.Lock()
	defer vs.mu.Unlock()
	for i, item := range vs.items {
		if item.ID == id {
			vs.items = append(vs.items[:i], vs.items[i+1:]...)
			return
		}
	}
}

func (vs *VectorStore) Search(query string, limit int) []VectorItem {
	vs.mu.RLock()
	defer vs.mu.RUnlock()
	if limit <= 0 { limit = 10 }

	queryTokens := tokenize(query)
	if len(queryTokens) == 0 { return nil }

	// Compute query TF
	qTF := make(map[string]float64)
	for _, t := range queryTokens { qTF[t]++ }
	total := float64(len(queryTokens))
	for k, v := range qTF { qTF[k] = v / total }

	// IDF computation
	idf := make(map[string]float64)
	n := float64(len(vs.items))
	for _, item := range vs.items {
		for token := range item.Tokens {
			idf[token]++
		}
	}
	for token, count := range idf {
		idf[token] = math.Log((1 + n) / (1 + count))
	}

	type scored struct {
		item  VectorItem
		score float64
	}
	var results []scored

	for _, item := range vs.items {
		var dotProduct, qMag, dMag float64
		for token, dWeight := range item.Tokens {
			dWeighted := dWeight * idf[token]
			dMag += dWeighted * dWeighted
		}
		for token, qWeight := range qTF {
			qWeighted := qWeight * idf[token]
			dWeighted := item.Tokens[token] * idf[token]
			dotProduct += qWeighted * dWeighted
			qMag += qWeighted * qWeighted
		}
		denom := math.Sqrt(qMag) * math.Sqrt(dMag)
		score := float64(0)
		if denom > 0 { score = dotProduct / denom }
		if score > 0 {
			results = append(results, scored{item, score})
		}
	}

	sort.Slice(results, func(i, j int) bool {
		return results[i].score > results[j].score
	})

	if len(results) > limit {
		results = results[:limit]
	}

	var out []VectorItem
	for _, r := range results {
		out = append(out, r.item)
	}
	return out
}

func tokenize(text string) []string {
	text = strings.ToLower(text)
	var tokens []string
	var current []rune
	for _, r := range text {
		if unicode.IsLetter(r) || unicode.IsDigit(r) {
			current = append(current, r)
		} else {
			if len(current) >= 2 {
				tokens = append(tokens, string(current))
			}
			current = nil
		}
	}
	if len(current) >= 2 {
		tokens = append(tokens, string(current))
	}
	return tokens
}
