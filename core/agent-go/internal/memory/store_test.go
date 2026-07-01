package memory

import (
	"encoding/json"
	"phoneclaw/agent-go/internal/storage"
	"testing"
)

func TestMemorySaveAndSearch(t *testing.T) {
	store, _ := storage.NewStore(t.TempDir())
	mem := NewStore(store)

	// Save a memory
	saveReq := json.RawMessage(`{"id":"test1","title":"Test Memory","body":"This is a test memory about Golang programming"}`)
	result, err := mem.HandleSave(saveReq)
	if err != nil {
		t.Fatalf("Save failed: %v", err)
	}
	resp := result.(map[string]any)
	if resp["saved"] != true {
		t.Fatal("expected saved=true")
	}
	if resp["id"].(string) == "" {
		t.Fatal("expected non-empty id")
	}

	// Search for it
	searchReq := json.RawMessage(`{"query":"Golang","limit":5}`)
	searchResult, err := mem.HandleSearch(searchReq)
	if err != nil {
		t.Fatalf("Search failed: %v", err)
	}
	searchResp := searchResult.(map[string]any)
	count := searchResp["count"].(int)
	if count < 1 {
		t.Fatal("expected at least 1 result for Golang search")
	}
}

func TestMemorySaveAndDelete(t *testing.T) {
	store, _ := storage.NewStore(t.TempDir())
	mem := NewStore(store)

	saveReq := json.RawMessage(`{"id":"del1","title":"Delete Me","body":"This will be deleted"}`)
	saveResult, _ := mem.HandleSave(saveReq)
	saved := saveResult.(map[string]any)
	id := saved["id"].(string)

	// Delete it
	deleteReq := json.RawMessage(`{"id":"` + id + `"}`)
	deleteResult, err := mem.HandleDelete(deleteReq)
	if err != nil {
		t.Fatalf("Delete failed: %v", err)
	}
	if deleteResult.(map[string]any)["deleted"] != true {
		t.Fatal("expected deleted=true")
	}
}

func TestMemoryList(t *testing.T) {
	store, _ := storage.NewStore(t.TempDir())
	mem := NewStore(store)

	// Save two items
	mem.HandleSave(json.RawMessage(`{"id":"a","title":"A","body":"first item"}`))
	mem.HandleSave(json.RawMessage(`{"id":"b","title":"B","body":"second item"}`))

	result, err := mem.HandleList(json.RawMessage(`{}`))
	if err != nil {
		t.Fatalf("List failed: %v", err)
	}
	list := result.(map[string]any)
	if list["count"].(int) < 2 {
		t.Fatalf("expected at least 2 items, got %d", list["count"].(int))
	}
}

func TestVectorSearch(t *testing.T) {
	vs := NewVectorStore()
	vs.Add("doc1", "Go language programming tutorial", nil)
	vs.Add("doc2", "Python machine learning guide", nil)
	vs.Add("doc3", "Golang concurrency patterns", nil)

	results := vs.Search("Go programming", 2)
	if len(results) == 0 {
		t.Fatal("expected at least 1 result")
	}
	// doc1 and doc3 should match better than doc2
	found := false
	for _, r := range results {
		if r.ID == "doc1" || r.ID == "doc3" {
			found = true
			break
		}
	}
	if !found {
		t.Fatal("expected doc1 or doc3 in results")
	}
}
