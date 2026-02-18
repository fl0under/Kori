package org.yangdai.kori.presentation.component.note.highlighting

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import org.yangdai.kori.data.local.entity.NoteType

@Composable
internal fun rememberAsyncOutputTransformation(
    noteType: NoteType,
    textFieldState: TextFieldState
): OutputTransformation? {
    val highlighter = remember(noteType) { createAsyncHighlighter(noteType) } ?: return null

    var spans by remember(noteType) { mutableStateOf<List<HighlightSpan>>(emptyList()) }

    LaunchedEffect(noteType, textFieldState, highlighter) {
        snapshotFlow { textFieldState.text.toString() }
            .distinctUntilChanged()
            .mapLatest { text ->
                withContext(Dispatchers.Default) {
                    highlighter.highlight(text).normalized(text.length)
                }
            }
            .collect { computedSpans -> spans = computedSpans }
    }

    return remember(noteType) { AsyncOutputTransformation { spans } }
}
