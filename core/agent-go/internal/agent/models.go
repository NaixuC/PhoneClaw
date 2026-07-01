package agent

// Tool categories - expanded from PhoneClaw's 5 to Operit's full set
type ToolKind string

const (
	// Standard tools
	ToolFilesystem  ToolKind = "filesystem"
	ToolShell       ToolKind = "shell"
	ToolNetwork     ToolKind = "network"
	ToolBrowser     ToolKind = "browser"
	ToolFFmpeg      ToolKind = "ffmpeg"
	ToolCalculator  ToolKind = "calculator"
	ToolDeviceInfo  ToolKind = "device_info"
	ToolIntent      ToolKind = "intent"
	ToolBroadcast   ToolKind = "broadcast"
	ToolBluetooth   ToolKind = "bluetooth"
	ToolMusic       ToolKind = "music"
	ToolSettings    ToolKind = "settings"
	ToolUIAutomation ToolKind = "ui_automation"
	ToolScreenShare ToolKind = "screenshare"
	ToolSystemOps   ToolKind = "systemops"
	ToolAPK         ToolKind = "apk"
	ToolChatManager ToolKind = "chat_manager"
	ToolSkill       ToolKind = "skill"
	ToolMemoryQuery ToolKind = "memory_query"
	ToolTerminal    ToolKind = "terminal"
	ToolVoiceTTS    ToolKind = "voice_tts"
	ToolVoiceSTT    ToolKind = "voice_stt"
	ToolUbuntu      ToolKind = "ubuntu"

	// Access & privilege tools (bridge to Android)
	ToolAccessibility ToolKind = "accessibility"
	ToolRoot          ToolKind = "root"

	// MCP tools
	ToolMCP ToolKind = "mcp"
)

type Mode string

const (
	ModeFast     Mode = "fast"
	ModeBalanced Mode = "balanced"
	ModeDeep     Mode = "deep"
)

type RiskLevel string

const (
	RiskLow    RiskLevel = "low"
	RiskMedium RiskLevel = "medium"
	RiskHigh   RiskLevel = "high"
)

type StepStatus string

const (
	StepReady   StepStatus = "ready"
	StepBlocked StepStatus = "blocked"
	StepDone    StepStatus = "done"
)

type EventTone string

const (
	ToneNeutral EventTone = "neutral"
	ToneGood    EventTone = "good"
	ToneWarn    EventTone = "warn"
)

type RuntimeType string

const (
	RuntimeGo      RuntimeType = "go"
	RuntimeAndroid RuntimeType = "android"
)

// Tool descriptor - metadata for each tool
type ToolDescriptor struct {
	Kind         ToolKind    `json:"kind"`
	Label        string      `json:"label"`
	Description  string      `json:"description"`
	DefaultAct   string      `json:"defaultAction"`
	DefaultInput string      `json:"defaultInput"`
	Risk         RiskLevel   `json:"risk"`
	Runtime      RuntimeType `json:"runtime"`
	Actions      []string    `json:"actions"`
	Categories   []string    `json:"categories"`
}

// Agent request/response types
type AgentRequest struct {
	Goal         string     `json:"goal"`
	Mode         Mode       `json:"mode"`
	AllowedTools []ToolKind `json:"allowedTools"`
	SessionID    string     `json:"sessionId,omitempty"`
}

type AgentResponse struct {
	Summary     string          `json:"summary"`
	Confidence  float32         `json:"confidence"`
	Plan        []PlanStep      `json:"plan"`
	Permissions []PermissionAsk `json:"permissions"`
	Events      []Event         `json:"events"`
	Execution   *Execution      `json:"execution,omitempty"`
}

type PlanStep struct {
	ID     string     `json:"id"`
	Title  string     `json:"title"`
	Detail string     `json:"detail"`
	Status StepStatus `json:"status"`
	Tool   *ToolKind  `json:"tool,omitempty"`
}

type PermissionAsk struct {
	Tool   ToolKind  `json:"tool"`
	Reason string    `json:"reason"`
	Risk   RiskLevel `json:"risk"`
}

type Event struct {
	Label string    `json:"label"`
	Body  string    `json:"body"`
	Tone  EventTone `json:"tone"`
}

type Execution struct {
	Success        bool   `json:"success"`
	Title          string `json:"title"`
	Detail         string `json:"detail"`
	CompletedSteps int    `json:"completedSteps"`
}

// Tool execution types
type ToolRequest struct {
	Tool         ToolKind  `json:"tool"`
	Action       string    `json:"action"`
	Input        string    `json:"input"`
	Params       map[string]any `json:"params,omitempty"`
	AllowedTools []ToolKind `json:"allowedTools"`
}

type ToolResult struct {
	Success bool      `json:"success"`
	Title   string    `json:"title"`
	Output  string    `json:"output"`
	Risk    RiskLevel `json:"risk"`
	Data    any       `json:"data,omitempty"`
}

// Android bridge types
type AndroidAction struct {
	ActionID string                 `json:"actionId"`
	Tool     ToolKind               `json:"tool"`
	Action   string                 `json:"action"`
	Input    string                 `json:"input"`
	Params   map[string]any         `json:"params,omitempty"`
	Metadata map[string]string      `json:"metadata,omitempty"`
}

type AndroidActionResult struct {
	ActionID string   `json:"actionId"`
	Success  bool     `json:"success"`
	Output   string   `json:"output"`
	Error    string   `json:"error,omitempty"`
}
