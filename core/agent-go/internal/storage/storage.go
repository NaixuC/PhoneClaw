package storage

import (
	"encoding/json"
	"os"
	"path/filepath"
	"sync"
)

type Store struct {
	dataDir string
	mu      sync.RWMutex
	cache   map[string][]byte
}

func NewStore(dataDir string) (*Store, error) {
	dirs := []string{dataDir, filepath.Join(dataDir, "memory"), filepath.Join(dataDir, "workflows"),
		filepath.Join(dataDir, "config"), filepath.Join(dataDir, "mcp"), filepath.Join(dataDir, "characters")}
	for _, d := range dirs {
		if err := os.MkdirAll(d, 0755); err != nil {
			return nil, err
		}
	}
	return &Store{
		dataDir: dataDir,
		cache:   make(map[string][]byte),
	}, nil
}

func (s *Store) DataDir() string { return s.dataDir }

func (s *Store) Read(group, key string, v any) error {
	s.mu.RLock()
	cached, ok := s.cache[group+"/"+key]
	s.mu.RUnlock()
	if ok {
		return json.Unmarshal(cached, v)
	}
	data, err := os.ReadFile(filepath.Join(s.dataDir, group, key+".json"))
	if err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return err
	}
	s.mu.Lock()
	s.cache[group+"/"+key] = data
	s.mu.Unlock()
	return json.Unmarshal(data, v)
}

func (s *Store) Write(group, key string, v any) error {
	data, err := json.MarshalIndent(v, "", " ")
	if err != nil {
		return err
	}
	path := filepath.Join(s.dataDir, group, key+".json")
	if err := os.WriteFile(path, data, 0644); err != nil {
		return err
	}
	s.mu.Lock()
	s.cache[group+"/"+key] = data
	s.mu.Unlock()
	return nil
}

func (s *Store) Delete(group, key string) error {
	path := filepath.Join(s.dataDir, group, key+".json")
	os.Remove(path)
	s.mu.Lock()
	delete(s.cache, group+"/"+key)
	s.mu.Unlock()
	return nil
}

func (s *Store) List(group string) ([]string, error) {
	dir := filepath.Join(s.dataDir, group)
	entries, err := os.ReadDir(dir)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, nil
		}
		return nil, err
	}
	var keys []string
	for _, e := range entries {
		if !e.IsDir() && filepath.Ext(e.Name()) == ".json" {
			keys = append(keys, e.Name()[:len(e.Name())-5])
		}
	}
	return keys, nil
}
