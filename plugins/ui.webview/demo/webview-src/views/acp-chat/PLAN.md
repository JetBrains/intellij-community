# ACP Chat — assistant-ui feature parity

## Context

The ACP AI-chat demo (`community/plugins/ui.webview/demo/.../acp` + `webview-src/views/acp-chat`) renders messages, thinking, tool cards, plans, permissions and auth, but is missing the polish of the assistant-ui reference demo: per-message **action bar** (copy / edit / regenerate), **bottom-left agent selector**, **model/mode picker**, **file-attach** button, **slash-command** and **@-mention** affordances, **branch picker**, and the **smooth typewriter** text animation.

Goal: add these features, preferring *real* wiring to ACP where the protocol supports it and clean approximations/stubs only where it does not. Research against the **installed** packages (`@assistant-ui/react@0.14.0`, `@agentclientprotocol/sdk@1.0.0`) confirms almost everything can be real.

This is webview-only work plus one small additive Kotlin bridge method. No `.iml`/module changes.

## Current architecture (recap, with refs)

- **Kotlin host** spawns the agent and pipes stdio: `acp/AcpProcessBridge.kt`, `acp/AcpBridgeHostApiImpl.kt`, contract in `acp/AcpBridgeApi.kt` (host methods `listAgents/startAgent/sendStdin/stopAgent`; page notifications `onAgentStdout/onAgentExit`).
- **Transport**: `src/bridge/acpStdioStream.ts` turns bridged stdout/stdin into an ndjson `Stream`; `src/bridge/webviewApi.ts` is the typed bridge mirror.
- **Protocol**: `src/acp/client.ts` — `AcpSession` wraps the SDK `ClientSideConnection` (`initialize`/`newSession`/`prompt`/`cancel`/`authenticate`) and decodes `session/update` into an `AcpEventSink`.
- **Runtime**: `src/runtime/useAcpChat.ts` — builds assistant-ui state via `useExternalStoreRuntime` (currently only `isRunning`, `messages`, `convertMessage`, `onNew`, `onCancel`). Streams accumulate in `turnRef` and flush into the last assistant message.
- **UI**: `src/components/ChatView.tsx` composes `ThreadPrimitive` + `MessagePrimitive.Parts` (Text→markdown, Reasoning→`ThinkingBlock`, tools→`ToolCallCard`) + `ComposerPrimitive`.
- **Build**: `bun run build` (`webview-src/build.ts`, vite) emits to `resources/webview/views/acp-chat/`. `bun run typecheck` = `tsc --noEmit`.

## Feature → ACP capability mapping (what's real vs approximated)

| Feature | Mechanism | Status |
|---|---|---|
| Smooth typing animation | `useSmooth(useMessagePartText(), …)` (lib) | **Real (lib)** |
| Copy | `ActionBarPrimitive.Copy` + `unstable_capabilities.copy` | **Real (lib)** |
| Agent selector placement | existing `AgentSelector` moved into bottom-left chat chrome | **Real (UI-only)** |
| Model / mode picker | `NewSessionResponse.modes` + `configOptions`; `setSessionMode` / `setSessionConfigOption`; `current_mode_update` | **Real (agent-dependent; disabled placeholder if agent advertises none)** |
| File attachments | `PromptCapabilities.image/embeddedContext` → `ContentBlock` `image`/`resource` in `prompt` | **Real (capability-gated)** |
| Slash-command menu | `available_commands_update` → insert `/cmd` into composer | **Real** |
| @-mention files | new bridge `listFiles` → `resource_link` content block (baseline; no capability gate) | **Real (+1 bridge method)** |
| Edit / Regenerate | no native ACP op → client re-prompt (`onEdit`/`onReload`) | **Real via re-prompt** |
| Branch picker ◀1/2▶ | `BranchPickerPrimitive` + external-store `setMessages`/`messageRepository` | **Real (lib; most involved)** |
| Quote selection | `SelectionToolbarPrimitive.Root`/`.Quote` → composer quote; `ComposerPrimitive.Quote`; `useMessageQuote()` | **Real (lib)** |
| Comment on selection | custom, on `SelectionToolbarPrimitive.Root` + `useSelectionToolbarInfo()`; chip via composer-attachment controls | **Real (lib-assisted)** |

Key confirmations: `ClientSideConnection implements Agent` (so `setSessionMode`/`setSessionConfigOption` are already on the existing `connection`); `useExternalStoreRuntime` accepts `onEdit`, `onReload(parentId, cfg)`, `setMessages`, `adapters.attachments`, `unstable_capabilities.copy`; `AppendMessage` carries `attachments` and `sourceId` (edited message id); `useSmooth` is a backlog-draining typewriter (defaults drainMs 250, maxCharIntervalMs 5). Selection→quote is fully library-provided: `SelectionToolbarPrimitive.Root` captures `{text, messageId, rect}` and portals a toolbar; `SelectionToolbarPrimitive.Quote` calls `thread().composer().setQuote(...)`; `ComposerPrimitive.Quote`/`.QuoteText`/`.QuoteDismiss` render & clear the pending chip; the sent user message exposes it via `useMessageQuote()` (`metadata.custom.quote`). `useSelectionToolbarInfo()` exposes the selection to custom toolbar buttons (basis for comment-on-selection).

