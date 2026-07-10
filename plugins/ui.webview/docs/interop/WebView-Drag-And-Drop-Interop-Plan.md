# WebView Drag and Drop Interop Plan

Status: **DESIGN ONLY** (⬜). No production API, no `TransferHandler` / `DataFlavor` wiring, no `WebViewDragPayloadAdapter`, and no page-side `window.__WVI__.dnd` helpers exist in code. This plan is the v1 spec when work starts; nothing in it has landed.

Stage-level legend: ✅ done · ⏳ partial · ⬜ todo · 🚫 blocked · 🗑️ dropped.

- `com.intellij.ui.webview.dnd` public API package — ⬜
- `WebViewDragAndDropController` / payload adapter registry — ⬜
- Browser → Swing path (custom MIME tokens through `DataTransfer`) — ⬜
- Swing → Browser path (`TransferHandler` + payload adapter) — ⬜
- macOS WKWebView smoke verification — ⬜
- Windows WebView2 HWND controller path — ⬜
- JCEF support (explicitly **non-goal for v1**) — 🗑️
- Linux WebKitGTK support (waits on interactive input + asset serving) — 🚫

## Goal

Add an opt-in drag-and-drop layer between local WebView content and IntelliJ Swing UI. The first implementation should move IDE-local typed payloads across WebView and Swing in both directions while keeping standard browser/Swing fallback formats available for ordinary targets.

Target backends for v1:

- macOS system WebView (`WKWebView`).
- Windows system WebView (WebView2 HWND controller).

The feature is intentionally explicit. Existing WebView clients keep the current behavior until they request drag-and-drop support through the asset panel options.

## Non-goals

- Do not support JCEF in the first implementation.
- Do not support the Linux WebKitGTK backend until interactive input and asset serving are complete enough to test the full path.
- Do not make every existing Swing `TransferHandler`, `DnDSupport`, or platform DnD target transparently understand WebView-specific payloads.
- Do not switch the message bus to JSON-RPC. Request/response is not needed for the first DnD protocol.
- Do not implement native AppKit/WebView2 drag hooks unless smoke testing proves that the normal browser/OS DnD path strips the required token formats.

## Current State

`ui.webview` currently has no `TransferHandler`, `DataFlavor`, or DnD bridge. The sample web UI uses HTML drag events only: it writes a custom browser MIME type and a `text/plain` fallback into `DataTransfer`. When dragging outside the page, Swing can only see whatever the browser exports through the OS, which is why the current cross-boundary behavior degrades to plain text.

The existing interop layer is notification-only:

- Kotlin sends and receives typed `WebViewNotification` messages through `WebViewMessageBus`.
- JavaScript uses descriptor-bound `window.__WVI__.notification(...).send/on` helpers from `wvi-bridge.js`.
- The v1 wire envelope remains `{ "method": "...", "params": ... }` without JSON-RPC ids or response fields.

DnD should reuse that bridge for lifecycle notifications and drop events, but the synchronous drag payload visible to the OS must still be written into the browser `DataTransfer` and Swing `Transferable` objects.

## Public API Shape

Add a separate experimental API package for DnD-specific concepts:

```kotlin
package com.intellij.ui.webview.dnd

@ApiStatus.Experimental
data object WebViewDragAndDropDisabled : WebViewDragAndDropOptions

@ApiStatus.Experimental
data class WebViewDragAndDropEnabled(
  val adapters: List<WebViewDragPayloadAdapter<*>> = emptyList(),
) : WebViewDragAndDropOptions

@ApiStatus.Experimental
sealed interface WebViewDragAndDropOptions

@ApiStatus.Experimental
class WebViewDragAndDropController internal constructor { ... }

@ApiStatus.Experimental
data class WebViewDragPayloadType<T : Any>(val id: String)

@ApiStatus.Experimental
data class WebViewDragPayload<T : Any>(
  val type: WebViewDragPayloadType<T>,
  val value: T,
  val presentableText: String,
)

@ApiStatus.Experimental
interface WebViewDragPayloadAdapter<T : Any> { ... }
```

Integrate it with asset-backed Swing panels:

