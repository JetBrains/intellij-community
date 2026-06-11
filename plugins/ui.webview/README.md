# intellij.platform.ui.webview

Experimental engine-neutral WebView runtime for local web UI embedded into IntelliJ Swing UI. It lets platform and plugin code host static TypeScript/HTML/CSS views in Swing, serve bundled assets through WebView asset handlers, and communicate with the page through typed JSON-RPC APIs.

Start with [WebView UI Authoring Guide](docs/guides/WebView-UI-Authoring-Guide.md) when adding a new UI. The guide covers the normal path: frontend sources under `webview-src/views/<view-id>`, built assets under `resources/webview/views/<view-id>`, `createWebViewPanel(...)` on the Kotlin side, and typed `WebViewApi` contracts across the bridge.

Use the module through the current API surface:

- `WebViewRuntime.createWebView(...)` creates one engine-neutral `WebView` with a lifecycle-owned `interop` facade.
- `createWebViewPanel(...)` is the preferred Swing path for bundled web UI. It creates the `WebView`, host component, message bus, and initial asset load.
- `WebViewAssetRoot.fromClasspath(...)`, `WebViewAssetRoot.fromDirectory(...)`, and `WebViewAssetPath` describe local assets served through WebView asset handlers, not through a local HTTP server.
- `WebViewEngineFactory` remains a lower-level engine-only factory for direct engine consumers and tests.

Current engines:

- macOS system WebView: `WKWebView` with asset serving, message passing, Swing embedding, and interactive input.
- Windows system WebView: WebView2 with asset serving, message passing, Swing embedding, and interactive input.
- Linux system WebView: experimental WebKitGTK Wayland snapshot backend; display, JavaScript evaluation, and message passing work, but interactive input and asset-handler serving are still pending.
- JCEF: windowed/in-process and OSR modes with asset serving, message passing, Swing embedding, and interactive input.

The browser bridge is JSON-RPC based. Application code should use typed protocol APIs: Kotlin uses `WebView.interop` with `WebViewApi` and `WebViewApiId`; TypeScript uses `@jetbrains/intellij-webview` wrappers such as `webView.callable(...)` and `webView.implement(...)`. The raw `window.__WVI__` bridge is a low-level runtime detail loaded from `/__webview/wvi-bridge.js`.

Useful local entry points:

- `demo/` - internal WebView demo module loaded by `.idea/runConfigurations/IDEA__WebView_Demo_.xml`.
- `tests/testSrc/com/intellij/ui/webview/LightweightStandaloneSampleApp.kt` - standalone JFrame smoke app.
- `tests/testSrc/com/intellij/ui/webview/*WebViewSmokeTest.kt` and `WebViewRuntimeTest.kt` - runtime, backend, and smoke coverage.

Design docs start at `docs/directory.md`. They explain the runtime and frontend design in depth, but they are not required for ordinary framework usage. For practical UI authoring, use `docs/guides/WebView-UI-Authoring-Guide.md` first.
