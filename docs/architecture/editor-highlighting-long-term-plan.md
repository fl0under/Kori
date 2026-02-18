# Editor highlighting long-term architecture plan

## Why this exists

Kori currently applies syntax highlighting through `OutputTransformation` in `BasicTextField`, which runs synchronously during text layout on the UI thread. This creates unavoidable per-keystroke work for larger notes.

This document proposes a long-term architecture that preserves current features while removing the main-thread bottleneck.

## Current pipeline and hard constraints

### Where highlighting happens today

- `AdaptiveEditor` picks one of:
  - `MarkdownTransformation`
  - `TodoTransformation`
  - `PlainTextTransformation`
- The selected transformation is passed into `Editor`, then into `BasicTextField(outputTransformation = ...)`.

### Cost centers

1. **Markdown** (`MarkdownTransformation`)
   - Full AST parse (`parser.buildMarkdownTreeFromString(...)`) on each output transform.
   - Recursive AST traversal and additional regex scans for HTML/GitHub alert/math subranges.

2. **Todo** (`TodoTransformation`)
   - Full-text line iteration on each transform.
   - Multiple regex passes per line (done/priority/date/context/project/meta).

3. **Plain text** (`PlainTextTransformation`)
   - Two large URL/email regex scans across the full document on each transform.

### Why the remaining perf issue is architectural

- `OutputTransformation` has no diff API (no changed range, no changed lines).
- Highlighting executes synchronously as part of text field rendering.
- Even with regex optimizations, full-document scanning remains proportional to document size per keystroke.

## Target architecture

### Core idea

Move highlighting to an **asynchronous incremental pipeline** and render using immutable highlight snapshots.

High-level flow:

1. Editor emits document revisions (`text`, `revisionId`).
2. Background highlighter computes incremental highlights from previous state.
3. UI collects latest completed `HighlightSnapshot`.
4. Text is rendered from snapshot (annotated spans), while editing remains responsive.

### Data model

```kotlin
data class HighlightSnapshot(
    val revisionId: Long,
    val spans: List<HighlightSpan>
)

data class HighlightSpan(
    val start: Int,
    val endExclusive: Int,
    val styleKey: StyleKey
)
```

`StyleKey` should map to theme-derived `SpanStyle` inside UI code (avoid raw color/font values in parser core).

### Runtime components

- `HighlightCoordinator`
  - Owns latest text revision.
  - Cancels stale jobs (`mapLatest`/structured concurrency).
  - Publishes snapshots via `StateFlow<HighlightSnapshot>`.

- `IncrementalHighlighter` (per note type)
  - `MarkdownIncrementalHighlighter`
  - `TodoIncrementalHighlighter`
  - `PlainTextIncrementalHighlighter`

- `EditDeltaDetector`
  - Computes changed range between old/new text using prefix/suffix trim in O(delta).
  - Provides approximate dirty region when Compose cannot provide edit details.

## Rendering strategy options

### Option A (recommended): overlay renderer + transparent text glyphs

- Keep `BasicTextField` for cursor, selection, IME, accessibility semantics.
- Render highlighted text in decorator background using `AnnotatedString` from snapshot.
- Use transparent/alpha-minimized foreground glyphs in text field to avoid double-painted characters while retaining caret metrics.

Pros:
- No dependency on `OutputTransformation` limitations.
- Full control over when highlighted text recomputes.

Risks:
- Must ensure layout parity between overlay text and `BasicTextField` style.
- Needs validation for composing text/IME marked ranges.

### Option B: wait for upstream incremental APIs in Compose

- Lower implementation risk in app code.
- Unknown timeline and API shape; does not solve near-term product needs.

## Parser/highlighter implementation plan

### Markdown

**Long-term best path:** switch to incremental parser (e.g., Tree-sitter markdown) or parser with retained parse state.

- Keep an incremental syntax tree in worker memory.
- Re-parse only dirty regions + affected ancestor blocks.
- Emit style spans from changed syntax nodes.
- Preserve existing markdown-specific styles (headings, code fences, links, alerts, HTML, math).

### Todo

- Maintain per-line token cache keyed by line hash/version.
- Recompute only dirty lines and context-dependent neighbors if needed.
- Convert regex pipeline to lightweight tokenizer where practical (especially `context`, `project`, `meta`).

### Plain text

- Replace giant monolithic URL regex scans with:
  - line-based tokenization and
  - bounded URL/email detectors on dirty lines.
- Keep compatibility tests against current behavior to avoid regressions in URL matching.

## Concurrency and cancellation requirements

- All highlight computation runs on `Dispatchers.Default`.
- Use `mapLatest` semantics so stale revisions are canceled.
- Apply snapshot only if `snapshot.revisionId == currentRevisionId`.
- Add fallback timeout budget (e.g., 24–32 ms target, then coalesce) for very large docs.

## Backward-compatible migration phases

### Phase 0: preparatory abstractions

- Introduce highlighter interfaces and style-key mapping.
- Keep current transformations unchanged for shipping stability.

### Phase 1: async snapshots behind feature flag

