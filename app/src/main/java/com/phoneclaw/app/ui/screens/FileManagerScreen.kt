package com.phoneclaw.app.ui.screens

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phoneclaw.app.bridge.GoAgentBridge
import com.phoneclaw.app.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class FileItem(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileManagerScreen(goAgent: GoAgentBridge) {
    val scope = rememberCoroutineScope()
    var currentPath by remember { mutableStateOf(".") }
    var files by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var selectedFile by remember { mutableStateOf<FileItem?>(null) }
    var fileContent by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    fun loadDir(path: String) {
        scope.launch {
            isLoading = true
            try {
                val result = goAgent.runTool("filesystem", "list", path, listOf("filesystem"))
                if (result != null && result.success) {
                    val lines = result.output.lines()
                    val items = lines.mapNotNull { line ->
                        val name = line.removeSuffix("/")
                        if (name.isBlank()) return@mapNotNull null
                        FileItem(name = name, path = if (path == ".") name else "$path/$name",
                            isDir = line.endsWith("/"))
                    }.sortedByDescending { it.isDir }.sortedBy { it.name }
                    files = items
                } else {
                    statusMessage = "读取失败: ${result?.output}"
                }
            } catch (e: Exception) {
                statusMessage = "错误: ${e.message}"
            }
            isLoading = false
        }
    }

    fun readFile(path: String) {
        scope.launch {
            isLoading = true
            val result = goAgent.runTool("filesystem", "read", path, listOf("filesystem"))
            if (result != null && result.success) {
                fileContent = result.output
                selectedFile = files.find { it.path == path }
            } else {
                statusMessage = "读取失败: ${result?.output}"
            }
            isLoading = false
        }
    }

    LaunchedEffect(currentPath) { loadDir(currentPath) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        // Path bar
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Folder, null, Modifier.size(18.dp), tint = Amber)
                Spacer(Modifier.width(8.dp))
                Text("/ $currentPath", style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (currentPath != ".") {
                    IconButton(onClick = {
                        val parent = File(currentPath).parent
                        currentPath = if (parent == null || parent.isEmpty()) "." else parent
                    }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(18.dp), tint = Fern)
                    }
                }
                IconButton(onClick = { loadDir(currentPath) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp), tint = Fern)
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        if (isLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        // File list or content view
        if (fileContent != null && selectedFile != null) {
            // File content view
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(selectedFile!!.name, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { fileContent = null; selectedFile = null }) {
                            Text("返回")
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(fileContent!!, style = MaterialTheme.typography.bodySmall,
                        maxLines = 500, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            // File list
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(files) { file ->
                    Surface(
                        modifier = Modifier.fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (file.isDir) {
                                        currentPath = file.path
                                    } else {
                                        readFile(file.path)
                                    }
                                },
                                onLongClick = { selectedFile = file }
                            ),
                        shape = MaterialTheme.shapes.small,
                        color = if (selectedFile?.path == file.path)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (file.isDir) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                                null, Modifier.size(22.dp),
                                tint = if (file.isDir) Amber else MutedInk,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(file.name, style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!file.isDir) {
                                    Text(formatSize(file.size), style = MaterialTheme.typography.bodySmall,
                                        color = MutedInk)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Status
        if (statusMessage.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = Coral)
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024
    if (kb < 1024) return "${kb} KB"
    val mb = kb / 1024
    if (mb < 1024) return "${mb}.${(kb % 1024) * 10 / 1024} MB"
    val gb = mb / 1024
    return "${gb}.${(mb % 1024) * 10 / 1024} GB"
}
