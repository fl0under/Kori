package org.yangdai.kori.presentation.component.note.markdown

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import kotlinx.collections.immutable.persistentListOf
import org.yangdai.kori.presentation.component.note.highlighting.applyHighlightSpans
import org.yangdai.kori.presentation.theme.linkColor

class MarkdownTransformation : OutputTransformation {
    private val highlighter = MarkdownSpanHighlighter()

    override fun TextFieldBuffer.transformOutput() {
        if (originalText.isEmpty()) return
        applyHighlightSpans(highlighter.highlight(originalText.toString()))
    }
}

@Immutable
object MarkdownFormat {

    val headingStyles = persistentListOf(
        SpanStyle(fontWeight = FontWeight.Black, fontSynthesis = FontSynthesis.Weight), // H1
        SpanStyle(fontWeight = FontWeight.ExtraBold, fontSynthesis = FontSynthesis.Weight), // H2
        SpanStyle(fontWeight = FontWeight.Bold, fontSynthesis = FontSynthesis.Weight), // H3
        SpanStyle(fontWeight = FontWeight.SemiBold, fontSynthesis = FontSynthesis.Weight), // H4
        SpanStyle(fontWeight = FontWeight.Medium, fontSynthesis = FontSynthesis.Weight), // H5
        SpanStyle(fontWeight = FontWeight.Normal, fontSynthesis = FontSynthesis.Weight)  // H6
    )

    // 使用橙色强调，mono-space 确保对齐
    val marker = SpanStyle(color = Color(0xFFCE8D6E), fontFamily = FontFamily.Monospace)
    val codeBlockLanguage = SpanStyle(color = Color(0xFFC67CBA))
    val monoContent = SpanStyle(fontFamily = FontFamily.Monospace)
    val linkTextStyle = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
    val urlStyle = SpanStyle(color = Color.Gray, fontStyle = FontStyle.Italic)
    val boldStyle = SpanStyle(fontWeight = FontWeight.Bold)
    val italicStyle = SpanStyle(fontStyle = FontStyle.Italic)
    val strikethroughStyle = SpanStyle(textDecoration = TextDecoration.LineThrough)

    // 为不同 alert 类型定义更鲜明的样式
    val noteStyle = SpanStyle(color = Color(0xFF2F81F7), background = Color(0x142F81F7))
    val tipStyle = SpanStyle(color = Color(0xFF238636), background = Color(0x14238636))
    val importantStyle = SpanStyle(color = Color(0xFF8250DF), background = Color(0x148250DF))
    val warningStyle = SpanStyle(color = Color(0xFFD29922), background = Color(0x14FFD299))
    val cautionStyle = SpanStyle(color = Color(0xFFF85149), background = Color(0x14F85149))

    val inlineCodeStyle =
        SpanStyle(background = Color.DarkGray.copy(alpha = 0.2f), fontFamily = FontFamily.Monospace)
    val htmlTag = SpanStyle(color = Color(0xFFD92C54))       // 标签名样式 (例如: div)
    val brackets = SpanStyle(color = Color(0xFF808080))  // 括号样式
    val htmlQuotedValue = SpanStyle(color = Color(0xFF8ABB6C)) // (例如: "yyy")

    val latexCommand = SpanStyle(color = Color(0xFF512DA8))  // LaTeX 命令样式 (例如: \frac, \alpha)
    val latexNumber = SpanStyle(color = Color(0xFF00796B))   // LaTeX 数字样式 (例如: 2, 3.14)

    // 根据规范，最多允许3个空格的缩进，且不允许制表符
    val githubAlertRegex =
        Regex("""^ {0,3}> ?\[(!(NOTE|TIP|IMPORTANT|WARNING|CAUTION))]""", RegexOption.MULTILINE)
    val htmlTagRegex = Regex("""<(/)?([a-zA-Z0-9]+)([^>\n]*)>""")
    val quotedContentRegex = Regex("\"[^\"]*\"")
    val latexRegex = Regex("""(\\[a-zA-Z]+)|(\d+(?:\.\d+)?)|([{}()\[\]])""")
}