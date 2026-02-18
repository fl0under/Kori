package org.yangdai.kori.presentation.component.note.highlighting

internal fun List<HighlightSpan>.normalized(textLength: Int): List<HighlightSpan> {
    if (isEmpty() || textLength <= 0) return emptyList()

    val clampedSorted = asSequence()
        .mapNotNull { span ->
            val start = span.start.coerceIn(0, textLength)
            val end = span.endExclusive.coerceIn(0, textLength)
            if (end <= start) null else span.copy(start = start, endExclusive = end)
        }
        .sortedWith(compareBy<HighlightSpan> { it.start }.thenBy { it.endExclusive })
        .toList()

    if (clampedSorted.isEmpty()) return emptyList()

    val merged = ArrayList<HighlightSpan>(clampedSorted.size)
    for (span in clampedSorted) {
        val previous = merged.lastOrNull()
        if (previous != null && previous.style == span.style && span.start <= previous.endExclusive) {
            merged[merged.lastIndex] = previous.copy(endExclusive = maxOf(previous.endExclusive, span.endExclusive))
        } else {
            merged += span
        }
    }

    return merged
}
