package org.yangdai.kori

import org.yangdai.kori.data.local.entity.NoteType
import org.yangdai.kori.presentation.component.note.highlighting.createAsyncHighlighter
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertTrue

class HighlightingPerformanceTest {

    @Test
    fun `benchmark async highlighter median latency across note types`() {
        val markdown = buildString {
            repeat(300) {
                append("## Header $it\n")
                append("- [ ] task item +project @context due:2026-01-01\n")
                append("Link https://example.com/$it and mail user$it@example.com\n")
                append("`inline` **bold** _italic_ ~~strike~~\n\n")
            }
        }

        val todo = buildString {
            repeat(1500) {
                append("(A) 2026-01-01 Finish item $it +proj @home key:value\n")
            }
        }

        val plain = buildString {
            repeat(1500) {
                append("visit https://example.org/$it and contact me$it@foo.bar\n")
            }
        }

        val markdownStats = runBenchmark(NoteType.MARKDOWN, markdown)
        val todoStats = runBenchmark(NoteType.TODO, todo)
        val plainStats = runBenchmark(NoteType.PLAIN_TEXT, plain)

        println(
            "Highlight benchmark (ms): " +
                    "markdown median=${markdownStats.medianMs}, p95=${markdownStats.p95Ms}; " +
                    "todo median=${todoStats.medianMs}, p95=${todoStats.p95Ms}; " +
                    "plain median=${plainStats.medianMs}, p95=${plainStats.p95Ms}"
        )

        assertTrue(markdownStats.medianMs >= 0.0)
        assertTrue(todoStats.medianMs >= 0.0)
        assertTrue(plainStats.medianMs >= 0.0)
    }

    private fun runBenchmark(noteType: NoteType, text: String): Stats {
        val highlighter = createAsyncHighlighter(noteType)
        requireNotNull(highlighter)

        repeat(5) { highlighter.highlight(text) }

        val samples = mutableListOf<Double>()
        repeat(20) {
            val elapsedNs = measureNanoTime {
                highlighter.highlight(text)
            }
            samples += elapsedNs / 1_000_000.0
        }

        val sorted = samples.sorted()
        val median = sorted[sorted.size / 2]
        val p95Index = ((sorted.size - 1) * 0.95).toInt()
        val p95 = sorted[p95Index]
        return Stats(medianMs = median, p95Ms = p95)
    }

    private data class Stats(
        val medianMs: Double,
        val p95Ms: Double
    )
}
