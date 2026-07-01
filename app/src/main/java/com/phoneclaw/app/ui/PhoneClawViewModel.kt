package com.phoneclaw.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phoneclaw.app.bridge.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

enum class MainTab { Chat, Agent, Tools, Memory, Workflows, Settings }

data class UiState(
    val connected: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val activeTab: MainTab = MainTab.Chat,
    // Chat
    val chatInput: String = "",
    val messages: List<ChatMsg> = listOf(
        ChatMsg("assistant", "Operit NG 已就绪。配置模型供应商后开始对话。")
    ),
    val chatting: Boolean = false,
    val currentModel: String = "gpt-4.1-mini",
    val availableModels: List<String> = emptyList(),
    // Agent
    val goal: String = "分析当前项目结构并创建低风险执行计划",
    val mode: String = "balanced",
    val allowedTools: Set<String> = emptySet(),
    val agentResponse: AgentResponse? = null,
    val planning: Boolean = false,
    val executing: Boolean = false,
    // Tools
    val tools: List<ToolDescriptor> = emptyList(),
    val selectedTool: String = "filesystem",
    val toolAction: String = "list",
    val toolInput: String = ".",
    val toolResult: ToolResult? = null,
    val toolRunning: Boolean = false,
    // Memory
    val memories: List<MemoryItem> = emptyList(),
    val memoryInput: String = "",
    val memoryQuery: String = "",
    // Workflows
    val workflows: List<Workflow> = emptyList(),
    val workflowName: String = "",
    val workflowRunning: Boolean = false,
    // Toolbox navigation
    val toolboxScreen: String? = null, // null=hide, "filemanager"/"logcat"/"sql"
    // System
    val systemInfo: Map<String, JsonElement> = emptyMap(),
    // LLM Config
    val llmEnabled: Boolean = false,
    val llmEndpoint: String = "https://api.openai.com/v1",
    val llmApiKey: String = "",
)

