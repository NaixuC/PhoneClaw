package tools

import (
	"os"
	"path/filepath"
	"phoneclaw/agent-go/internal/agent"
	"phoneclaw/agent-go/internal/storage"
	"strings"
)

type SkillManager struct {
	store *storage.Store
}

type SkillPackage struct {
	Name        string `json:"name"`
	Version     string `json:"version"`
	Description string `json:"description"`
	Author      string `json:"author,omitempty"`
	Tools       []SkillTool `json:"tools"`
	Requires    []string `json:"requires,omitempty"`
}

type SkillTool struct {
	Name        string   `json:"name"`
	Description string   `json:"description"`
	Command     string   `json:"command"`
	Args        []string `json:"args,omitempty"`
	Timeout     int      `json:"timeout,omitempty"`
}

func NewSkillManager(s *storage.Store) *SkillManager {
	return &SkillManager{store: s}
}

func (sm *SkillManager) List() ([]SkillPackage, error) {
	keys, err := sm.store.List("skills")
	if err != nil {
		// Try data dir
		skillDir := filepath.Join(sm.store.DataDir(), "skills")
		entries, err := os.ReadDir(skillDir)
		if err != nil { return nil, nil }
		for _, e := range entries {
			if !e.IsDir() && strings.HasSuffix(e.Name(), ".json") {
				keys = append(keys, strings.TrimSuffix(e.Name(), ".json"))
			}
		}
	}
	var skills []SkillPackage
	for _, k := range keys {
		var s SkillPackage
		if sm.store.Read("skills", k, &s) == nil {
			skills = append(skills, s)
		}
	}
	return skills, nil
}

func (sm *SkillManager) Install(pkg SkillPackage) error {
	return sm.store.Write("skills", pkg.Name, pkg)
}

func (sm *SkillManager) Remove(name string) error {
	return sm.store.Delete("skills", name)
}

func (sm *SkillManager) Execute(name, toolName string, input string) agent.ToolResult {
	var pkg SkillPackage
	if err := sm.store.Read("skills", name, &pkg); err != nil {
		return toolErr("技能未找到", err, agent.RiskLow)
	}
	for _, t := range pkg.Tools {
		if t.Name == toolName {
			// Execute command
			return runShell("run", t.Command+" "+input, map[string]any{
				"timeout": float64(t.Timeout),
			})
		}
	}
	return agent.ToolResult{Success: false, Title: "技能工具未找到", Output: toolName, Risk: agent.RiskLow}
}
