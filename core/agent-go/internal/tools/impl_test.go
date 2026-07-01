package tools

import (
	"os"
	"path/filepath"
	"testing"
)

func TestRunFilesystem_List(t *testing.T) {
	// Create temp dir with test files
	dir := t.TempDir()
	os.WriteFile(filepath.Join(dir, "test.txt"), []byte("hello"), 0644)
	os.Mkdir(filepath.Join(dir, "subdir"), 0755)

	result := runFilesystem("list", dir, nil)
	if !result.Success {
		t.Fatalf("expected success, got: %s", result.Output)
	}
	if !contains(result.Output, "test.txt") || !contains(result.Output, "subdir/") {
		t.Fatalf("expected test.txt and subdir/, got: %s", result.Output)
	}
}

func TestRunFilesystem_Read(t *testing.T) {
	dir := t.TempDir()
	os.WriteFile(filepath.Join(dir, "data.txt"), []byte("hello world"), 0644)
	result := runFilesystem("read", filepath.Join(dir, "data.txt"), nil)
	if !result.Success || result.Output != "hello world" {
		t.Fatalf("expected 'hello world', got: %s", result.Output)
	}
}

func TestRunFilesystem_Write(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "new.txt")
	result := runFilesystem("write", path, map[string]any{"content": "test data"})
	if !result.Success {
		t.Fatalf("write failed: %s", result.Output)
	}
	data, _ := os.ReadFile(path)
	if string(data) != "test data" {
		t.Fatalf("expected 'test data', got '%s'", string(data))
	}
}

func TestRunShell(t *testing.T) {
	result := runShell("run", "echo hello", nil)
	if !result.Success {
		t.Fatalf("shell failed: %s", result.Output)
	}
	if result.Output != "hello\n" && result.Output != "hello" {
		t.Fatalf("expected 'hello', got '%s'", result.Output)
	}
}

func TestRunShell_Timeout(t *testing.T) {
	result := runShell("run", "sleep 10", map[string]any{"timeout": float64(1)})
	if result.Success {
		t.Fatal("expected timeout")
	}
}

func TestRunNetwork_InvalidURL(t *testing.T) {
	result := runNetwork("get", "not-a-url", nil)
	if result.Success {
		t.Fatal("expected failure for invalid URL")
	}
}

func TestRunCalc_Basic(t *testing.T) {
	tests := []struct{ input, expected string }{
		{"1+2", "3"},
		{"10-3", "7"},
		{"4*5", "20"},
		{"20/4", "5"},
		{"(1+2)*3", "9"},
	}
	for _, tc := range tests {
		result := runCalc("eval", tc.input, nil)
		if !result.Success {
			t.Fatalf("calc %s failed: %s", tc.input, result.Output)
		}
		if !contains(result.Output, tc.expected) {
			t.Fatalf("calc %s expected %s, got %s", tc.input, tc.expected, result.Output)
		}
	}
}

func TestRunCalc_Scientific(t *testing.T) {
	result := runCalc("eval", "sqrt(16)", nil)
	if !result.Success {
		t.Fatalf("sqrt failed: %s", result.Output)
	}
	if !contains(result.Output, "4") {
		t.Fatalf("sqrt(16) expected 4, got %s", result.Output)
	}
}

func TestRunDevice(t *testing.T) {
	result := runDevice("summary", "", nil)
	if !result.Success {
		t.Fatalf("device info failed: %s", result.Output)
	}
	if !contains(result.Output, "CPU") {
		t.Fatalf("expected CPU info, got: %s", result.Output)
	}
}

func TestRunDevice_Env(t *testing.T) {
	result := runDevice("env", "", nil)
	if !result.Success {
		t.Fatalf("env failed: %s", result.Output)
	}
	if !contains(result.Output, "HOME=") {
		t.Fatalf("expected HOME env, got: %s", result.Output)
	}
}

func TestTerminal_CreateSession(t *testing.T) {
	// Reset terminal state
	termMu.Lock()
	termSessions = make(map[string]*terminalSession)
	termCounter = 0
	termMu.Unlock()

	result := termCreateSession("test-session")
	if !result.Success {
		t.Fatalf("create session failed: %s", result.Output)
	}
	if !contains(result.Output, "test-session") {
		t.Fatalf("expected session name, got: %s", result.Output)
	}
}

func TestTerminal_Exec(t *testing.T) {
	termMu.Lock()
	termSessions = make(map[string]*terminalSession)
	termCounter = 0
	termMu.Unlock()

	result := termExec("echo hello terminal")
	if !result.Success {
		t.Fatalf("term exec failed: %s", result.Output)
	}
	if !contains(result.Output, "hello") {
		t.Fatalf("expected hello, got: %s", result.Output)
	}
}

func TestUbuntu_Status(t *testing.T) {
	result := runUbuntu("status", "", nil)
	if !result.Success {
		t.Fatalf("ubuntu status should not fail: %s", result.Output)
	}
	if !contains(result.Output, "未安装") {
		t.Fatalf("expected not installed on test env: %s", result.Output)
	}
}

func TestEvalMath(t *testing.T) {
	tests := []struct{ input string; expected float64 }{
		{"1+2", 3},
		{"2*3+4", 10},
		{"(1+2)*3", 9},
		{"10/2", 5},
		{"10-3", 7},
	}
	for _, tc := range tests {
		result, err := evalMath(tc.input)
		if err != nil {
			t.Fatalf("eval %s error: %v", tc.input, err)
		}
		if result != tc.expected {
			t.Fatalf("eval %s expected %f, got %f", tc.input, tc.expected, result)
		}
	}
}

func contains(s, substr string) bool {
	return len(s) >= len(substr) && containsStr(s, substr)
}

func containsStr(s, substr string) bool {
	for i := 0; i <= len(s)-len(substr); i++ {
		if s[i:i+len(substr)] == substr {
			return true
		}
	}
	return false
}