class PhoneClawViewModel(application: Application) : AndroidViewModel(application) {
    private val ctx = application.applicationContext
    private val goAgent = GoAgentBridge(ctx)
    private val androidBridge = AndroidToolBridge(ctx)
    private val localModelBridge = LocalModelBridge()
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        viewModelScope.launch {
            connectToAgent()
        }
    }

    private suspend fun connectToAgent() {
        _state.update { it.copy(loading = true) }
        val ok = goAgent.start()
        if (ok) {
            val tools = goAgent.listTools()
            val models = goAgent.listModels()
            val info = goAgent.systemInfo()
            _state.update {
                it.copy(connected = true, loading = false, tools = tools,
                    availableModels = models, systemInfo = info)
            }
        } else {
            _state.update { it.copy(loading = false, error = "无法连接到Go引擎") }
        }
    }

    // === Tab ===
    fun setTab(tab: MainTab) { _state.update { it.copy(activeTab = tab) } }

    // === Chat ===
    fun setChatInput(v: String) { _state.update { it.copy(chatInput = v) } }
    fun setModel(m: String) { _state.update { it.copy(currentModel = m) } }

    fun sendChat() {
        val input = _state.value.chatInput.trim()
        if (input.isEmpty()) return
        val userMsg = ChatMsg("user", input)
        _state.update {
            it.withMsg(userMsg).copy(chatInput = "", chatting = true, error = null)
        }
        viewModelScope.launch {
            val model = _state.value.currentModel
            if (model.startsWith("llama/") || model.startsWith("mnn/")) {
                val prompt = _state.value.messages.joinToString("\n") { "${it.role}: ${it.content}" }
                val result = localModelBridge.generateAsync(prompt)
                _state.update { it.withMsg(ChatMsg("assistant", result)).copy(chatting = false) }
            } else {
                val history = _state.value.messages
                val resp = goAgent.chatComplete(model, history)
                if (resp != null) {
                    _state.update { it.withMsg(ChatMsg("assistant", resp.content)).copy(chatting = false) }
                } else {
                    _state.update { it.withMsg(ChatMsg("assistant", "(引擎未响应，请检查连接)")).copy(chatting = false) }
                }
            }
        }
    }

    // === Agent ===
    fun setGoal(v: String) { _state.update { it.copy(goal = v) } }
    fun setMode(m: String) { _state.update { it.copy(mode = m) } }
    fun toggleTool(t: String) {
        _state.update { s ->
            val next = s.allowedTools.toMutableSet()
            if (!next.add(t)) next.remove(t)
            s.copy(allowedTools = next)
        }
    }

    fun plan() {
        viewModelScope.launch {
            _state.update { it.copy(planning = true, error = null) }
            val resp = goAgent.plan(
                _state.value.goal,
                _state.value.mode,
                _state.value.allowedTools.toList()
            )
            _state.update { it.copy(agentResponse = resp, planning = false) }
        }
    }

    fun execute() {
        viewModelScope.launch {
            _state.update { it.copy(executing = true, error = null) }
            val resp = goAgent.execute(
                _state.value.goal,
                _state.value.mode,
                _state.value.allowedTools.toList()
            )
            _state.update { it.copy(agentResponse = resp, executing = false) }
        }
    }

    fun approveAll() {
        val tools = _state.value.agentResponse?.permissions?.map { it.tool } ?: return
        _state.update { it.copy(allowedTools = it.allowedTools + tools) }
        plan()
    }

    // === Tools ===
    fun selectTool(t: String) {
        val desc = _state.value.tools.find { it.kind == t }
        _state.update {
            it.copy(selectedTool = t,
                toolAction = desc?.defaultAction ?: "list",
                toolInput = desc?.defaultInput ?: ".")
        }
    }
    fun setToolAction(v: String) { _state.update { it.copy(toolAction = v) } }
    fun setToolInput(v: String) { _state.update { it.copy(toolInput = v) } }

    fun runTool() {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(toolRunning = true, error = null) }
            val result = goAgent.runTool(s.selectedTool, s.toolAction, s.toolInput, s.allowedTools.toList())
            if (result != null) {
                // Check if this is an Android bridge action
                if (result.data?.jsonObject?.get("_android")?.jsonPrimitive?.boolean == true) {
                    val tool = result.data.jsonObject["tool"]?.jsonPrimitive?.content ?: ""
                    val action = result.data.jsonObject["action"]?.jsonPrimitive?.content ?: ""
                    val input = result.data.jsonObject["input"]?.jsonPrimitive?.content ?: ""
                    val androidResult = androidBridge.execute(tool, action, input)
                    _state.update { it.copy(toolResult = androidResult, toolRunning = false) }
                } else {
                    _state.update { it.copy(toolResult = result, toolRunning = false) }
                }
            } else {
                _state.update { it.copy(toolRunning = false, error = "工具执行失败") }
            }
        }
    }

    // === Memory ===
    fun setMemoryInput(v: String) { _state.update { it.copy(memoryInput = v) } }
    fun setMemoryQuery(v: String) { _state.update { it.copy(memoryQuery = v) } }

    fun saveMemory() {
        val text = _state.value.memoryInput.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val item = MemoryItem(
                title = text.take(48).ifBlank { "记忆" },
                body = text,
            )
            goAgent.memorySave(item)
            _state.update { it.copy(memoryInput = "") }
            loadMemories()
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            goAgent.memoryDelete(id)
            loadMemories()
        }
    }

    fun searchMemory() {
        viewModelScope.launch { loadMemories() }
    }

    private suspend fun loadMemories() {
        val items = goAgent.memorySearch(_state.value.memoryQuery)
        _state.update { it.copy(memories = items) }
    }

    // === Workflow ===
    fun setWorkflowName(v: String) { _state.update { it.copy(workflowName = v) } }

    fun saveWorkflow() {
        val s = _state.value
        val wf = Workflow(
            name = s.workflowName.ifBlank { "${s.selectedTool}/${s.toolAction}" },
            steps = listOf(WorkflowStep(s.selectedTool, s.toolAction, s.toolInput)),
        )
        viewModelScope.launch {
            // Save via Go engine
            _state.update { it.copy(workflowName = "") }
        }
    }

    // === Toolbox Navigation ===
    fun openToolbox(screen: String) { _state.update { it.copy(toolboxScreen = screen) } }
    fun closeToolbox() { _state.update { it.copy(toolboxScreen = null) } }

    // === LLM Config ===
    fun goAgentForFileManager(): GoAgentBridge = goAgent
    fun setLlmEnabled(v: Boolean) { _state.update { it.copy(llmEnabled = v) } }
    fun setLlmEndpoint(v: String) { _state.update { it.copy(llmEndpoint = v) } }
    fun setLlmApiKey(v: String) { _state.update { it.copy(llmApiKey = v) } }

    // === Settings ===
    fun setLlmProvider(endpoint: String, apiKey: String) {
        _state.update { it.copy(llmEndpoint = endpoint, llmApiKey = apiKey) }
    }

    private fun UiState.withMsg(msg: ChatMsg): UiState {
        return copy(messages = messages + msg)
    }

    override fun onCleared() {
        goAgent.stop()
        super.onCleared()
    }
}
