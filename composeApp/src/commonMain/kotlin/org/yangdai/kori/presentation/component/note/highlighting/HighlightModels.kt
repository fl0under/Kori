package org.yangdai.kori.presentation.component.note.highlighting

import androidx.compose.ui.text.SpanStyle

internal data class HighlightSpan(
    val start: Int,
    val endExclusive: Int,
    val style: SpanStyle
)

internal interface AsyncTextHighlighter {
    fun highlight(text: String): List<HighlightSpan>
}
