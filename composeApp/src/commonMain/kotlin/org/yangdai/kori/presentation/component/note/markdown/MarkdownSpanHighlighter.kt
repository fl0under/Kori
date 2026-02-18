package org.yangdai.kori.presentation.component.note.markdown

import org.yangdai.kori.presentation.component.note.highlighting.HighlightSpan
import kmark.MarkdownElementTypes
import kmark.MarkdownTokenTypes
import kmark.ast.ASTNode
import kmark.flavours.gfm.GFMElementTypes
import kmark.flavours.gfm.GFMFlavourDescriptor
import kmark.flavours.gfm.GFMTokenTypes
import kmark.parser.MarkdownParser

internal class MarkdownSpanHighlighter {
    private val flavor = GFMFlavourDescriptor()
    private val parser = MarkdownParser(flavor)

    fun highlight(text: String): List<HighlightSpan> {
        if (text.isEmpty()) return emptyList()
        val rootNode = parser.buildMarkdownTreeFromString(text)
        return buildList { visitNode(rootNode, text) }
    }

    private fun MutableList<HighlightSpan>.addSpan(
        style: androidx.compose.ui.text.SpanStyle,
        start: Int,
        end: Int
    ) {
        if (end > start) add(HighlightSpan(start = start, endExclusive = end, style = style))
    }

