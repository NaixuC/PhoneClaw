package com.phoneclaw.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phoneclaw.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

data class LogEntry(
    val level: String, // V, D, I, W, E
    val tag: String,
    val text: String,
    val time: String,
)

@Composable
fun LogcatScreen() {
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var filterText by remember { mutableStateOf("") }
    var levelFilter by remember { mutableStateOf("") } // V/D/I/W/E
    var isRunning by remember { mutableStateOf(false) }
    var autoScroll by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    val filteredLogs = remember(logs, filterText, levelFilter) {
        logs.filter { log ->
            (filterText.isEmpty() || log.text.contains(filterText, ignoreCase = true) ||
                    log.tag.contains(filterText, ignoreCase = true)) &&
                    (levelFilter.isEmpty() || log.level == levelFilter)
        }
    }

    fun startLogcat() {
        if (isRunning) return
        isRunning = true
        scope.launch(Dispatchers.IO) {
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("logcat", "-v", "time", "*:V"))
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                while (isActive && isRunning) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    val entry = parseLogLine(line)
                    if (entry != null) {
                        val newLogs = (logs + entry).takeLast(500)
                        logs = newLogs
                    }
                }
                proc.destroy()
            } catch (e: Exception) {
                // Process terminated
            }
        }
    }

    fun stopLogcat() {
        isRunning = false
    }

    LaunchedEffect(Unit) {
        startLogcat()
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        // Controls
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = filterText, onValueChange = { filterText = it },
                modifier = Modifier.weight(1f), singleLine = true,
                placeholder = { Text("过滤...") },
                leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp)) },
            )
            FilterChip(
                selected = levelFilter == "E",
                onClick = { levelFilter = if (levelFilter == "E") "" else "E" },
                label = { Text("错误", style = MaterialTheme.typography.labelSmall) },
            )
            FilterChip(
                selected = levelFilter == "W",
                onClick = { levelFilter = if (levelFilter == "W") "" else "W" },
                label = { Text("警告", style = MaterialTheme.typography.labelSmall) },
            )
            IconButton(onClick = { logs = emptyList() }) {
                Icon(Icons.Rounded.Delete, null, tint = Coral)
            }
            IconButton(onClick = { if (isRunning) stopLogcat() else startLogcat() }) {
                Icon(if (isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Fern)
            }
        }
        Spacer(Modifier.height(8.dp))

        // Log list
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            items(filteredLogs.takeLast(300)) { log ->
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.extraSmall) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text(log.level, style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = when (log.level) {
                                "E" -> Coral; "W" -> Amber; "I" -> Fern; "D" -> MutedInk; else -> MutedInk
                            },
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(20.dp),
                        )
                        Text(log.tag, style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = Sky, modifier = Modifier.width(80.dp),
                            maxLines = 1)
                        Text(log.text, style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

private fun parseLogLine(line: String): LogEntry? {
    // Format: "01-01 12:00:00.000 I/TAG  (PID): message"
    try {
        val levelMatch = Regex("""\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}\s+([VDEWIF])/(\S+)\s*\(\s*\d+\):\s?(.*)""").find(line)
        if (levelMatch != null) {
            return LogEntry(
                level = levelMatch.groupValues[1],
                tag = levelMatch.groupValues[2],
                text = levelMatch.groupValues[3],
                time = levelMatch.groupValues[0].take(18),
            )
        }
    } catch (_: Exception) {}
    return LogEntry("I", "?", line.take(200), "")
}
