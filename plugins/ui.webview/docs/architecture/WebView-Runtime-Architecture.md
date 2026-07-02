# WebView Runtime Architecture

Status: current architecture snapshot after the May 2026 runtime refactoring. The old staged migration plan was removed from this file because its intermediate adapter steps no longer describe the tree.

## Current Model

`community/plugins/ui.webview` exposes an engine-neutral runtime around four public concepts:

- `WebViewRuntime` is the application service that selects an engine provider and creates a `WebView`.
- `WebView` is one browser/page instance. It owns `messageBus`, `runtimeInfo`, load/eval operations, and `close()`.
- `createWebViewPanel` is the high-level Swing entry point for bundled web UI. It creates the `WebView`, asks the provider for the host component, creates the panel, and loads the initial asset.
- `WebViewEngineFactory` is the lower-level direct engine factory for direct engine consumers and tests.

Removed compatibility APIs:

- `WebViewFacade`
- `WebViewFacadeFactory`
- callback-based factory creation through `onMessage`
- deprecated `WebViewEngine.loadUrl`
- file-extracting `WebUiAssetLoader` helpers

## Public Creation Paths

Asset-backed Swing clients should use `createWebViewPanel`:

```kotlin
val assetRoot = WebViewAssetRoot.fromClasspath(
  owner = MyPanel::class.java,
  root = WebViewAssetPath.of("webview/views/my-panel"),
)

val panel = createWebViewPanel(
  scope = scope,
  options = WebViewPanelOptions(
    assetRoot = assetRoot,
    debugName = "my-panel",
  ),
)

panel.interop.messageBus.registerNotificationHandler(MyNotifications.Ready) { params, context ->
  // update host state or send follow-up notifications
}
```

Lower-level clients that do not need the bundled-asset Swing panel can use `WebViewRuntime` directly:

```kotlin
val webView = WebViewRuntime.getInstance().createWebView(
  scope = scope,
  options = WebViewCreationOptions(
    enginePreference = WebViewEnginePreference.Auto,
    requirements = WebViewEngineRequirements(messagePassing = true),
    debugName = "my-webview",
  ),
)

webView.loadHtml("<html><body>Hello</body></html>")
```

## Engine Selection

`WebViewRuntime` selects an internal `WebViewEngineProvider` by matching `WebViewEngineKind` and `WebViewEngineRequirements` against provider capabilities and availability diagnostics.

Default providers:

| Provider id | Backend | Notes |
| --- | --- | --- |
| `SYSTEM_MACOS` | WKWebView | Asset serving, message passing, Swing embedding, and interactive input are supported. |
| `SYSTEM_WINDOWS` | WebView2 | Asset serving, message passing, Swing embedding, and interactive input are supported. |
| `SYSTEM_LINUX` | WebKitGTK | Experimental Wayland snapshot backend. Message passing and Swing embedding are supported; asset serving and interactive input are pending. |
| `JCEF` | JBCEF browser | Asset serving, message passing, Swing embedding, and interactive input are supported when JBCEF is available. JBCEF owns windowed/off-screen/remote rendering selection. |

`WebViewPanelOptions` always adds these requirements before selection:

- `assetServing = true`
- `messagePassing = true`
- `swingEmbedding = true`

This currently means asset-backed panels can select macOS system WebView, Windows system WebView, or JCEF providers, but not the Linux system provider until its asset handler is implemented.

The `ide.webview.engine` registry key can force `SYSTEM` or `JCEF` for diagnostics. Forced values are intentionally strict; unsupported engines should fail with diagnostics instead of silently changing behavior.

## Asset Loading

Assets are represented by `WebViewAssetRoot` and `WebViewAssetPath`:

- `WebViewAssetRoot.fromClasspath(owner, root, devSourceRoot)` serves packaged resources from the owner classloader and can use an explicit dev source root.
- `WebViewAssetRoot.fromDirectory(root)` serves a local directory through the same handler-backed path.
- `WebViewAssetRootFactory.fromResourceDirectory(...)` is a service wrapper around the classpath form.

The asset request path is normalized and cannot escape the root. Common runtime assets under `/__webview/...` are served by `WebViewAssetResolver` before client assets. The current shared runtime asset is:

```html
<script src="/__webview/wvi-bridge.js"></script>
```

Backend URL shapes are internal details:

- WKWebView uses the custom `ij-webview-asset:/...` scheme.
- JCEF uses `https://ij-webview-assets.local/...` and a CEF resource handler.
- Windows WebView2 uses `https://ij-webview-assets.local/...` and handles requests through `WebResourceRequested`.
- Linux WebKitGTK currently rejects `loadAsset`; its provider reports `assetServing = false`.

## Message Bus

`WebViewMessageBus` is an asynchronous JSON-RPC 2.0 runtime scoped to one `WebView`. The provider creates the engine, creates the bus, calls `engine.connectMessageBus(bus)`, and returns the created `WebView` that owns both.

Client rules:

- use `webView.interop.messageBus` or `panel.interop.messageBus` when low-level bus access is needed;
- do not construct `WebViewMessageBus` in client code;
- do not connect transports manually;
- use `bindApi<T>(implementation, namespace)` for JS -> Kotlin async calls backed by suspend-only Kotlin interfaces with serializable DTOs;
- use typed `WebViewNotification` descriptors with `notify` and `registerNotificationHandler` for resultless bidirectional notifications.

JavaScript uses `window.__WVI__` from `wvi-bridge.js`:

