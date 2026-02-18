package org.yangdai.kori.presentation.component.note.highlighting

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer

internal class AsyncOutputTransformation(
    private val getSpans: () -> List<HighlightSpan>
) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        if (originalText.isEmpty()) return
        applyHighlightSpans(getSpans())
    }
}

internal fun TextFieldBuffer.applyHighlightSpans(spans: List<HighlightSpan>) {
    val textLength = originalText.length
    spans.forEach { span ->
        if (span.start >= textLength || span.endExclusive <= span.start) return@forEach
        addStyle(span.style, span.start, span.endExclusive.coerceAtMost(textLength))
    }
}
