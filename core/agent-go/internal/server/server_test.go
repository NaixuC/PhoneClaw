package server

import (
	"encoding/json"
	"net"
	"os"
	"testing"
)

func TestServerStartStop(t *testing.T) {
	socketPath := os.TempDir() + "/test_server.sock"
	defer os.Remove(socketPath)

	srv := NewServer(socketPath)
	err := srv.Start()
	if err != nil {
		t.Fatalf("Start failed: %v", err)
	}

	// Verify socket was created
	if _, err := os.Stat(socketPath); err != nil {
		t.Fatalf("socket not created: %v", err)
	}

	srv.Stop()
}

func TestServerHandleRequest(t *testing.T) {
	socketPath := os.TempDir() + "/test_handler.sock"
	defer os.Remove(socketPath)

	srv := NewServer(socketPath)
	srv.Handle("test.echo", func(params json.RawMessage) (any, error) {
		var msg map[string]any
		json.Unmarshal(params, &msg)
		return msg, nil
	})

	if err := srv.Start(); err != nil {
		t.Fatalf("Start failed: %v", err)
	}
	defer srv.Stop()

	// Connect and send a request
	conn, err := net.Dial("unix", socketPath)
	if err != nil {
		t.Fatalf("Dial failed: %v", err)
	}
	defer conn.Close()

	req := `{"jsonrpc":"2.0","id":1,"method":"test.echo","params":{"key":"value"}}` + "\n"
	conn.Write([]byte(req))

	buf := make([]byte, 4096)
	n, err := conn.Read(buf)
	if err != nil {
		t.Fatalf("Read failed: %v", err)
	}

	var resp struct {
		JSONRPC string          `json:"jsonrpc"`
		ID      int             `json:"id"`
		Result  json.RawMessage `json:"result"`
		Error   *struct{ Message string } `json:"error"`
	}
	if err := json.Unmarshal(buf[:n], &resp); err != nil {
		t.Fatalf("Unmarshal response failed: %v", err)
	}
	if resp.Error != nil {
		t.Fatalf("got error: %s", resp.Error.Message)
	}
	if resp.ID != 1 {
		t.Fatalf("expected id 1, got %d", resp.ID)
	}
	var result map[string]any
	json.Unmarshal(resp.Result, &result)
	if result["key"] != "value" {
		t.Fatalf("expected key=value, got %v", result)
	}
}

func TestServerUnknownMethod(t *testing.T) {
	socketPath := os.TempDir() + "/test_unknown.sock"
	defer os.Remove(socketPath)

	srv := NewServer(socketPath)
	srv.Start()
	defer srv.Stop()

	conn, err := net.Dial("unix", socketPath)
	if err != nil {
		t.Fatalf("Dial failed: %v", err)
	}
	defer conn.Close()

	req := `{"jsonrpc":"2.0","id":1,"method":"nonexistent","params":{}}` + "\n"
	conn.Write([]byte(req))

	buf := make([]byte, 4096)
	n, _ := conn.Read(buf)

	var resp struct {
		Error *struct{ Code int `json:"code"` } `json:"error"`
	}
	json.Unmarshal(buf[:n], &resp)
	if resp.Error == nil {
		t.Fatal("expected error for unknown method")
	}
}
