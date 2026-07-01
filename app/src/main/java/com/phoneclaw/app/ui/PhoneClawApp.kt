package com.phoneclaw.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phoneclaw.app.ui.theme.*
import kotlinx.serialization.json.*

@Composable
fun PhoneClawApp(viewModel: PhoneClawViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (!state.connected && state.loading) {
            LoadingScreen()
        } else {
            MainContent(state, viewModel)
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Fern)
            Spacer(Modifier.height(16.dp))
            Text("正在启动 Go 引擎...", color = MutedInk)
        }
    }
}

@Composable
private fun MainContent(state: UiState, vm: PhoneClawViewModel) {
    Column(Modifier.fillMaxSize()) {
        // Top bar
        TopBar(state, vm)
        // Tab strip or toolbox back button
        if (state.toolboxScreen != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                TextButton(onClick = vm::closeToolbox) {
                    Icon(Icons.Rounded.ArrowBack, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("返回工具箱")
                }
            }
        } else {
            TabStrip(state, vm)
        }
        HorizontalDivider(color = Line)
        // Content area
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state.toolboxScreen) {
                "filemanager" -> com.phoneclaw.app.ui.screens.FileManagerScreen(vm.goAgentForFileManager())
                "logcat" -> com.phoneclaw.app.ui.screens.LogcatScreen()
                "sql" -> com.phoneclaw.app.ui.screens.SQLViewerScreen()
                else -> when (state.activeTab) {
                    MainTab.Chat -> ChatScreen(state, vm)
                    MainTab.Agent -> AgentScreen(state, vm)
                    MainTab.Tools -> ToolScreen(state, vm)
                    MainTab.Memory -> MemoryScreen(state, vm)
                    MainTab.Workflows -> WorkflowScreen(state, vm)
                    MainTab.Settings -> SettingsScreen(state, vm)
                }
            }
        }
    }
}

@Composable
private fun TopBar(state: UiState, vm: PhoneClawViewModel) {
    Surface(shadowElevation = 0.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Operit NG", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (state.connected) "Go 引擎运行中" else "未连接",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.connected) Fern else Coral,
                )
            }
            if (state.connected) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Fern, modifier = Modifier.size(24.dp))
            }
        }
    }
    HorizontalDivider(color = Line)
}

@Composable
private fun TabStrip(state: UiState, vm: PhoneClawViewModel) {
    Surface(shadowElevation = 0.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MainTab.entries.forEach { tab ->
                val selected = tab == state.activeTab
                val icon = when (tab) {
                    MainTab.Chat -> Icons.Rounded.Chat
                    MainTab.Agent -> Icons.Rounded.Psychology
                    MainTab.Tools -> Icons.Rounded.Build
                    MainTab.Memory -> Icons.Rounded.Memory
                    MainTab.Workflows -> Icons.Rounded.Schema
                    MainTab.Settings -> Icons.Rounded.Settings
                }
                TabItem(
                    selected = selected,
                    icon = icon,
                    label = tab.name,
                    onClick = { vm.setTab(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    HorizontalDivider(color = Line)
}

@Composable
private fun TabItem(selected: Boolean, icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (selected) Fern else MutedInk, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = if (selected) Fern else MutedInk)
    }
}

// ===== Screens =====

@Composable
private fun ChatScreen(state: UiState, vm: PhoneClawViewModel) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        ModelSelector(state, vm)
        // Messages
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.messages.takeLast(50)) { msg ->
                ChatBubble(msg)
            }
        }
        // Input
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.chatInput,
                onValueChange = vm::setChatInput,
                modifier = Modifier.weight(1f),
                minLines = 1, maxLines = 4,
                placeholder = { Text("输入消息...") },
                shape = RoundedCornerShape(12.dp),
            )
            FilledIconButton(onClick = vm::sendChat, enabled = !state.chatting) {
                Icon(if (state.chatting) Icons.Rounded.HourglassTop else Icons.Rounded.Send, null)
            }
        }
    }
}

