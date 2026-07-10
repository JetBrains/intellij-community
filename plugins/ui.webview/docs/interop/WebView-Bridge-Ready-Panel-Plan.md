# WebView Bridge-Ready Panel API Plan

Status: **PARTIAL** (⏳). The `suspend createWebViewPanel(...)` and `suspend reload()` signatures already exist; JCEF backend has internal `bridgeReady` handling in `JcefWebViewEngine`. The unified platform `$/webview/bridgeReady` notification, message-bus-owned readiness gating of outgoing transfers, the `configure` overload, and the corresponding tests are not yet implemented. The Markdown WebView preview still uses its feature-level `pageReady` workaround.

Stage-level legend: ✅ done · ⏳ partial · ⬜ todo · 🚫 blocked · 🗑️ dropped.

| Item | Status | Note |
|---|---|---|
| Suspend `createWebViewPanel` / `reload` signatures | ✅ | shipped |
| `configure` overload (register host APIs before first load) | ⬜ | |
| `$/webview/bridgeReady` notification in TS runtime | ⬜ | `wvi-bridge.js` currently emits `$/webview/runtimeInfoRequest` before bridge object is assigned |
| `WebViewMessageBusImpl`-owned readiness gating of `transferToJs` | ⬜ | JCEF has its own delivery buffering; platform-wide gating still ⬜ |
| JCEF internal `bridgeReady` handling | ✅ | `impl/jcef/JcefWebViewEngine.kt` |
| Markdown preview migration to platform readiness | ⬜ | still uses feature-level `pageReady` |
| Tests for ready/reload/queued-notifications semantics | ⬜ | |

## Summary

Change the current `createWebViewPanel(...)` contract so it returns a `WebViewPanel` only after the initial asset page has installed the platform JavaScript bridge and host-to-JS message delivery is safe.

Bridge-ready means:

- `window.__WVI__` is assigned.
- `window.__WVI__.__deliver(...)` is available.
- Host-to-JS notifications sent after `createWebViewPanel(...)` returns are not lost.

This is platform transport readiness, not feature-level application readiness. A feature page may still do its own rendering after the platform bridge is ready.

## API Contract

Keep the existing API shape and change its semantics:

```kotlin
@RequiresEdt
suspend fun createWebViewPanel(
  scope: CoroutineScope,
  options: WebViewPanelOptions,
): WebViewPanel
```

The function should create the native WebView, create the host component, load the initial asset, wait for the platform bridge-ready signal, and only then return the panel.

`WebViewPanel.reload()` should also wait for bridge readiness of the newly loaded page:

```kotlin
suspend fun reload()
```

Add an overload for startup wiring that must happen before the page is loaded:

```kotlin
@RequiresEdt
suspend fun createWebViewPanel(
  scope: CoroutineScope,
  options: WebViewPanelOptions,
  configure: WebViewPanel.() -> Unit,
): WebViewPanel
```

`configure` runs after the panel and message bus exist, but before the initial `reload()`. Call sites should register host APIs that TypeScript may call during startup inside this block.

## Runtime Design

Add an explicit internal bridge-ready notification in the TypeScript runtime:

```text
$/webview/bridgeReady
```

The bridge sends this signal only after `window.__WVI__ = bridge` has completed. Do not use `$/webview/runtimeInfoRequest` as a readiness signal because it is currently emitted from `createWebViewBridge()` before the bridge object is assigned to `window.__WVI__`.

On the Kotlin side, `WebViewMessageBusImpl` owns readiness for the current page navigation:

- Reset readiness before `loadAsset`, `loadFile`, and `loadHtml`.
- Complete readiness when `$/webview/bridgeReady` arrives from JS.
- Make the outgoing worker wait for readiness before calling `engine.transferToJs(...)`.
- Close or cancel the readiness waiter when the bus or WebView is closed.

This centralizes the protection in the message bus. Mac, Linux, and Windows engines no longer receive early host-to-JS frames. JCEF can keep its existing delivery buffering as an additional defensive layer.

## Call Site Changes

Markdown WebView preview should rely on platform bridge readiness instead of its feature-level `pageReady` workaround:

- Register `MarkdownPreviewPageApi` and host APIs in the new `configure` block.
- Do not call `reload()` manually immediately after `createWebViewPanel(...)`.
- Send the first `contentChanged` after `createWebViewPanel(...)` returns.

Other WebView call sites should follow the same pattern:

- Register startup host APIs in `configure` if TypeScript may call them while the page initializes.
- Send host-to-JS notifications after the factory returns.
- Keep feature-specific readiness only for actual feature rendering or domain initialization, not for transport safety.

## Tests

Add or update Kotlin tests in `intellij.platform.ui.webview`:

- `createWebViewPanel(...)` waits for `$/webview/bridgeReady` before returning.
- `WebViewPanel.reload()` resets readiness and waits for the next bridge-ready signal.
- Notifications queued before bridge-ready are delivered after readiness and are not dropped.
- Runtime info and theme requests continue to work independently of the new ready signal.

Add or update TypeScript bridge tests:

- `$/webview/bridgeReady` is sent after `window.__WVI__` is assigned.
- `$/webview/runtimeInfoRequest` is still sent.
- Inbound notifications received after bridge-ready but before `webView.implement(...)` are replayed by the existing pending notification queue.

Suggested verification:

```shell
./tests.cmd --module intellij.platform.ui.webview.tests --test com.intellij.ui.webview.WebViewRuntimeTest
./tests.cmd --module intellij.platform.ui.webview.tests --test com.intellij.ui.webview.impl.rpc.WebViewMessageBusTest
```

Also run the TypeScript build or typecheck for `community/plugins/ui.webview/webview-src`, and lint changed Kotlin and TypeScript files with `mcp__ijproxy__lint_files` after implementation.

## Assumptions

- The desired readiness boundary is platform message infrastructure readiness, not page rendering completion.
- The existing `createWebViewPanel(...)` may take longer because it now waits for bridge readiness. This is intentional.
- A page that uses `createWebViewPanel(...)` but never installs the common WebView bridge will not produce a ready panel; cancellation through the caller scope is the expected failure path.
