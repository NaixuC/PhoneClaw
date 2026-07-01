package com.phoneclaw.app.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AgentRequest(
    val goal: String,
    val mode: String = "balanced",
    val allowedTools: List<String> = emptyList(),
    val sessionId: String? = null,
)

@Serializable
data class AgentResponse(
    val summary: String = "",
    val confidence: Float = 0f,
    val plan: List<PlanStep> = emptyList(),
    val permissions: List<PermissionAsk> = emptyList(),
    val events: List<Event> = emptyList(),
    val execution: ExecutionResult? = null,
)

@Serializable
data class PlanStep(
    val id: String,
    val title: String,
    val detail: String,
    val status: String,
    val tool: String? = null,
)

@Serializable
data class PermissionAsk(
    val tool: String,
    val reason: String,
    val risk: String,
)

@Serializable
data class Event(
    val label: String,
    val body: String,
    val tone: String = "neutral",
)

@Serializable
data class ExecutionResult(
    val success: Boolean,
    val title: String,
    val detail: String,
    val completedSteps: Int,
)

@Serializable
data class ToolRequest(
    val tool: String,
    val action: String,
    val input: String,
    val params: Map<String, JsonElement>? = null,
    val allowedTools: List<String> = emptyList(),
)

@Serializable
data class ToolResult(
    val success: Boolean = false,
    val title: String = "",
    val output: String = "",
    val risk: String = "low",
    val data: JsonElement? = null,
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMsg>,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val stream: Boolean = false,
)

@Serializable
data class ChatMsg(
    val role: String,
    val content: String,
)

@Serializable
data class ChatResponse(
    val content: String = "",
    val model: String = "",
    val usage: ChatUsage? = null,
)

@Serializable
data class ChatUsage(
    val promptTokens: Int = 0,
    val outputTokens: Int = 0,
)

@Serializable
data class MemoryItem(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val category: String = "",
    val tags: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class Workflow(
    val id: String = "",
    val name: String = "",
    val steps: List<WorkflowStep> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val enabled: Boolean = true,
    val schedule: String? = null,
)

@Serializable
data class WorkflowStep(
    val tool: String,
    val action: String,
    val input: String,
    val params: Map<String, JsonElement>? = null,
)

@Serializable
data class MCPConfig(
    val name: String,
    val url: String,
    val description: String = "",
    val apiKey: String = "",
    val enabled: Boolean = true,
)

@Serializable
data class CharacterCard(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val personality: String = "",
    val greeting: String = "",
    val systemPrompt: String = "",
    val avatar: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class ToolDescriptor(
    val kind: String = "",
    val label: String = "",
    val description: String = "",
    val defaultAction: String = "",
    val defaultInput: String = "",
    val risk: String = "low",
    val runtime: String = "go",
    val actions: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
)
