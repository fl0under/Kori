package org.yangdai.kori.presentation.component.note.highlighting

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import org.yangdai.kori.data.local.entity.NoteType
import org.yangdai.kori.presentation.component.note.markdown.MarkdownSpanHighlighter
import org.yangdai.kori.presentation.component.note.plaintext.TextFormat
import org.yangdai.kori.presentation.component.note.todo.TodoFormat
import org.yangdai.kori.presentation.theme.linkColor

private object PlainTextAsyncHighlighter : AsyncTextHighlighter {
    private val linkStyle = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)

    override fun highlight(text: String): List<HighlightSpan> {
        if (text.isEmpty()) return emptyList()
        return buildList {
            TextFormat.AUTOLINK_EMAIL_ADDRESS.findAll(text).forEach { matchResult ->
                add(HighlightSpan(matchResult.range.first, matchResult.range.last + 1, linkStyle))
            }
            TextFormat.AUTOLINK_WEB_URL.findAll(text).forEach { matchResult ->
                add(HighlightSpan(matchResult.range.first, matchResult.range.last + 1, linkStyle))
            }
        }
    }
}

private object TodoAsyncHighlighter : AsyncTextHighlighter {
    override fun highlight(text: String): List<HighlightSpan> {
        if (text.isEmpty()) return emptyList()

        return buildList {
            var lineStart = 0
            text.lineSequence().forEach { line ->
                val lineEnd = lineStart + line.length

                val doneMatch = TodoFormat.doneRegex.find(line)
                if (doneMatch != null) {
                    add(HighlightSpan(lineStart, lineEnd, TodoFormat.doneStyle))
                } else {
                    val priMatch = TodoFormat.priorityRegex.find(line)
                    if (priMatch != null) {
                        val priEnd = priMatch.range.last + 1
                        val idx = priMatch.groupValues[1][0] - 'A'
                        if (idx in 0..25) {
                            add(
                                HighlightSpan(
                                    lineStart,
                                    lineStart + priEnd,
                                    TodoFormat.priorityStyle.copy(color = TodoFormat.priorityColors[idx])
                                )
                            )
                        }
                    }

                    TodoFormat.dateRegex.findAll(line).forEach { dateMatch ->
                        add(
                            HighlightSpan(
                                lineStart + dateMatch.range.first,
                                lineStart + dateMatch.range.first + 10,
                                TodoFormat.dateStyle
                            )
                        )
                    }
                }

                TodoFormat.contextRegex.findAll(line).forEach {
                    add(
                        HighlightSpan(
                            lineStart + it.range.first,
                            lineStart + it.range.last + 1,
                            TodoFormat.contextStyle
                        )
                    )
                }

                TodoFormat.projectRegex.findAll(line).forEach {
                    add(
                        HighlightSpan(
                            lineStart + it.range.first,
                            lineStart + it.range.last + 1,
                            TodoFormat.projectStyle
                        )
                    )
                }

                TodoFormat.metaRegex.findAll(line).forEach {
                    val value = it.groupValues[1]
                    if (!value.startsWith("@") && !value.startsWith("+")) {
                        add(
                            HighlightSpan(
                                lineStart + it.range.first,
                                lineStart + it.range.last + 1,
                                TodoFormat.metaStyle
                            )
                        )
                    }
                }

                lineStart = lineEnd + 1
            }
        }
    }
}

private object MarkdownAsyncHighlighter : AsyncTextHighlighter {
    private val highlighter = MarkdownSpanHighlighter()

    override fun highlight(text: String): List<HighlightSpan> = highlighter.highlight(text)
}

internal fun createAsyncHighlighter(noteType: NoteType): AsyncTextHighlighter? = when (noteType) {
    NoteType.MARKDOWN -> MarkdownAsyncHighlighter
    NoteType.PLAIN_TEXT -> PlainTextAsyncHighlighter
    NoteType.TODO -> TodoAsyncHighlighter
    else -> null
}