@Composable
private fun ModelSelector(state: UiState, vm: PhoneClawViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("模型:", style = MaterialTheme.typography.bodySmall, color = MutedInk)
        var expanded by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(state.currentModel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                (state.availableModels + listOf("gpt-4.1-mini", "claude-sonnet-5")).forEach { model ->
                    DropdownMenuItem(text = { Text(model) }, onClick = { vm.setModel(model); expanded = false })
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMsg) {
    val isUser = msg.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(if (isUser) 16.dp else 16.dp, if (isUser) 4.dp else 16.dp, 16.dp, 16.dp),
            color = if (isUser) Fern.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    if (isUser) "你" else "Operit",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) Fern else Sky,
                )
                Spacer(Modifier.height(4.dp))
                Text(msg.content, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentScreen(state: UiState, vm: PhoneClawViewModel) {
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedTextField(
                value = state.goal, onValueChange = vm::setGoal,
                modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 5,
                label = { Text("目标") },
            )
        }
        item {
            Text("模式", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("fast", "balanced", "deep").forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = state.mode == mode,
                        onClick = { vm.setMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, 3),
                    ) { Text(mode.replaceFirstChar { it.uppercase() }) }
                }
            }
        }
        item {
            Text("可用工具", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.tools.forEach { t ->
                    val selected = t.kind in state.allowedTools
                    FilterChip(
                        selected = selected,
                        onClick = { vm.toggleTool(t.kind) },
                        label = { Text(t.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::plan, enabled = !state.planning) {
                    if (state.planning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("规划")
                }
                OutlinedButton(onClick = vm::execute, enabled = !state.executing) {
                    Text("执行")
                }
            }
        }
        // Agent response
        state.agentResponse?.let { resp ->
            item {
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(resp.summary, style = MaterialTheme.typography.titleSmall)
                        Text("置信度: ${(resp.confidence * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MutedInk)
                        resp.plan.forEach { step ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                Icon(
                                    when (step.status) {
                                        "done" -> Icons.Rounded.CheckCircle
                                        "blocked" -> Icons.Rounded.Block
                                        else -> Icons.Rounded.RadioButtonUnchecked
                                    },
                                    null, modifier = Modifier.size(18.dp),
                                    tint = when (step.status) { "done" -> Fern; "blocked" -> Coral; else -> Amber },
                                )
                                Column { Text(step.title, fontWeight = FontWeight.Medium); Text(step.detail, style = MaterialTheme.typography.bodySmall, color = MutedInk) }
                            }
                        }
                        if (resp.permissions.isNotEmpty()) {
                            HorizontalDivider()
                            Text("需要授权:", fontWeight = FontWeight.SemiBold, color = Coral)
                            resp.permissions.forEach { p ->
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(p.tool, style = MaterialTheme.typography.labelMedium, color = when (p.risk) { "high" -> Coral; "medium" -> Amber; else -> Fern })
                                    Text(p.reason, style = MaterialTheme.typography.bodySmall, color = MutedInk)
                                }
                            }
                            Button(onClick = vm::approveAll) { Text("全部授权") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolScreen(state: UiState, vm: PhoneClawViewModel) {
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("工具控制台", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.tools.forEach { t ->
                    FilterChip(
                        selected = t.kind == state.selectedTool,
                        onClick = { vm.selectTool(t.kind) },
                        label = { Text(t.label, maxLines = 1) },
                    )
                }
            }
        }
        item {
            val desc = state.tools.find { it.kind == state.selectedTool }
            if (desc != null) {
                Text(desc.description, style = MaterialTheme.typography.bodySmall, color = MutedInk)
                Text("可用操作: ${desc.actions.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MutedInk)
            }
        }
        item {
            OutlinedTextField(value = state.toolAction, onValueChange = vm::setToolAction,
                modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("操作") })
        }
        item {
            OutlinedTextField(value = state.toolInput, onValueChange = vm::setToolInput,
                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4, label = { Text("输入") })
        }
        item {
            Button(onClick = vm::runTool, enabled = !state.toolRunning) {
                if (state.toolRunning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("执行工具")
            }
        }
        state.toolResult?.let { result ->
            item {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (result.success) Icons.Rounded.CheckCircle else Icons.Rounded.Error, null,
                                tint = if (result.success) Fern else Coral, modifier = Modifier.size(20.dp))
                            Text(result.title, fontWeight = FontWeight.SemiBold)
                        }
                        Text(result.output, style = MaterialTheme.typography.bodySmall, maxLines = 20, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
        // Toolbox section
        item {
            Spacer(Modifier.height(8.dp))
            Text("工具箱", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.openToolbox("filemanager") }) {
                    Icon(Icons.Rounded.Folder, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("文件管理器")
                }
                OutlinedButton(onClick = { vm.openToolbox("logcat") }) {
                    Icon(Icons.Rounded.Terminal, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("日志")
                }
                OutlinedButton(onClick = { vm.openToolbox("sql") }) {
                    Icon(Icons.Rounded.Storage, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("SQL")
                }
            }
        }
    }
}

@Composable
private fun MemoryScreen(state: UiState, vm: PhoneClawViewModel) {
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("记忆系统", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = state.memoryInput, onValueChange = vm::setMemoryInput,
                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4, label = { Text("记住什么？") })
            Spacer(Modifier.height(8.dp))
            Button(onClick = vm::saveMemory) { Text("保存记忆") }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = state.memoryQuery, onValueChange = vm::setMemoryQuery,
                modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("搜索记忆") })
        }
        if (state.memories.isEmpty()) {
            item { Text("暂无记忆", color = MutedInk) }
        } else {
            items(state.memories) { mem ->
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(12.dp)) {
                        Text(mem.title, fontWeight = FontWeight.SemiBold)
                        Text(mem.body, style = MaterialTheme.typography.bodySmall, color = MutedInk,
                            maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkflowScreen(state: UiState, vm: PhoneClawViewModel) {
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("工作流", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = state.workflowName, onValueChange = vm::setWorkflowName,
                modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("工作流名称") })
            Spacer(Modifier.height(8.dp))
            Button(onClick = vm::saveWorkflow) { Text("保存当前工具为工作流") }
        }
        if (state.workflows.isEmpty()) {
            item { Text("暂无工作流", color = MutedInk) }
        } else {
            items(state.workflows) { wf ->
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(wf.name, fontWeight = FontWeight.SemiBold)
                            Text("${wf.steps.size} 步", style = MaterialTheme.typography.bodySmall, color = MutedInk)
                        }
                        Icon(Icons.Rounded.PlayArrow, null, tint = Fern, modifier = Modifier.clickable { })
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: UiState, vm: PhoneClawViewModel) {
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("设置", style = MaterialTheme.typography.titleMedium)
        }
        item {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("LLM 供应商", fontWeight = FontWeight.SemiBold)
                            Text("OpenAI 兼容接口", style = MaterialTheme.typography.bodySmall, color = MutedInk)
                        }
                        Switch(checked = state.llmEnabled, onCheckedChange = vm::setLlmEnabled)
                    }
                    if (state.llmEnabled) {
                        OutlinedTextField(value = state.llmEndpoint, onValueChange = vm::setLlmEndpoint,
                            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("API 端点") })
                        OutlinedTextField(value = state.llmApiKey, onValueChange = vm::setLlmApiKey,
                            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("API Key") })
                    }
                }
            }
        }
        item {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("系统信息", fontWeight = FontWeight.SemiBold)
                    Text("Go引擎: ${if (state.connected) "运行中" else "未连接"}", style = MaterialTheme.typography.bodySmall)
                    Text("工具数: ${state.tools.size}", style = MaterialTheme.typography.bodySmall)
                    Text("模型数: ${state.availableModels.size}", style = MaterialTheme.typography.bodySmall)
                    val ver = state.systemInfo["version"]?.jsonPrimitive?.contentOrNull ?: "-"
                    Text("版本: $ver", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
