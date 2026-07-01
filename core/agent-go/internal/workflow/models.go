package workflow

import "phoneclaw/agent-go/internal/agent"

type Workflow struct {
	ID        string         `json:"id"`
	Name      string         `json:"name"`
	Steps     []WorkflowStep `json:"steps"`
	CreatedAt int64          `json:"createdAt"`
	UpdatedAt int64          `json:"updatedAt"`
	Enabled   bool           `json:"enabled"`
	Schedule  string         `json:"schedule,omitempty"` // cron expression
}

type WorkflowStep struct {
	Tool   agent.ToolKind      `json:"tool"`
	Action string              `json:"action"`
	Input  string              `json:"input"`
	Params map[string]any      `json:"params,omitempty"`
	// Next step conditions
	OnSuccess string `json:"onSuccess,omitempty"` // "continue", "stop", "goto:N"
	OnFailure string `json:"onFailure,omitempty"`
}
