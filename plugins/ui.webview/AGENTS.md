## WebView UI Authoring

- For WebView UI work, start with [WebView UI Authoring Guide](docs/guides/WebView-UI-Authoring-Guide.md).
- New UI code should load bundled assets through `createWebViewPanel(...)` and `WebViewAssetRoot`, not a local HTTP server.
- New bridge contracts should use typed `WebViewApi`, `WebViewApiId`, and `WebViewInterop` on the Kotlin side and `@jetbrains/intellij-webview` (`apiId`, `webView.callable`, `webView.implement`) on the TypeScript side.
- Do not copy raw `WebViewMessageBus`, raw method string, or direct `window.__WVI__` usage into new feature code unless the task is explicitly about the low-level runtime.

## WebView Local Code Style

- In WebView Kotlin, Java, and native bridge code, mark string literals that embed HTML or JavaScript code.
- Apply the same rule to related WebView native bridge files under `community/plugins/ui.webview/native/LinuxWebKitGtkBridge` and `community/plugins/ui.webview/native/WinWebView2Bridge`.
- Prefer `@Language("HTML")` or `@Language("JavaScript")` on a parameter, property, local variable, or helper function when the language applies to the whole value.
- If an annotation cannot be attached cleanly, put an IntelliLang marker immediately before the literal: `/*language=HTML*/` or `/*language=JavaScript*/`.
