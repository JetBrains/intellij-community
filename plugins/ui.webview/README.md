# intellij.platform.ui.webview

Experimental engine-neutral WebView runtime for local web UI embedded into IntelliJ Swing UI. It lets platform and plugin code host static TypeScript/HTML/CSS views in Swing, serve bundled assets through WebView asset handlers, and communicate with the page through typed JSON-RPC APIs.

Start with [WebView UI Authoring Guide](docs/guides/WebView-UI-Authoring-Guide.md) when adding a new UI. The guide covers the normal path: frontend sources under `webview-src/views/<view-id>`, built assets under `resources/webview/views/<view-id>`, `createWebViewPanel(...)` on the Kotlin side, typed `WebViewApi` contracts across the bridge, browser mock previews, and Playwright smoke tests.

Plugin-facing main API (all `@ApiStatus.Experimental`):

- `createWebViewPanel(...)` is the main entry point for bundled web UI. It creates the host component, the typed `interop` facade, the message bus, and the initial asset load. Configure it with `WebViewPanelOptions(assetRoot, debugName, ...)`.
- `WebViewAssetRoot.forView(viewId)` describes assets bundled under `webview/views/<viewId>`, served through WebView asset handlers (not a local HTTP server); it captures the calling class automatically (use `forView(owner, viewId)` to anchor a different module or from Java). `WebViewAssetPath` addresses entries within a root, and `WebViewAssetProvider` serves dynamic entries. `WebViewAssetRoot.fromClasspath(...)`/`fromDirectory(...)` are low-level escape hatches for non-standard layouts.
- Talk to the page through `WebViewPanel.interop` with typed `WebViewApi` / `WebViewApiId` contracts (`implement(...)` / `callable(...)`).

Lower-level/internal surfaces are marked `@ApiStatus.Internal` and may change without notice — not part of the plugin-facing API: `WebViewRuntime.createWebView(...)` and the engine-neutral `WebView`, the raw `WebViewMessageBus`, `WebViewEngineFactory`, and the engine `WebViewEngine`.

Current engines:

- macOS system WebView: `WKWebView` with asset serving, message passing, Swing embedding, and interactive input.
- Windows system WebView: WebView2 with asset serving, message passing, Swing embedding, and interactive input.
- Linux system WebView: experimental WebKitGTK Wayland snapshot backend; display, JavaScript evaluation, and message passing work, but interactive input and asset-handler serving are still pending.
- JCEF: windowed/in-process and OSR modes with asset serving, message passing, Swing embedding, and interactive input.

The browser bridge is JSON-RPC based. Application code should use typed protocol APIs: Kotlin uses `WebViewPanel.interop` with `WebViewApi` and `WebViewApiId`; TypeScript uses `@jetbrains/intellij-webview` wrappers such as `webView.callable(...)` and `webView.implement(...)`. The raw `window.__WVI__` bridge is a low-level runtime detail loaded from `/__webview/wvi-bridge.js`.

Useful local entry points:

- `demo/` - internal WebView demo module loaded by `.idea/runConfigurations/IDEA__WebView_Demo_.xml`.
- `demo/webview-src/test/acp-chat/preview.ts` and `bun run preview:acp-chat` - browser mock preview of the ACP chat view without Kotlin/IDE.
- `demo/webview-src/test/acp-chat/acp-chat.browser.test.ts` - Playwright smoke reference for a WebView running through the mock bridge.
- `tests/testSrc/com/intellij/ui/webview/LightweightStandaloneSampleApp.kt` - standalone JFrame smoke app.
- `tests/testSrc/com/intellij/ui/webview/*WebViewSmokeTest.kt` and `WebViewRuntimeTest.kt` - runtime, backend, and smoke coverage.

Design docs start at `docs/directory.md`. They explain the runtime and frontend design in depth, but they are not required for ordinary framework usage. For practical UI authoring, use `docs/guides/WebView-UI-Authoring-Guide.md` first.
