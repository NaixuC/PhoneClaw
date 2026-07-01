package com.phoneclaw.app.ui.screens

import android.database.sqlite.SQLiteDatabase
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phoneclaw.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

data class DatabaseInfo(
    val path: String,
    val name: String,
    val size: Long,
    val tables: List<String> = emptyList(),
)

data class QueryResult(
    val columns: List<String>,
    val rows: List<List<String>>,
    val error: String? = null,
)

@Composable
fun SQLViewerScreen() {
    val scope = rememberCoroutineScope()
    var databases by remember { mutableStateOf<List<DatabaseInfo>>(emptyList()) }
    var selectedDb by remember { mutableStateOf<DatabaseInfo?>(null) }
    var tables by remember { mutableStateOf<List<String>>(emptyList()) }
    var query by remember { mutableStateOf("SELECT * FROM ") }
    var result by remember { mutableStateOf<QueryResult?>(null) }
    var status by remember { mutableStateOf("") }

    fun scanDatabases() {
        scope.launch(Dispatchers.IO) {
            try {
                val dirs = listOf(
                    "/data/data/com.phoneclaw.app/databases",
                    "/data/data/com.phoneclaw.app/files",
                )
                val dbs = dirs.flatMap { dir ->
                    File(dir).listFiles()?.filter {
                        it.name.endsWith(".db") || it.name.endsWith(".sqlite")
                    }?.map { DatabaseInfo(it.path, it.name, it.length()) } ?: emptyList()
                }
                databases = dbs
                status = "找到 ${dbs.size} 个数据库"
            } catch (e: Exception) {
                status = "扫描失败: ${e.message}"
            }
        }
    }

    fun openDatabase(db: DatabaseInfo) {
        scope.launch(Dispatchers.IO) {
            try {
                SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY).use { sqLite ->
                    val cursor = sqLite.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)
                    val tbls = mutableListOf<String>()
                    while (cursor.moveToNext()) { tbls.add(cursor.getString(0)) }
                    cursor.close()
                    selectedDb = db
                    tables = tbls
                    status = "${tbls.size} 个表"
                }
            } catch (e: Exception) {
                status = "打开失败: ${e.message}"
            }
        }
    }

    fun executeQuery(sql: String) {
        val db = selectedDb ?: return
        scope.launch(Dispatchers.IO) {
            try {
                SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY).use { sqLite ->
                    val cursor = sqLite.rawQuery(sql, null)
                    val cols = cursor.columnNames.toList()
                    val rows = mutableListOf<List<String>>()
                    var count = 0
                    while (cursor.moveToNext() && count < 200) {
                        val row = cols.indices.map { i ->
                            try { cursor.getString(i) ?: "NULL" } catch (_: Exception) { "BLOB" }
                        }
                        rows.add(row)
                        count++
                    }
                    cursor.close()
                    result = QueryResult(cols, rows)
                    status = "${rows.size} 行"
                }
            } catch (e: Exception) {
                result = QueryResult(emptyList(), emptyList(), e.message)
                status = "查询失败"
            }
        }
    }

    LaunchedEffect(Unit) { scanDatabases() }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("SQL 查看器", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        // Database list
        if (selectedDb == null) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(databases) { db ->
                    Surface(Modifier.fillMaxWidth().clickable { openDatabase(db) },
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Storage, null, Modifier.size(24.dp), tint = Fern)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(db.name, fontWeight = FontWeight.Medium)
                                Text(formatSize2(db.size), style = MaterialTheme.typography.bodySmall, color = MutedInk)
                            }
                        }
                    }
                }
            }
        } else {
            // Query view
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(selectedDb!!.name, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { selectedDb = null; result = null }) {
                    Text("返回列表")
                }
            }
            // Table chips
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tables.forEach { table ->
                    FilterChip(
                        selected = query.contains(table),
                        onClick = {
                            query = "SELECT * FROM $table LIMIT 50"
                            executeQuery(query)
                        },
                        label = { Text(table, maxLines = 1) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // SQL input
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
                label = { Text("SQL") },
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = { executeQuery(query) }, modifier = Modifier.fillMaxWidth()) {
                Text("执行")
            }
            Spacer(Modifier.height(8.dp))
            // Results
            result?.let { res ->
                if (res.error != null) {
                    Text("错误: ${res.error}", color = Coral)
                } else {
                    Text("${res.columns.size} 列, $status", style = MaterialTheme.typography.bodySmall)
                    // Header
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        res.columns.forEach { col ->
                            Surface(Modifier.width(120.dp).padding(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text(col, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    // Rows
                    LazyColumn(Modifier.fillMaxWidth()) {
                        items(res.rows) { row ->
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                row.forEach { cell ->
                                    Text(cell, fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.width(120.dp).padding(4.dp),
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        // Status
        if (status.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(status, style = MaterialTheme.typography.bodySmall, color = MutedInk)
        }
    }
}

private fun formatSize2(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024
    return if (kb < 1024) "${kb} KB" else "${kb / 1024}.${(kb % 1024) * 10 / 1024} MB"
}