```kotlin
data class WebViewPanelOptions(
  ...,
  val dragAndDrop: WebViewDragAndDropOptions = WebViewDragAndDropDisabled,
)

class WebViewPanel {
  val dragAndDrop: WebViewDragAndDropController?
}
```

When `dragAndDrop` is enabled, `createWebViewPanel` must add `dragAndDrop = true` to `WebViewEngineRequirements`, create a controller owned by the same panel/WebView lifecycle, and close the controller before or together with `WebView.close()`. When disabled, `WebViewPanel.dragAndDrop` is `null`; do not create a hidden no-op controller.

Extend provider selection capabilities:

```kotlin
data class WebViewEngineRequirements(
  val assetServing: Boolean = false,
  val messagePassing: Boolean = false,
  val swingEmbedding: Boolean = false,
  val interactiveInput: Boolean = false,
  val dragAndDrop: Boolean = false,
)

data class WebViewEngineCapabilities(
  val assetServing: Boolean,
  val messagePassing: Boolean,
  val swingEmbedding: Boolean,
  val interactiveInput: Boolean,
  val dragAndDrop: Boolean,
)
```

Initial capability values after smoke verification:

| Provider id | `dragAndDrop` | Notes |
| --- | --- | --- |
| `SYSTEM_MACOS` | `true` | Use normal `WKWebView`/AppKit DnD first. |
| `SYSTEM_WINDOWS` | `true` | Use normal WebView2 HWND controller DnD first. |
| `SYSTEM_LINUX` | `false` | Interactive input is still incomplete. |
| `JCEF` | `false` | Out of scope for v1. JBCEF owns the rendering mode. |

Forced provider selection must fail with diagnostics like `missing dragAndDrop` when a caller requests DnD on an unsupported provider.

## Payload Model

The controller owns an in-memory token registry scoped to one WebView panel. Tokens are opaque, unguessable ids with bounded lifetime. They are valid only inside the current IDE process and only while the source drag is active or until the controller expires them.

Each active drag can expose several representations:

- IDE-local typed payload: a JVM-local `DataFlavor` carrying `WebViewDragPayload<*>` or a small internal token object.
- Browser-readable custom MIME: `application/x-intellij-webview-dnd+json` with a compact envelope containing protocol version, token, payload type id, allowed actions, and optional metadata.
- Browser-readable URI token fallback: `text/uri-list` with a private `ide-webview-dnd:<token>` URI so browser engines that drop custom MIME still preserve a resolvable token.
- Human-readable fallback: `text/plain` from `presentableText` so external apps and non-opt-in Swing targets receive useful text instead of an implementation token.

The token registry is the source of truth for IDE-local typed objects and wrapped Swing `Transferable` values. Browser-readable formats are handles back to that registry, not serialized rich IDE data. This avoids leaking object structure into the page and avoids pretending that external applications can consume IDE-local values.

`WebViewDragPayloadAdapter` is the future extension point for existing Swing DnD entities. An adapter can recognize selected `DataFlavor`s or typed payload types and convert them into the WebView payload envelope or back into a Swing `Transferable`. The core controller should not hardcode editor, project view, tool window, or PSI-specific flavors.

## Web to Swing Flow

JavaScript gets a helper under the existing bridge:

```javascript
window.__WVI__.dnd.startDrag(dataTransfer, {
  type: "demo.task",
  token: "...",
  text: "WEB-123: Fix drag support",
  action: "move"
});
```

Required behavior:

- `startDrag` must synchronously populate the supplied `DataTransfer`; it cannot wait for an async Kotlin round trip after the native drag has started.
- The helper writes the custom MIME envelope, `text/uri-list` token fallback, `text/plain`, and `effectAllowed`.
- The helper sends a best-effort notification through `window.__WVI__.notification(...).send(...)` so Kotlin can register or refresh the token before an opt-in Swing target resolves it.
- If the native target is an opt-in Swing component, it asks the panel's `WebViewDragAndDropController` to resolve the token into a typed payload or adapted `Transferable`.
- If the native target is a normal Swing component, it sees the standard flavors only and should not depend on WebView-specific token resolution.

