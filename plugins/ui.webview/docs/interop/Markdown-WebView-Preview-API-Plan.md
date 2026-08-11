# Markdown WebView Preview API Plan

Status: **BLOCKED** (🚫). This is not a near-term implementation plan. It records the target shape for a good Markdown WebView preview after approval for larger Markdown preview API changes.

## Thesis

A good Markdown WebView preview should be source-bound, not HTML-bound.

The WebView renderer needs the current Markdown document state and editor metadata: unsaved `Document` text, source ranges, selection, scroll target, VCS changed lines, runnable command descriptors, preview settings, and resource context. Pushing generated HTML through the old `setHtml(...)` API makes those capabilities either impossible or awkward because the WebView page becomes a passive HTML sink instead of a Markdown-aware renderer.

The long-term API should keep legacy HTML preview providers compatible, but let modern providers bind to the current Markdown preview source once and own the renderer-specific update lifecycle.

## Current Regression Context

The branch had these capabilities before the rollback commits:

- Document-bound WebView preview lifecycle through a `MarkdownPreviewPanel`-style abstraction.
- Quiet theme updates without preview panel recreation.
- Editor caret/selection projection into source ranges sent to the page.
- VCS changed-line projection into rendered block decorations.
- Runnable Markdown command descriptors sent to the page for code fence and inline run controls.

Rollback commits restored the older `MarkdownHtmlPanelProvider` / `setHtml(...)` shape and removed most Kotlin-side producers for those capabilities. Some frontend and protocol pieces survived: `MarkdownPreviewPageApi` still has `commands`, `changes`, `selection`, and `settings` DTOs, and the React page still contains source-position decorations and CSS classes. The missing part is the platform Markdown preview API and backend lifecycle that feeds those DTOs.

## Target API Shape

Keep the existing Markdown preview provider extension point as the selection boundary. Do not introduce a second provider family.

Add a compatible source-bound creation path to the existing provider API. The exact name can be decided during implementation, but the capability should be equivalent to:

```kotlin
open suspend fun createPreviewPanel(
  project: Project,
  document: Document,
  editor: Editor,
  file: VirtualFile?,
  scope: CoroutineScope,
): MarkdownPreviewPanel
```

The default implementation should adapt existing HTML providers through the legacy `sourceTextPreprocessor + setHtml(...)` flow. WebView should override this method and create a Markdown-aware panel directly.

The panel abstraction should expose preview-level operations, not HTML transport operations:

```kotlin
interface MarkdownPreviewPanel {
  val component: JComponent

  suspend fun scrollToLine(line: Int) {}

  suspend fun setEditorSelection(range: MarkdownPreviewSourceRange?) {}
}
```

The important contract is ownership:

- `MarkdownPreviewFileEditor` owns editor-level events such as caret/selection and split-view lifecycle.
- The provider-created panel owns renderer-specific content updates, resource loading, WebView protocol calls, and cleanup through the supplied scope.
- Legacy HTML providers remain supported by an adapter.

## WebView Renderer Requirements

The WebView panel should receive raw Markdown and metadata, not generated HTML.

It should own these update paths:

- Document text changes: send `contentChanged(markdown, scrollLine, commands, changes, settings)` after debounce/conflation.
- Editor caret/selection changes: send `selectionChanged(selection)` without resending Markdown.
- VCS range changes: update changed block descriptors without panel recreation.
- Scroll requests: call `scrollToLine(...)` without resending Markdown.
- Preview settings such as font size: use a settings-only bridge call, not `contentChanged(...)`.
- Theme changes: rely on the shared WebView theme bridge and frontend `webViewTheme.onChanged`; do not recreate the panel and do not rerender Markdown solely for a theme change.

The renderer must preserve unsaved editor text by reading from `Document`, not from `VirtualFile` contents alone.

## Feature Restore Notes

### Source-bound panel lifecycle

The previous implementation added a `createPreviewPanel(project, document, scope)` path to the existing `MarkdownHtmlPanelProvider`. The default implementation was a compatibility adapter: find the `VirtualFile` through `FileDocumentManager`, create the legacy `MarkdownHtmlPanel`, and start a `LegacyMarkdownPreviewPanel` that kept using `sourceTextPreprocessor + setHtml(...)` internally.