    private fun MutableList<HighlightSpan>.visitNode(node: ASTNode, originalText: String) {
        var handleChildrenRecursively = true

        when (node.type) {
            MarkdownTokenTypes.CODE_FENCE_START, MarkdownTokenTypes.CODE_FENCE_END -> {
                addSpan(MarkdownFormat.marker, node.startOffset, node.endOffset)
            }

            MarkdownTokenTypes.FENCE_LANG -> {
                addSpan(MarkdownFormat.codeBlockLanguage, node.startOffset, node.endOffset)
            }

            MarkdownTokenTypes.CODE_FENCE_CONTENT -> {
                addSpan(MarkdownFormat.monoContent, node.startOffset, node.endOffset)
            }

            MarkdownElementTypes.CODE_SPAN -> {
                addSpan(MarkdownFormat.marker, node.startOffset, node.startOffset + 1)
                addSpan(MarkdownFormat.marker, node.endOffset - 1, node.endOffset)
                addSpan(MarkdownFormat.inlineCodeStyle, node.startOffset + 1, node.endOffset - 1)
                handleChildrenRecursively = false
            }

            MarkdownElementTypes.EMPH -> {
                addSpan(MarkdownFormat.marker, node.startOffset, node.startOffset + 1)
                addSpan(MarkdownFormat.marker, node.endOffset - 1, node.endOffset)
                if (node.endOffset > node.startOffset + 2) {
                    addSpan(MarkdownFormat.italicStyle, node.startOffset + 1, node.endOffset - 1)
                }
            }

            MarkdownElementTypes.STRONG -> {
                addSpan(MarkdownFormat.marker, node.startOffset, node.startOffset + 2)
                addSpan(MarkdownFormat.marker, node.endOffset - 2, node.endOffset)
                if (node.endOffset > node.startOffset + 4) {
                    addSpan(MarkdownFormat.boldStyle, node.startOffset + 2, node.endOffset - 2)
                }
            }

            GFMElementTypes.STRIKETHROUGH -> {
                addSpan(MarkdownFormat.marker, node.startOffset, node.startOffset + 2)
                addSpan(MarkdownFormat.marker, node.endOffset - 2, node.endOffset)
                if (node.endOffset > node.startOffset + 4) {
                    addSpan(
                        MarkdownFormat.strikethroughStyle,
                        node.startOffset + 2,
                        node.endOffset - 2
                    )
                }
            }

            MarkdownTokenTypes.LIST_BULLET, MarkdownTokenTypes.LIST_NUMBER, GFMTokenTypes.CHECK_BOX -> {
                addSpan(MarkdownFormat.marker, node.startOffset, node.endOffset)
            }

            GFMTokenTypes.TABLE_SEPARATOR -> {
                addSpan(MarkdownFormat.marker, node.startOffset, node.endOffset)
            }

            GFMElementTypes.TABLE -> {
                addSpan(MarkdownFormat.monoContent, node.startOffset, node.endOffset)
            }

            GFMElementTypes.INLINE_MATH -> {
                addSpan(MarkdownFormat.marker, node.startOffset, node.startOffset + 1)
                addSpan(MarkdownFormat.marker, node.endOffset - 1, node.endOffset)
                val mathContent = originalText.substring(node.startOffset + 1, node.endOffset - 1)
                MarkdownFormat.latexRegex.findAll(mathContent).forEach { latexMatch ->
                    val style = when {
                        latexMatch.groups[1] != null -> MarkdownFormat.latexCommand
                        latexMatch.groups[2] != null -> MarkdownFormat.latexNumber
                        latexMatch.groups[3] != null -> MarkdownFormat.brackets
                        else -> return@forEach
                    }
                    val start = node.startOffset + 1 + latexMatch.range.first
                    val end = node.startOffset + 1 + latexMatch.range.last + 1
                    addSpan(style, start, end)
                }
                handleChildrenRecursively = false
            }

            GFMElementTypes.BLOCK_MATH -> {
                addSpan(MarkdownFormat.marker, node.startOffset, node.startOffset + 2)
                addSpan(MarkdownFormat.marker, node.endOffset - 2, node.endOffset)
                val mathContent = originalText.substring(node.startOffset + 2, node.endOffset - 2)
                MarkdownFormat.latexRegex.findAll(mathContent).forEach { latexMatch ->
                    val style = when {
                        latexMatch.groups[1] != null -> MarkdownFormat.latexCommand
                        latexMatch.groups[2] != null -> MarkdownFormat.latexNumber
                        latexMatch.groups[3] != null -> MarkdownFormat.brackets
                        else -> return@forEach
                    }

                    val start = node.startOffset + 2 + latexMatch.range.first
                    val end = node.startOffset + 2 + latexMatch.range.last + 1
                    addSpan(style, start, end)
                }
                handleChildrenRecursively = false
            }

            MarkdownTokenTypes.HORIZONTAL_RULE -> {
                addSpan(MarkdownFormat.marker, node.startOffset, node.endOffset)
            }

            MarkdownTokenTypes.HTML_TAG, MarkdownElementTypes.HTML_BLOCK -> {
                val html = originalText.substring(node.startOffset, node.endOffset)

                MarkdownFormat.htmlTagRegex.findAll(html).forEach { match ->
                    val isClosingTag = match.groups[1] != null
                    val tagNameGroup = match.groups[2]
                    val attributesGroup = match.groups[3]
                    if (tagNameGroup == null) return@forEach

                    val tagStart = match.range.first + node.startOffset
                    val openBracketEnd = tagStart + 1 + (if (isClosingTag) 1 else 0)
                    addSpan(MarkdownFormat.brackets, tagStart, openBracketEnd)
                    addSpan(
                        MarkdownFormat.brackets,
                        match.range.last + node.startOffset,
                        match.range.last + node.startOffset + 1
                    )

                    val tagNameStart = openBracketEnd
                    val tagNameEnd = tagNameStart + tagNameGroup.value.length
                    addSpan(MarkdownFormat.htmlTag, tagNameStart, tagNameEnd)

                    if (!isClosingTag && attributesGroup != null && attributesGroup.value.isNotEmpty()) {
                        val attributesStart = tagNameEnd
                        MarkdownFormat.quotedContentRegex.findAll(attributesGroup.value)
                            .forEach { quoteMatch ->
                                val quoteStart = attributesStart + quoteMatch.range.first
                                val quoteEnd = attributesStart + quoteMatch.range.last + 1
                                addSpan(MarkdownFormat.htmlQuotedValue, quoteStart, quoteEnd)
                            }
                    }
                }
            }

            MarkdownTokenTypes.ATX_HEADER, MarkdownTokenTypes.SETEXT_1, MarkdownTokenTypes.SETEXT_2 -> {
                addSpan(MarkdownFormat.marker, node.startOffset, node.endOffset)
            }

            MarkdownElementTypes.ATX_1, MarkdownElementTypes.SETEXT_1 -> {
                addSpan(MarkdownFormat.headingStyles[0], node.startOffset, node.endOffset)
            }

            MarkdownElementTypes.ATX_2, MarkdownElementTypes.SETEXT_2 -> {
                addSpan(MarkdownFormat.headingStyles[1], node.startOffset, node.endOffset)
            }

            MarkdownElementTypes.ATX_3 -> {
                addSpan(MarkdownFormat.headingStyles[2], node.startOffset, node.endOffset)
            }

            MarkdownElementTypes.ATX_4 -> {
                addSpan(MarkdownFormat.headingStyles[3], node.startOffset, node.endOffset)
            }

            MarkdownElementTypes.ATX_5 -> {
                addSpan(MarkdownFormat.headingStyles[4], node.startOffset, node.endOffset)
            }

            MarkdownElementTypes.ATX_6 -> {
                addSpan(MarkdownFormat.headingStyles[5], node.startOffset, node.endOffset)
            }

            MarkdownElementTypes.LINK_TEXT, MarkdownElementTypes.AUTOLINK -> {
                addSpan(MarkdownFormat.linkTextStyle, node.startOffset, node.endOffset)
            }

            MarkdownElementTypes.LINK_DESTINATION -> {
                addSpan(MarkdownFormat.urlStyle, node.startOffset, node.endOffset)
            }

            MarkdownElementTypes.BLOCK_QUOTE -> {
                val text = originalText.substring(node.startOffset, node.endOffset)
                MarkdownFormat.githubAlertRegex.findAll(text).forEach { match ->
                    val alertType = match.groupValues[2]
                    val style = when (alertType) {
                        "NOTE" -> MarkdownFormat.noteStyle
                        "TIP" -> MarkdownFormat.tipStyle
                        "IMPORTANT" -> MarkdownFormat.importantStyle
                        "WARNING" -> MarkdownFormat.warningStyle
                        "CAUTION" -> MarkdownFormat.cautionStyle
                        else -> return@forEach
                    }
                    addSpan(
                        style,
                        node.startOffset + match.range.first,
                        node.startOffset + match.range.last + 1
                    )
                }
            }
        }

        if (handleChildrenRecursively) {
            node.children.forEach { child ->
                visitNode(child, originalText)
            }
        }
    }
}