```typescript
import { apiId, webView, type WebViewCallable } from "@jetbrains/intellij-webview"

interface HostApi extends WebViewCallable {
  openFile(params: { path: string }): Promise<void>
}

const hostApiId = apiId<HostApi>()("host")
const notifications = window.__WVI__.notifications({
  themeChanged: { method: "host.themeChanged" },
  ready: { method: "ui.ready" },
})

notifications.themeChanged.on(params => {
  applyTheme(params.themeId)
})

const host = webView.callable(hostApiId)
await host.openFile({ path: "/tmp/a.txt" })

await notifications.ready.send({ timestamp: Date.now() })
```

The wire format is JSON-RPC 2.0. Old notification-only `{"method":"...","params":...}` frames are invalid protocol and are not dispatched to handlers. Kotlin -> JS uses `WebViewEngine.transferToJs(rawJson)`; JS -> Kotlin callbacks enter the bus through `transferFromJs(rawJson)`.

## Browser Console Logging

`WebViewEngineProvider.createWebView(...)` installs console capture for every runtime-created WebView. The provider creates `WebViewConsoleCapture`, registers its internal notification handler on the same `WebViewMessageBusImpl`, and passes `WebViewConsoleCapture.DOCUMENT_START_SCRIPT` to the selected engine through `WebViewEngineCreationOptions.documentStartScripts`.

The page-side shim wraps supported `console.*` methods, calls the original browser method first, and then sends a JSON-RPC 2.0 notification with method `$/webview/console`. This is an internal runtime notification, not an application protocol surface. Feature code should not register handlers for it or send it manually.

The payload contains the console method, a bounded string preview of arguments, and `jsTimeEpochMs` captured immediately around the JavaScript `console.*` call. Kotlin formats that epoch as a readable instant in the real log message prefix:

```text
[js=2026-07-02T18:30:15.123Z] message text
```

The default logger category is `#com.intellij.ui.webview.console`. `WebViewPanelOptions.consoleLogCategory` and `WebViewCreationOptions.consoleLogCategory` can override the base category. When a loaded `WebViewAssetRoot` carries a view id, the runtime appends the sanitized id to the base category; otherwise it logs to the base category only. Logger instances are obtained by category when each event is written.

Backend injection guarantees:

- Windows WebView2 installs document-start scripts through the native WebView2 document-created hook before page scripts run.
- macOS WKWebView installs them as `WKUserScript`s at document start.
- JCEF injects them from the load-start handler with `executeJavaScript`; this is the earliest common JCEF path currently wired through the runtime, but it is not the same native document-start guarantee as WebView2 or WKWebView.
- The Linux WebKitGTK backend is not part of the supported console-capture path.

## Backend Boundaries

Common runtime code should know only providers, capabilities, and engine-neutral interfaces. Backend-specific behavior belongs in these internal areas:

- macOS: `internal/mac/*`, `WKWebViewBridge`, `MacNativeWebViewHostPeer`
- Windows: `internal/windows/*`, `WinWebView2Bridge`, `WinNativeWebViewHostPeer`
- Linux: `internal/linux/*`, `LinuxWebKitGtkBridge`, Linux host peers
- JCEF: `internal/jcef/*`, `JcefWebViewEngine`, resource handlers, OSR host
- provider selection and host creation: `internal/engine/*`

Do not add new client-side OS checks, concrete engine casts, or backend imports to feature code.

## Remaining Work

Short-term: none.

Ongoing guardrail:

- keep Markdown, demo, ACP, and tests on `WebViewEngine`, `WebViewRuntime`, or `createWebViewPanel` APIs when adding new usage.

Deferred:

- Linux WebKitGTK asset serving and interactive input; the current snapshot backend remains display-only and is not a near-term priority;
- opt-in WebView-to-Swing drag-and-drop interop; see [WebView Drag and Drop Interop Plan](../interop/WebView-Drag-And-Drop-Interop-Plan.md);
- internal Remote Dev engine proxy that forwards `LoadAsset`, `LoadHtml`, `EvaluateJavaScript`, `TransferToJs`, and `Close` to a real engine while feeding inbound JS frames back to the backend bus;
- product/plugin extraction and stable distribution story;
- formal performance gates and UX acceptance criteria beyond current smoke coverage.

## Verification

For documentation-only changes, no build or test run is required.

For runtime, asset, host, or bridge code changes, run affected tests with fully-qualified test names. Common commands:

```bash
./tests.cmd --module intellij.platform.ui.webview.tests --test 'com.intellij.ui.webview.WebViewRuntimeTest'
./tests.cmd --module intellij.platform.ui.webview.tests --test 'com.intellij.ui.webview.impl.rpc.WebViewMessageBusTest'
./tests.cmd --module intellij.platform.ui.webview.tests --test 'com.intellij.ui.webview.WebViewInIdeSmokeTest'
```

Backend changes should also run the matching smoke test where the host platform can execute it:

```bash
./tests.cmd --module intellij.platform.ui.webview.tests --test 'com.intellij.ui.webview.MacWebViewSmokeTest'
./tests.cmd --module intellij.platform.ui.webview.tests --test 'com.intellij.ui.webview.WindowsWebViewSmokeTest'
./tests.cmd --module intellij.platform.ui.webview.tests --test 'com.intellij.ui.webview.LinuxWebViewSmokeTest'
./tests.cmd --module intellij.platform.ui.webview.tests --test 'com.intellij.ui.webview.JcefWebViewSmokeTest'
```