WebView overrode that method and did not support the old HTML factories. It created `WebViewMarkdownPreviewPanel.createStarted(project, document, scope)`, so the WebView renderer was bound to the editor `Document` from the start and could observe unsaved content.

`MarkdownPreviewFileEditor` held a `MarkdownPreviewPanel?` instead of a raw `MarkdownHtmlPanel?`. On attach it created a child scope, called `provider.createPreviewPanel(...)`, added `panel.component`, then initialized scroll and selection through `panel.scrollToLine(...)` and `panel.setEditorSelection(...)`. On detach it removed the component and cancelled the panel job. This is the right ownership model to restore, but the API should stay additive and compatible with the current Java `MarkdownHtmlPanelProvider` shape.

### Quiet theme changes

The old fix had two parts.

On the Kotlin side, `MarkdownHtmlPanelProvider` had a default `shouldRecreatePreviewPanelOnSettingsChange(): Boolean = true`. `WebViewMarkdownPreviewPanelProvider` returned `false`. `MarkdownPreviewFileEditor.UpdatePanelOnSettingsChangedListener` still attached a missing panel when needed, but it only detached and recreated an existing panel if the selected provider wanted recreation and the provider had not changed.

On the TypeScript side, `webViewTheme.onChanged` updated local `theme` and called `applyTheme(nextTheme)` only. The explicit `renderPreview()` call was removed from the theme listener. Theme changes therefore updated `document.documentElement.dataset.theme` and CSS variables without rebuilding the React markdown tree.

Future implementation should keep that behavior and split preview settings from content updates. Font size can use a settings-only bridge call, while theme should stay on the shared WebView theme bridge.

### Editor selection highlighting

The previous core API exposed `MarkdownPreviewPanel.setEditorSelection(range)` as a default no-op, so legacy providers ignored it.

`MarkdownPreviewFileEditor.setMainEditor(editor)` registered `CaretListener` and `SelectionListener` with the file editor as disposable parent. Caret movement, caret add/remove, and selection changes called `syncEditorSelection(editor)`. That method ignored events from non-main editors, mapped the primary caret to a source range, and called `panel?.setEditorSelection(...)` on EDT.

`MarkdownPreviewSourceRangeMapper` did the conversion from editor offsets to one-based source positions:

- Clamp both offsets into `0..document.textLength`.
- Sort start/end so reversed selections are valid.
- For non-empty selections, use `selectionEnd - 1` as inclusive end so a selection ending at the first column of the next line does not highlight that next line.
- Convert offsets to one-based `(line, column)` using `document.getLineNumber(offset)` and `document.getLineStartOffset(line)`.

WebView stored the current `MarkdownPreviewSourceRange?`, sent `MarkdownSelectionChangedParams(selection)` through `MarkdownPreviewPageApi.selectionChanged(...)`, and resent it from `pageReady()`.

The frontend already has the required rendering path: `selectionChanged` updates local `selection`, `MarkdownPreviewApp` runs `decorateSourceBlocks(selection, changes)`, source positions come from the `remarkSourcePositionAttributes` plugin as `data-sourcepos`, and intersecting block-level elements receive `is-source-selected`.

### VCS diff highlighting

The previous WebView panel installed VCS tracking inside the panel lifecycle.

It used `LineStatusTrackerManager.getInstanceImpl(project)`, called `requestTrackerFor(document, this)`, listened for tracker add/remove/valid events, and installed a `LineStatusTrackerListener` on the current tracker. `onBecomingValid()` and `onRangesChanged()` requested changed-block recomputation. Cleanup removed the tracker listener and released the tracker for the panel owner when the panel scope completed.

The later version separated reading from mapping:

- `readMarkdownPreviewChangedBlocks(tracker)` ran under `readAction`, checked `tracker == null`, `tracker.isReleased`, and `tracker.isValid()`, then used `tracker.readLock { tracker.getRanges() }`.
- `MarkdownPreviewVcsChangeMapper.toChangedBlocks(ranges)` converted VCS ranges to page descriptors.

Mapping rules were:

- `Range.INSERTED` with visible lines -> `MarkdownChangedBlockKind.ADDED`, source lines `line1 + 1 .. line2`.
- `Range.MODIFIED` and whitespace-only `Range.EQUAL` ranges -> `MODIFIED`, source lines `line1 + 1 .. line2`.
- `Range.DELETED` -> `REMOVED` placeholder anchored at `line1 + 1`.
- Empty visible ranges except deletes -> no descriptor.
- `null` ranges -> empty changes.