- Build coordinator + snapshot renderer.
- Run in parallel with existing `OutputTransformation` (A/B compare in debug).

### Phase 2: cut-over by note type

1. Plain text first (lowest parser complexity).
2. Todo second (line cache provides immediate wins).
3. Markdown last (incremental parser integration).

### Phase 3: remove `OutputTransformation` highlighters

- Keep only snapshot path.
- Retain lint/background analysis independently.

## Verification plan

### Performance acceptance targets

- Typing latency p95 (16 KB note): no visible jank on mid-tier desktop/mobile.
- Keystroke-to-highlight update: under 1 frame typical, under 2 frames p95.
- No full-document rescans for single-line edits in todo/plain text.

### Correctness checks

- Golden tests comparing span outputs before/after migration for fixture docs.
- Stress tests for rapid typing + undo/redo + paste.
- IME composition tests (CJK input, dead keys).

## Suggested immediate next step

Implement **Phase 0 + Phase 1 skeleton** (interfaces, coordinator, feature flag, no parser swap yet). This keeps risk low while unlocking incremental/highlight-worker experiments without touching the existing editor behavior for all users.


## Deep dive: what incremental Markdown parsing would actually require

Incremental parsing is not just an optimization toggle; it changes data flow and ownership in the editor stack.

### Required building blocks

1. **Stable revision model**
   - Every edit must produce `(revisionId, text, editDelta)` where `editDelta` includes at least:
     - changed start offset
     - deleted length
     - inserted length
   - Without this, an incremental parser cannot localize reparsing.

2. **Persistent syntax tree state**
   - Keep previous parse tree in memory (worker-side).
   - Apply edit delta to tree positions before reparsing affected ranges.

3. **Dirty-region expansion rules**
   - Markdown blocks are context-sensitive (fences, lists, blockquotes, tables).
   - You must expand the initial dirty region to safe structural boundaries (line starts, block starts, etc.).

4. **Incremental span invalidation**
   - Highlight cache must be segment-based (e.g., rope/chunk list or interval map).
   - On each edit:
     - shift unaffected spans after insertion/deletion
     - recompute only dirty segments
     - merge adjacent spans with same style key

5. **Cancellation-aware worker loop**
   - Continuous typing means stale parse jobs are normal.
   - Pipeline must be `mapLatest`/structured-concurrency first, not best-effort threads.

6. **Correctness harness**
   - Golden corpus comparing full parse vs incremental parse outputs.
   - Property tests for random edits to ensure convergence and span offset correctness.

### Practical implementation shape (elegant path)

A clean architecture keeps parser concerns separate from UI concerns:

- `EditorRevisionStream` (UI/domain boundary)
- `IncrementalParseEngine` (pure parser state machine)
- `SpanProjector` (tree -> style spans)
- `HighlightStore` (versioned immutable snapshots)

This keeps the UI fully declarative while parsing logic remains deterministic/testable in isolation.

## Is incremental parsing worth it?

### Short answer

- **For very small notes:** usually not worth the complexity.
- **For medium/large notes or heavy markdown features:** yes, it can be worth it.

### Rule-of-thumb decision matrix

- If p95 typing latency is already smooth on target devices after async offloading, stop there.
- If markdown still janks at realistic sizes (10–50 KB+ with tables/code/math), incremental parsing becomes high ROI.
- If future roadmap includes richer markdown semantics (folding, outline sync, code intelligence), incremental tree infrastructure compounds value.

### Complexity vs payoff

- Async full-parse (current) gives a big win fast with low risk.
- Incremental parse gives the next step-change but costs significantly more in:
  - correctness complexity
  - maintenance burden
  - parser/tooling coupling

## What a “god-tier” implementation would do

1. **Ship in layers, never big-bang**
   - Keep current async full-parse path as baseline.
   - Add incremental engine behind a feature flag.
   - Compare outputs in debug builds and auto-fallback on mismatch.

2. **Use a parser with proven incremental semantics**
   - Prefer a mature incremental parser (e.g., Tree-sitter style model) over hand-rolled diff+regex hacks.

3. **Design for observability from day one**
   - Emit per-revision metrics: parse time, spans projected, dirty-range width, canceled jobs.
   - Build dashboards or debug overlays to track real typing sessions.

4. **Guarantee correctness before micro-optimizing**
   - Differential tests (full vs incremental) are mandatory.
   - Randomized edit fuzzing on markdown corpora is mandatory.

5. **Fail gracefully**
   - If incremental state becomes uncertain, auto-rebuild full tree for that revision and recover.
   - Correctness > speed.

6. **Keep UI API stable**
   - UI should only see `HighlightSnapshot(revisionId, spans)` regardless of parser strategy.
   - This makes parser evolution invisible to the composable layer.

## Recommended next milestone

Implement an **Incremental-Ready Core** without enabling true incremental parsing yet:

- introduce explicit `EditDelta` tracking in the highlighter coordinator
- change span cache to segment-aware storage
- add full-vs-incremental differential test harness (incremental path can initially proxy full parse)

This gives most architectural benefits immediately and de-risks the eventual parser swap.
