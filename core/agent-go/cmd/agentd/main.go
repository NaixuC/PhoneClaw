package main

import (
	"encoding/json"
	"fmt"
	"log"
	"os"
	"os/signal"
	"syscall"

	"phoneclaw/agent-go/internal/server"
	"phoneclaw/agent-go/internal/agent"
	"phoneclaw/agent-go/internal/tools"
	"phoneclaw/agent-go/internal/tools/bridge"
	"phoneclaw/agent-go/internal/llm"
	"phoneclaw/agent-go/internal/memory"
	"phoneclaw/agent-go/internal/workflow"
	"phoneclaw/agent-go/internal/mcp"
	"phoneclaw/agent-go/internal/storage"
)

func main() {
	log.SetFlags(log.Lshortfile | log.Ltime)

	if len(os.Args) < 2 {
		fmt.Fprintln(os.Stderr, "usage: agentd <socket-path> [data-dir]")
		os.Exit(2)
	}

	socketPath := os.Args[1]
	dataDir := "/data/data/com.phoneclaw.app/files/agentd"
	if len(os.Args) >= 3 {
		dataDir = os.Args[2]
	}

	// Initialize storage
	store, err := storage.NewStore(dataDir)
	if err != nil {
		log.Fatalf("failed to init storage: %v", err)
	}

	// Initialize subsystems
	androidBridge := bridge.NewAndroidBridge()
	toolReg := tools.NewRegistry(androidBridge)
	llmRouter := llm.NewRouter()
	memStore := memory.NewStore(store)
	workflowEngine := workflow.NewEngine(store, toolReg)
	mcpManager := mcp.NewManager(store)
	agentRuntime := agent.NewRuntime(toolReg, llmRouter, memStore, workflowEngine, mcpManager, store)

	// Create RPC server
	srv := server.NewServer(socketPath)

	// Register all handlers
	srv.Handle("agent.plan", func(p json.RawMessage) (any, error) {
		return agentRuntime.HandlePlan(p)
	})
	srv.Handle("agent.execute", func(p json.RawMessage) (any, error) {
		return agentRuntime.HandleExecute(p)
	})

	srv.Handle("chat.complete", func(p json.RawMessage) (any, error) {
		return llmRouter.HandleComplete(p)
	})
	srv.Handle("chat.stream", func(p json.RawMessage) (any, error) {
		return llmRouter.HandleStream(p)
	})
	srv.Handle("chat.models", func(p json.RawMessage) (any, error) {
		return llmRouter.HandleListModels(p)
	})

	srv.Handle("tools.list", func(p json.RawMessage) (any, error) {
		return toolReg.HandleList(p)
	})
	srv.Handle("tools.run", func(p json.RawMessage) (any, error) {
		return toolReg.HandleRun(p)
	})
	srv.Handle("tools.run_android", func(p json.RawMessage) (any, error) {
		return agentRuntime.HandleAndroidTool(p)
	})
	srv.Handle("tools.android_result", func(p json.RawMessage) (any, error) {
		return agentRuntime.HandleAndroidResult(p)
	})

	srv.Handle("memory.search", func(p json.RawMessage) (any, error) { return memStore.HandleSearch(p) })
	srv.Handle("memory.save", func(p json.RawMessage) (any, error) { return memStore.HandleSave(p) })
	srv.Handle("memory.delete", func(p json.RawMessage) (any, error) { return memStore.HandleDelete(p) })
	srv.Handle("memory.list", func(p json.RawMessage) (any, error) { return memStore.HandleList(p) })

	srv.Handle("workflow.list", func(p json.RawMessage) (any, error) { return workflowEngine.HandleList(p) })
	srv.Handle("workflow.save", func(p json.RawMessage) (any, error) { return workflowEngine.HandleSave(p) })
	srv.Handle("workflow.delete", func(p json.RawMessage) (any, error) { return workflowEngine.HandleDelete(p) })
	srv.Handle("workflow.run", func(p json.RawMessage) (any, error) { return workflowEngine.HandleRun(p) })

	srv.Handle("mcp.list", func(p json.RawMessage) (any, error) { return mcpManager.HandleList(p) })
	srv.Handle("mcp.add", func(p json.RawMessage) (any, error) { return mcpManager.HandleAdd(p) })
	srv.Handle("mcp.remove", func(p json.RawMessage) (any, error) { return mcpManager.HandleRemove(p) })
	srv.Handle("mcp.invoke", func(p json.RawMessage) (any, error) { return mcpManager.HandleInvoke(p) })

	srv.Handle("config.get", func(p json.RawMessage) (any, error) { return store.HandleConfigGet(p) })
	srv.Handle("config.set", func(p json.RawMessage) (any, error) { return store.HandleConfigSet(p) })
	srv.Handle("system.info", func(p json.RawMessage) (any, error) { return agentRuntime.HandleSystemInfo(p) })

	srv.Handle("character.list", func(p json.RawMessage) (any, error) { return store.HandleCharacterList(p) })
	srv.Handle("character.save", func(p json.RawMessage) (any, error) { return store.HandleCharacterSave(p) })
	srv.Handle("character.delete", func(p json.RawMessage) (any, error) { return store.HandleCharacterDelete(p) })

	// Start server
	if err := srv.Start(); err != nil {
		log.Fatalf("failed to start server: %v", err)
	}
	defer srv.Stop()

	log.Printf("agentd running on %s (data: %s)", socketPath, dataDir)

	// Wait for signal
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	<-sigCh
	log.Println("shutting down")
}
