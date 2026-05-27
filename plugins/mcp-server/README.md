# MCP server — 261 ↔ master parity

This document tracks which MCP tool implementations are kept in sync with `master`
on this release branch (`261`) and where the two branches deliberately diverge.

Ticket: [IJPL-245792](https://youtrack.jetbrains.com/issue/IJPL-245792).

## Ported from master (in sync)

The implementations of these tools match `master`:

| Tool | JVM file | ij-proxy handler |
|------|----------|------------------|
| `apply_patch` | `toolsets/general/PatchToolset.kt` + `PatchApplyEngine.kt` | `proxy-tools/handlers/apply-patch.ts` |
| `read_file` | `toolsets/general/ReadToolset.kt` | `proxy-tools/handlers/read.ts` |
| `search_text` | `toolsets/general/SearchToolset.kt` | (uses 261 wire path) |
| `search_regex` | `toolsets/general/SearchToolset.kt` | (uses 261 wire path) |
| `search_file` | `toolsets/general/SearchToolset.kt` | (uses 261 wire path) |
| `search_symbol` | `toolsets/general/SearchToolset.kt` + `SearchSymbolSupport.kt` | (uses 261 wire path) |

Supporting infrastructure also taken from master:

- `annotations/annotations.kt` — adds back `McpToolHints` + `McpToolHintValue`.
- `util/fs.util.kt` — `isUnderProjectDirectory` is `internal` so the new
  `read_file` can use it.
- `proxy-tools/shared.ts` — adds `readFileTextExact`, `readFileTextLegacy`,
  `formatReadLine`, `renderRawTextFromReadOutput`, `parseNumberedReadOutput`
  next to the existing 261 helpers.

`read_file` API on this branch:

```
read_file(file_path: String, offset: Int = 1, limit: Int = DEFAULT_READ_LIMIT)
```

The previous 261-only modes (`slice` / `lines` / `indentation`,
`start_line` / `end_line` / `max_lines` / `anchor_line` / `include_siblings` /
`include_header`) are removed — they did not exist on `master`.

## 261-only (kept, not present on master)

These predate the master refactor and remain on 261:

- `lint_files` and `reformat_file` legacy toolsets (`AnalysisToolset.kt`,
  `FormattingToolset.kt`) — both the JVM side and the lack of ij-proxy
  handlers (the JVM toolsets are still wired into `plugin.xml`).
- `proxy-tools/handlers/edit.ts`, `list-dir.ts`, `rename.ts`, `search.ts`,
  `search-text.ts`, `search-file.ts`, `search-symbol.ts`, `search-shared.ts`,
  `search-scope.ts`, `search-constants.ts` on the ij-proxy side use the
  261 wire shape with `lineNumber` / `lineText` (see "Known divergence"
  below).

## Known divergence from master

| Area | 261 | master |
|------|-----|--------|
| `SearchItem` extra fields | `startOffset`, `endOffset`, `lineText` removed (matching master) | same |
| ij-proxy `SearchItem` wire shape | `filePath`, `lineNumber?`, `lineText?` | `filePath`, `startLine?`, `startColumn?`, `endLine?`, `endColumn?` |
| ij-proxy `shared.ts` | superset (keeps both 261 helpers + new master helpers) | only master helpers |
| `lint_files`, `reformat_file` proxy handlers | not present | present |
| Test framework | `McpToolsetTestBase` with SSE transport + `enableBraveMode` | `GeneralMcpToolsetTestBase` with `StreamableHttpClientTransport` + `authorizedSession` |

The ij-proxy keeps the 261 wire shape because the existing search handlers
(`search-text.ts`, `search-shared.ts`) and their tests assume it. The JVM
`SearchToolset` reports `startLine` / `startColumn` etc.; the proxy maps that
to the 261 shape when forwarding to clients. This is intentional — the goal
of the port was to bring the *implementation* in line with master without
changing the proxy's external schema mid-release.

## Tests

JVM (`./tests.cmd --module intellij.mcpserver.tests --test <FQN>`):

- `PatchApplyEngineTest` — pure unit tests for the V4A / unified-diff parser.
- `ReadToolsetTest` — rewritten for the `(file_path, offset, limit)` API.
- `SearchToolsetTest` — assertions on removed fields (`startOffset`,
  `endOffset`, `lineText`) dropped.

ij-proxy (`cd community/build/mcp-servers/ij-proxy && bun test`):

- 163 pass, 3 skip, 0 fail. The new `apply-patch.*.test.ts` and
  `read.*.test.ts` files come directly from master.

## Not ported

- Master's `GeneralMcpToolsetTestBase`, `authorizedSession`,
  `StreamableHttpClientTransport`, and `testResources/mcpToolsetProject`
  test fixtures. These are a sizeable test-framework refactor that would
  break 261's existing toolset tests; not worth the risk for a release branch.
- Master's `lint-files.ts` / `reformat-file.ts` ij-proxy handlers — 261 talks
  to those toolsets through the JVM side directly.
- `PatchToolsetTest`, master's `ReadToolsetTest`, master's `SearchToolsetTest`
  — they depend on `GeneralMcpToolsetTestBase`. Coverage on 261 comes from
  `PatchApplyEngineTest` + the 261-adapted `ReadToolsetTest` /
  `SearchToolsetTest`.