## Implementation plan

### 0. Shared plumbing
1. In `src/acp/client.ts`, make `AcpSession` expose prompt capabilities, session modes/config options, current mode updates, available commands, and plan updates through `AcpEventSink`.
2. Add `setMode(modeId)` / `setConfigOption(id, value)` on `AcpSession`, using the SDK methods already available on `ClientSideConnection`.
3. Generalize prompting from `prompt(text)` to `prompt(blocks: ContentBlock[])`, with a text convenience helper. Centralize prompt-block construction in `src/runtime/useAcpChat.ts` so text, attachments, mentions, quotes, and comments use one path.
4. Extend `src/model/types.ts` with `SessionModeView`, `ConfigOptionView`, `CommandView`, and `AttachmentView`.
5. In `src/runtime/useAcpChat.ts`, pass shared runtime options to `useExternalStoreRuntime`: `setMessages`, `unstable_capabilities: { copy: true }`, and feature-specific handlers/adapters as they are added.

### 1. Agent selector placement
1. In `ChatView.tsx`, move the existing `AgentSelector` out of the top thread header and into a persistent bottom-left control area.
2. Keep the existing `starting` disabled state and agent-switch flow intact.
3. In `styles.css`, position the selector so it does not overlap the composer, action bars, or message list on narrow webview sizes.
4. Keep status/auth/permission surfaces visually separate from the selector.

### 2. Smooth typing animation
1. In `ChatView.tsx`, replace the direct `props.text` read in `MarkdownText` with `useSmooth(useMessagePartText(), …)`.
2. Apply the same pattern in `ThinkingBlock.tsx` for reasoning text.
3. Render a trailing caret while `status.type === "running"` and tune `minCommitMs` so markdown is not re-parsed every frame.
4. Add caret styling in `styles.css`.

### 3. Message action bar: Copy / Edit / Regenerate
1. Add `ActionBar.tsx` using `ActionBarPrimitive.Root`, `Copy`, `Edit`, and `Reload`.
2. Mount the action bar on assistant messages; expose Edit on user messages where the primitive expects it.
3. Implement `onEdit` in `useAcpChat.ts`: use `message.sourceId`, truncate messages after the edited user turn, append a fresh assistant placeholder, and re-prompt with the rebuilt content blocks.
4. Implement `onReload(parentId, cfg)` in `useAcpChat.ts`: find the user turn that produced the assistant message and re-prompt with the same content blocks.
5. Style hover-reveal actions in `styles.css`.

### 4. Branch picker for regenerated replies
1. Switch the message store to a branch-aware shape using `ExportedMessageRepository` / `messageRepository`, or a branchable array with `setMessages` if that stays simpler.
2. Make regenerate append a sibling assistant message instead of replacing the current one.
3. Add `BranchPickerPrimitive` to assistant messages in `ChatView.tsx`.
4. Keep an explicit fallback: if repository wiring proves too heavy, keep in-place regenerate for the first pass and leave the picker disabled.
5. Style the branch picker controls in `styles.css`.

### 5. Model / mode picker
1. In `src/acp/client.ts`, capture `NewSessionResponse.modes` and `configOptions`, handle `current_mode_update`, and push all of them through the sink.
2. In `useAcpChat.ts`, store `modes`, `configOptions`, and `currentModeId`; reset them on agent switch.
3. Expose `selectMode` and `selectConfigOption`, calling `AcpSession.setMode` / `setConfigOption`.
4. Add `ModelPicker.tsx`, modeled on `AgentSelector.tsx`, and mount it in a new composer toolbar.
5. Show a disabled placeholder when the agent advertises neither modes nor config options.

### 6. File attachments
1. In `src/acp/client.ts`, capture `init.agentCapabilities?.promptCapabilities` during `connect()`.
2. Add a small `AttachmentAdapter` in `useAcpChat.ts`: `accept`, `add` with preview data, `send` to `CompleteAttachment`, and `remove`.
3. In `onNew` / `onEdit`, convert completed attachments into ACP `ContentBlock`s: `image` when image is supported, `resource` when embedded context is supported.
4. Add an attachment button plus `ComposerPrimitive.Attachments` preview row, gated by prompt capabilities.
5. Add attachment chip and preview styling in `styles.css`.

### 7. Slash commands
1. In `src/acp/client.ts`, handle `available_commands_update` and push commands through the sink.
2. In `useAcpChat.ts`, store command state and reset it on agent switch.
3. Add `SlashCommandMenu.tsx`: show a popover/typeahead on `/`, and insert `/<name> ` into the composer when selected.
4. Send the command as a normal prompt prefix; the agent remains responsible for parsing it.
5. Add slash-command popover styling in `styles.css`.

