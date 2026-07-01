package com.phoneclaw.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.phoneclaw.app.ui.theme.Fern
import com.phoneclaw.app.ui.theme.MutedInk

/**
 * 简易 Markdown 渲染组件
 * 支持: 标题、粗体、斜体、代码块、列表、链接、分割线
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    selectable: Boolean = true,
) {
    val scrollState = rememberScrollState()
    val content = if (selectable) {
        SelectionContainer {
            Text(
                text = renderMarkdown(markdown),
                modifier = Modifier.verticalScroll(scrollState).fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    } else {
        Text(
            text = renderMarkdown(markdown),
            modifier = Modifier.verticalScroll(scrollState).fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    Column(modifier = modifier) {
        // Process markdown line by line
        val lines = markdown.split("\n")
        var inCodeBlock = false
        var codeBlockContent = StringBuilder()

        lines.forEach { line ->
            when {
                line.startsWith("```") -> {
                    if (inCodeBlock) {
                        // End code block
                        Text(
                            text = codeBlockContent.toString(),
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            color = Fern,
                        )
                        codeBlockContent = StringBuilder()
                        inCodeBlock = false
                    } else {
                        inCodeBlock = true
                    }
                }
                inCodeBlock -> {
                    codeBlockContent.appendLine(line)
                }
                line.startsWith("# ") -> {
                    Text(line.removePrefix("# "), style = MaterialTheme.typography.headlineLarge)
                }
                line.startsWith("## ") -> {
                    Text(line.removePrefix("## "), style = MaterialTheme.typography.headlineMedium)
                }
                line.startsWith("### ") -> {
                    Text(line.removePrefix("### "), style = MaterialTheme.typography.headlineSmall)
                }
                line.startsWith("---") || line.startsWith("***") -> {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    Text(" •  ${line.removePrefix("- ").removePrefix("* ")}",
                        style = MaterialTheme.typography.bodyMedium)
                }
                line.trim().isEmpty() -> { /* skip */ }
                else -> {
                    // Inline formatting
                    Text(
                        text = renderInlineFormatting(line),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun renderInlineFormatting(text: String) {
    val annotated = buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // Bold: **text** or __text__
                text.startsWith("**", i) || text.startsWith("__", i) -> {
                    val end = text.indexOf(text.substring(i, i + 2), i + 2)
                    if (end != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else { append(text[i]); i++ }
                }
                // Italic: *text* or _text_
                text.startsWith("*", i) || text.startsWith("_", i) -> {
                    val end = text.indexOf(text[i], i + 1)
                    if (end != -1 && end > i + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else { append(text[i]); i++ }
                }
                // Inline code: `text`
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end != -1) {
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = Fern,
                        )) { append(text.substring(i + 1, end)) }
                        i = end + 1
                    } else { append(text[i]); i++ }
                }
                else -> { append(text[i]); i++ }
            }
        }
    }
    Text(text = annotated, style = MaterialTheme.typography.bodyMedium)
}