The first demo scenario should be: drag a task card from WebView into an opt-in Swing list and assert that Swing receives the typed task payload, not just `text/plain`.

## Swing to Web Flow

Swing sources opt in by wrapping their existing `Transferable` through the controller before starting the drag:

```kotlin
val transferable = panel.dragAndDrop?.createTransferable(payload) ?: fallbackTransferable
```

or, for existing Swing objects:

```kotlin
val transferable = panel.dragAndDrop?.wrapTransferable(existingTransferable) ?: existingTransferable
```

Required behavior:

- The controller registers the original typed payload or existing `Transferable` in the token registry.
- The wrapper preserves the original flavors and adds WebView DnD flavors in front of or alongside them without removing standard Swing behavior.
- On DOM `dragover`/`drop`, a JS helper reads the custom MIME or `text/uri-list` token from `DataTransfer` and sends a `drop` notification through the message bus.
- Kotlin resolves the token and invokes the WebView DnD drop handler registered by the owning panel/client.
- If the token cannot be resolved because it expired or came from another process, the drop handler receives a structured unsupported/expired result and may fall back to `text/plain` if it registered such behavior.

The first demo scenario should be: drag a Swing list item into a WebView board column and update the board through the typed payload path.

## Backend Notes

macOS should first rely on normal `WKWebView` and AppKit pasteboard drag support. The current native host attaches the `WKWebView` as a native subview; no native DnD hook is needed unless smoke tests show that required token formats are stripped between WebKit and Swing.

Windows should first rely on normal WebView2 DnD for the current HWND-backed controller. Do not move to the WebView2 composition controller for v1. Composition hosting has different input forwarding requirements and is unrelated to the first token protocol.

Linux remains unsupported for this capability until the system backend supports interactive input and asset serving well enough to run a full DnD smoke test.

JCEF remains unsupported in this plan. The API shape should not block a later JCEF implementation, but no CEF-specific drag source or OSR hook should be introduced for v1.

## Test Plan

Documentation-only changes require no build or test run.

For implementation, add focused unit coverage for:

- token creation, lookup, expiry, and cleanup on controller close;
- `Transferable` flavor ordering and preservation of wrapped Swing flavors;
- custom MIME and `text/uri-list` envelope parsing;
- typed payload decoding and unsupported/expired token results;
- adapter selection for existing `DataFlavor` values without hardcoding product-specific flavors.

Add demo/smoke coverage under `WebViewDemoPanel`:

- WebView card to opt-in Swing list carries a typed task payload;
- Swing list item to WebView column carries a typed payload;
- non-opt-in target receives readable `text/plain`;
- forced JCEF/Linux provider selection with DnD requirement fails with `missing dragAndDrop` diagnostics.

Common verification commands after code changes:

```bash
./tests.cmd --module intellij.platform.ui.webview.tests --test 'com.intellij.ui.webview.*'
./tests.cmd --module intellij.platform.ui.webview.tests --test 'com.intellij.ui.webview.MacWebViewSmokeTest'
./tests.cmd --module intellij.platform.ui.webview.tests --test 'com.intellij.ui.webview.WindowsWebViewSmokeTest'
```

Run platform-specific smoke tests only where the matching native runtime is available.

## References

- [WebView JSON-RPC Design](../architecture/WebView-JsonRpc-Design.md) - current Kotlin message bus and JSON-RPC contract.
- [Runtime Architecture](../architecture/WebView-Runtime-Architecture.md) - current runtime, provider, capability, and asset panel model.
- [HTML drag and drop](https://html.spec.whatwg.org/multipage/dnd.html) - browser `DataTransfer` behavior.
- [WebView2 features and APIs](https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/overview-features-apis) - WebView2 DnD support overview.
- [AppKit NSDraggingDestination](https://developer.apple.com/documentation/appkit/nsdraggingdestination) - native macOS drag destination model if backend hooks become necessary.
- [WebKitGTK WebView](https://webkitgtk.org/reference/webkit2gtk/2.42.4/class.WebView.html) - Linux backend reference for later work.