### 8. @-mention files
1. In `acp/AcpBridgeApi.kt`, add `suspend fun listFiles(params: ListFilesRequest): ListFilesResult` with DTOs `ListFilesRequest(query: String, limit: Int = 50)` / `ListFilesResult(files: List<String>)`.
2. In `acp/AcpBridgeHostApiImpl.kt`, implement bounded relative-path lookup from the agent `cwd` (already tracked) / project base, filtered by query and capped by limit.
3. Mirror the new host method and DTOs in `src/bridge/webviewApi.ts`.
4. Add `MentionMenu.tsx`: show a typeahead on `@`, insert an `@path` chip, and stage a `resource_link` block for the next prompt.
5. Convert staged mentions into ACP content blocks in `onNew` / `onEdit`, then clear staged mention state.
6. Add mention popover and chip styling in `styles.css`.

### 9. Quote selection
1. Mount `SelectionToolbarPrimitive.Root` once inside the thread and include `SelectionToolbarPrimitive.Quote`.
2. Render pending quotes in the composer with `ComposerPrimitive.Quote`, `QuoteText`, and `QuoteDismiss`.
3. Render sent quotes in `UserMessage` via `useMessageQuote()` as a blockquote above the user text.
4. In `onNew` / `onEdit`, read `message.metadata?.custom?.quote` and prepend the cited context to the ACP prompt as quoted text or a `resource_link` to the source `messageId`.
5. Preserve `metadata.custom.quote` through `convertMessage`.
6. Add selection toolbar and quote-chip styling in `styles.css`.

### 10. Comment on selection
1. Add a custom **Comment** button inside `SelectionToolbarPrimitive.Root`, using `useSelectionToolbarInfo()` for `{ text, messageId, rect }`.
2. On click, reveal a small inline input anchored in the toolbar or selection rect.
3. On submit, stage `{ quotedText, messageId, comment }` in `pendingContext[]` and clear the selection.
4. Render staged comments as dismissible composer chips next to the quote chip.
5. In `onNew`, drain `pendingContext` into ACP content blocks and clear it after sending.
6. Add comment input and staged-chip styling in `styles.css`.

## Files

**Modify**
- `webview-src/views/acp-chat/src/acp/client.ts`
- `webview-src/views/acp-chat/src/runtime/useAcpChat.ts`
- `webview-src/views/acp-chat/src/components/ChatView.tsx`
- `webview-src/views/acp-chat/src/components/AgentSelector.tsx`
- `webview-src/views/acp-chat/src/components/ThinkingBlock.tsx`
- `webview-src/views/acp-chat/src/model/types.ts`
- `webview-src/views/acp-chat/src/bridge/webviewApi.ts`
- `webview-src/views/acp-chat/styles.css`
- `demo/src/com/intellij/ui/webview/demo/acp/AcpBridgeApi.kt`
- `demo/src/com/intellij/ui/webview/demo/acp/AcpBridgeHostApiImpl.kt`

**Add** (under `webview-src/views/acp-chat/src/components/`)
- `ActionBar.tsx`, `ModelPicker.tsx`, `SlashCommandMenu.tsx`, `MentionMenu.tsx`, `CommentOnSelection.tsx` (+ small `AttachmentButton`/`ComposerToolbar`, or inline in `ChatView.tsx`)

## Verification

1. **Types/build**: from `webview-src/` run `bun run typecheck` then `bun run build` (emits to `resources/webview/views/acp-chat/`). `lint_files` on changed `.ts/.tsx`.
2. **Kotlin**: `bazel build` the demo plugin module; `lint_files` on the two changed `.kt` files (no tests in this demo plugin).
3. **End-to-end** (real agent): configure an ACP agent in `~/.jetbrains/acp.json` (e.g. gemini/claude-code ACP), open the **ACP Chat** tool window, then verify:
   - text streams with the smooth typewriter + caret;
   - the agent selector sits in the bottom-left corner and switching agents still restarts the session cleanly;
   - hover a reply → Copy works;
   - **Edit** a user message and **Regenerate** a reply both re-prompt; branch picker switches variants;
   - **model/mode picker** lists agent-advertised modes/options and switching calls the ACP method (placeholder/disabled if none);
   - **attach** button appears only when the agent advertises image/embeddedContext and the file is delivered as a content block;
   - typing `/` shows agent commands; typing `@` lists files and inserts a reference.
   - selecting text in a reply shows the floating toolbar: **Quote** adds a dismissible quote chip to the composer (and renders as a blockquote on the sent message) and the quoted context reaches the agent; **Comment** opens an inline input and stages a comment+selection chip that is delivered in the next prompt.
4. Confirm graceful degradation: an agent that advertises no modes/commands/attachment caps still chats, with those affordances hidden/disabled.