WebView kept `changedBlocks` as state. Content updates included `changes = changedBlocks.value` in `MarkdownContentChangedParams`. The frontend already decorates this: added/modified ranges add `is-vcs-added` or `is-vcs-modified` to rendered blocks whose `data-sourcepos` line interval intersects the change; removed ranges insert a `markdownRemovedBlockPlaceholder` before the nearest following rendered block.

### Runnable command descriptors

The command support was implemented by `MarkdownCommandIndex` and `MarkdownCommandRunnerSupport`.

During `sendCurrentContent()`, WebView built `MarkdownCommandIndex.build(project, sourceFile, markdown)`. If the command runner extension was disabled, or no working directory was available, the index was empty. Otherwise it parsed markdown with `MarkdownParser(MarkdownParserManager.FLAVOUR)` and visited code fences and code spans.

Code fence handling:

- Find the content range between the first EOL after the opening fence and the closing fence.
- Use the fence language to find a block runner through `MarkdownCommandRunnerSupport.findBlockRunner(...)`.
- Create a block command for runnable fenced content.
- For each content line, create a line command when `RunAnythingProvider` can match it.
- If a multiline block also has a runnable first line, store `firstLineCommandId` so the preview can offer block-vs-line execution.

Code span handling:

- Strip matching backticks and trim the inline command text.
- Allow Run Anything run configurations for inline commands.
- Create an inline command descriptor at the code span source position when the command matches.

Command IDs were stable hashes of command kind, source position, and command text. WebView kept the executable command map in `commandIndex`, sent only descriptors to the page, and used `runCommand(id, clientX, clientY)` from the page to find and execute the matching command. If a block command had a first-line alternative, WebView showed a Swing popup with block and line run actions; otherwise it executed directly through the Markdown command runner.

The frontend already has the presentation pieces: `commands` are split into block, line, and inline lookups; code fences render `CodeFenceRunGutter`; inline code renders an inline run button; clicks call `MarkdownPreviewHostApi.runCommand(...)`.

### Content, scroll, and settings updates

The old WebView panel used conflated flows rather than synchronous push calls from the editor:

- `DocumentListener.documentChanged` called `requestContentUpdate()`.
- `MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = DROP_OLDEST)` represented content update requests.
- `updateRequests.debounce(20.milliseconds).collectLatest { sendCurrentContent() }` read the latest document text and cancelled obsolete work.
- `scrollLine` was stored separately and clamped to the current document line count before sending.

`sendCurrentContent()` read `document.text`, current scroll line, and current changed blocks under read action, rebuilt command descriptors, then sent a single `contentChanged(...)` payload. Non-content operations should stay separate: `scrollToLine(...)` should call the page scroll API, `selectionChanged(...)` should call the selection API, and settings-only changes should not resend markdown.

### No HTML fallback for WebView

WebView should not depend on `setHtml(...)`, generated temporary HTML files, or resource hacks that are artifacts of the old HTML preview contract. The WebView page is the Markdown renderer; it needs raw markdown plus structured metadata.

## Implementation Constraints

- The API change is intentionally larger than a narrow compatibility patch and should wait for explicit approval from Markdown platform owners.
- The existing provider extension point should stay the renderer-selection boundary.
- Compatibility must be additive: existing `MarkdownHtmlPanel` providers should continue to work.
- Do not restore `runBlockingMaybeCancellable` cleanup paths from the old WebView prototype. Cleanup should be scope/disposable-owned.
- Do not change the Compose renderer just to support WebView-specific raw Markdown behavior.
- Avoid storing provider state in `MarkdownPreviewFileEditor` unless it is required for renderer selection or compatibility.

## Suggested Verification

When this plan becomes active, add focused tests for:

- Legacy provider compatibility through the adapter path.
- WebView provider creation through the source-bound path.
- No preview recreation on theme/settings changes that should be handled in-place.
- Source range mapping for caret, selection, and reversed offsets.
- VCS range mapping for added, modified, deleted, invalid, and released trackers.
- Protocol serialization for `contentChanged`, `selectionChanged`, settings-only updates, and changed block descriptors.

Run affected Markdown and WebView preview tests, rebuild WebView frontend assets after TypeScript changes, and lint changed Kotlin/Java files.
